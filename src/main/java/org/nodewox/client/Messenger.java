package org.nodewox.client;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static android.content.Context.ALARM_SERVICE;

public abstract class Messenger extends Handler {

    private final NxApplication mApp;
    private final MessageSensible theNode;
    private final NxService.EventType[] etypes = NxService.EventType.values();

    // regexp for known topic
    private final Pattern mKnownTopicPat;

    private PendingIntent mConnCheckIntent = null;
    private boolean mIsConnected = false;

    public Messenger(MessageSensible mgrNode) {
        if (BuildConfig.DEBUG && !(mgrNode instanceof Node))
            throw new AssertionError("MessageSensible object must be an Node instance");

        this.theNode = mgrNode;
        this.mApp = ((Node) mgrNode).getApp();
        mKnownTopicPat = Pattern.compile("^" + this.mApp.getTopicPrefix() + "(\\d+)(/q)?$");
    }

    public abstract String getAddr();

    public abstract boolean setAddr(String uri);

    public abstract byte[] getCA();

    public abstract String getCAPass();

    public abstract byte[] getCert();

    public abstract String getCertPass();

    public abstract String getUsername();

    public abstract String getPassword();

    public abstract String getMqttClientId();

    public abstract String getMqttWillTopic();

    public abstract byte[] getMqttWillPayload();

    public abstract int getMqttWillQos();

    public NxApplication getApp() {
        return mApp;
    }

    public MessageSensible getNode() {
        return theNode;
    }

    public void handleMessengerEvent(int event, Bundle data) {
    }

    public boolean isMqttClear() {
        return true;
    }

    public int getMqttKeepAlive() {
        return 60;
    }

    public boolean isConnected() {
        return mIsConnected;
    }

    public int getConnectionCheckInterval() {
        return 5000;  // in sec
    }

    private void checkConnection(boolean on) {
        if (on && mConnCheckIntent == null) {
            int timeout = getConnectionCheckInterval();
            if (timeout > 0) {
                Log.v("nodewox/service", "schedule to connect after " + timeout + "ms");
                Intent it = new Intent(mApp, mApp.getService().getClass());
                it.setAction(NxService.ACTION_CONNECT);
                mConnCheckIntent = PendingIntent.getService(mApp, 0, it, 0);
                long now = System.currentTimeMillis();
                ((AlarmManager) mApp.getSystemService(ALARM_SERVICE)).setRepeating(
                        AlarmManager.RTC_WAKEUP, now + 1000, timeout, mConnCheckIntent);
            }
        } else if (!on && mConnCheckIntent != null) {
            ((AlarmManager) mApp.getSystemService(ALARM_SERVICE)).cancel(mConnCheckIntent);
            mConnCheckIntent = null;
        }
    }

    public synchronized void connect() {
        if (!isConnected()) {
            checkConnection(false);  // stop check connection
            mApp.getService().connect();
        }
    }

    public synchronized void disconnect() {
        checkConnection(false);
        mApp.getService().disconnect();
    }

    public synchronized void subscribe(String[] topics, Serializable ctx) {
        if (isConnected()) {
            mApp.getService().subscribe(topics, ctx);
        }
    }

    public synchronized void subscribe(String[] topics) {
        subscribe(topics, null);
    }

    public synchronized void unsubscribe(String[] topics, Serializable ctx) {
        if (isConnected()) {
            mApp.getService().subscribe(topics, ctx);
        }
    }

    public synchronized void unsubscribe(String[] topics) {
        unsubscribe(topics, null);
    }

    public synchronized void publish(String topic, byte[] payload, int qos, boolean retained, Serializable sessid) {
        if (isConnected()) {
            mApp.getService().publish(topic, payload, qos, retained, sessid);
        }
    }

    public synchronized void publish(String topic, byte[] payload, int qos, boolean retained) {
        publish(topic, payload, qos, retained, null);
    }

    public synchronized boolean publishSync(String topic, byte[] payload, int qos, boolean retained, int timeout) {
        if (isConnected())
            return mApp.getService().publishSync(topic, payload, qos, retained, timeout);
        else
            return false;
    }

    private void processMessage(String topic, byte[] payload, int qos, boolean dup, boolean retain) {
        Matcher m = mKnownTopicPat.matcher(topic);
        if (!m.matches()) {
            theNode.onMessage(topic, payload, qos, dup, retain);
            return;
        }

        int nid = Integer.valueOf(m.group(1));
        String verb = m.group(2);
        if (verb == null) verb = "";

        Node target = null;
        if (nid == ((Node) theNode).getID()) {
            target = (Node) theNode;
        } else {
            for (Node ch : ((Node) theNode).getChildren()) {
                if (ch.getID() == nid) {
                    target = ch;
                    break;
                }
            }
        }

        if (target != null) {
            Map<String, JSONObject> res = null;

            switch (verb) {
                case "/q":
                    JSONObject msg;
                    if (payload != null && payload.length > 0) {
                        try {
                            msg = new JSONObject(new String(payload));
                        } catch (JSONException e) {
                            Log.w("nodewox/messenger", "invalid request message");
                            return;
                        }
                    } else
                        msg = new JSONObject();
                    res = target.handleRequest(msg);
                    break;

                case "":
                    if (target instanceof FemaleChannel)
                        ((FemaleChannel) target).handlePacket(payload);
                    break;
            }

            if (res != null && !res.isEmpty()) {
                for (Map.Entry<String, JSONObject> key_res : res.entrySet()) {
                    JSONObject x = key_res.getValue();
                    publish(
                            key_res.getKey(),
                            (x != null && x.keys().hasNext()) ? x.toString().getBytes() : null,
                            0,
                            false);
                }
            }
        }
    }

    @Override
    public void handleMessage(Message msg) {
        super.handleMessage(msg);

        String ctx = null;
        String error = null;  // null means no error

        Bundle bundle = msg.getData();
        if (bundle != null) {
            if (bundle.containsKey("context"))
                ctx = (String) bundle.getSerializable("context");

            if (bundle.containsKey("error"))
                error = bundle.getString("error");
        }

        // check event type
        NxService.EventType etype;
        if (msg.what >= 0 && msg.what < etypes.length)
            etype = etypes[msg.what];
        else
            etype = NxService.EventType.UNKOWN;

        switch (etype) {
            case CONNECTING:
                theNode.onConnecting(ctx);
                break;

            case CONNECT_SUCCESS:
                mIsConnected = true;
                checkConnection(false);
                theNode.onConnected(ctx);
                break;

            case CONNECT_FAIL:
                mIsConnected = false;
                checkConnection(true);
                theNode.onConnectFail(ctx, error);
                break;

            case CONNECT_CLOSE:
                mIsConnected = false;
                checkConnection(false);
                theNode.onDisconnected(ctx);
                break;

            case CONNECT_LOST:
                mIsConnected = false;
                checkConnection(true);
                theNode.onConnectionLost(ctx, error);
                break;

            case SUB_SUCCESS:
            case SUB_FAIL:
                String[] topics = null;
                if (bundle != null && bundle.containsKey("topics"))
                    topics = bundle.getStringArray("topics");
                theNode.onSubscribe(ctx, topics, error);
                break;

            case UNSUB_SUCCESS:
            case UNSUB_FAIL:
                String[] t2 = null;
                if (bundle != null && bundle.containsKey("topics"))
                    t2 = bundle.getStringArray("topics");
                theNode.onUnsubscribe(ctx, t2, error);
                break;

            case PUB_COMPLETE:
            case PUB_FAIL:
                theNode.onPublish(ctx, error);
                break;

            case MESSAGE:
                processMessage(
                        bundle.getString("topic"),
                        bundle.getByteArray("payload"),
                        bundle.getInt("qos"),
                        bundle.getBoolean("duplicate"),
                        bundle.getBoolean("retained"));
                break;

            default:
                handleMessengerEvent(msg.what, bundle);
        }
    }

}
