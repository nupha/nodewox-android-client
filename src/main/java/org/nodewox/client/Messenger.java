package org.nodewox.client;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static android.content.Context.ALARM_SERVICE;

public abstract class Messenger extends Handler {

    // regexp for known topic
    private static final Pattern PAT_KNOWN_TOPIC = Pattern.compile("^/NX/(\\d+)(/q)?$");

    private final NxApplication mApp;
    private final MessageSensible mMgrNode;
    private final NxService.EventType[] etypes = NxService.EventType.values();

    private PendingIntent mConnCheckIntent = null;
    private boolean mIsConnected = false;

    public Messenger(MessageSensible mgrNode) {
        if (BuildConfig.DEBUG && !(mgrNode instanceof Node))
            throw new AssertionError("MessageSensible object must be an Node instance");

        this.mMgrNode = mgrNode;
        this.mApp = ((Node) mgrNode).getApp();
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

    // handlers
    public abstract void onBeforeConnect();

    public abstract void onBeforeDisconnect();

    public abstract void onConnecting(Serializable ctx);

    public abstract void onConnected(Serializable ctx);

    public abstract void onConnectFail(Serializable ctx, String error);

    public abstract void onDisconnected(Serializable ctx);

    public abstract void onConnectionLost(Serializable ctx, String error);

    public abstract void onSubscribe(Serializable ctx, String[] topics, String error);

    public abstract void onUnsubscribe(Serializable ctx, String[] topics, String error);

    public abstract void onPublish(Serializable ctx, String error);

    public abstract void onMessage(String topic, byte[] payload, int qos, final boolean duplicate, boolean retained);


    public NxApplication getApp() {
        return mApp;
    }

    public Node getNode() {
        return (Node) mMgrNode;
    }

    public void handleMessengerEvent(int event, Bundle data) {
    }

    public int getMqttWillQos() {
        return 0;
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
        Matcher m = PAT_KNOWN_TOPIC.matcher(topic);
        if (!m.matches()) {
            onMessage(topic, payload, qos, dup, retain);
            return;
        }

        int nid = Integer.valueOf(m.group(1));
        String verb = m.group(2);
        if (verb == null) verb = "";
        GeneratedMessageV3 msg = null;

        try {
            switch (verb) {
                case "/q":
                    msg = NodeTalk.Request.parseFrom(payload);
                    break;
                case "":
                    msg = NodeTalk.Packet.parseFrom(payload);
                    break;
            }
        } catch (InvalidProtocolBufferException e) {
            Log.w("nodewox/messenger", "invalid message body " + e.getMessage());
            return;
        }

        Map<String, NodeTalk.Response> res = new HashMap<>();
        if (nid == ((Node) mMgrNode).getID()) {
            // for this node
            switch (verb) {
                case "/q":
                    res = ((Node) mMgrNode).handleRequest((NodeTalk.Request) msg);
                    break;
                case "":
                    if (mMgrNode instanceof Channel)
                        ((Channel) mMgrNode).handlePacket((NodeTalk.Packet) msg);
                    break;
            }
        } else {
            // match nid to children nodes
            for (Node ch : ((Node) mMgrNode).getChildren()) {
                if (ch.getID() == nid) {
                    Map<String, NodeTalk.Response> res2 = null;
                    switch (verb) {
                        case "/q":
                            res2 = ch.handleRequest((NodeTalk.Request) msg);
                            break;
                        case "":
                            if (ch instanceof Channel)
                                ((Channel) ch).handlePacket((NodeTalk.Packet) msg);
                            break;
                    }
                    if (res2 != null)
                        res.putAll(res2);
                    break;
                }
            }
        }

        if (res != null && !res.isEmpty()) {
            // send response
            for (Map.Entry<String, NodeTalk.Response> key_res : res.entrySet())
                publish(key_res.getKey(), key_res.getValue().toByteArray(), 0, false);
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
                onConnecting(ctx);
                break;

            case CONNECT_SUCCESS:
                mIsConnected = true;
                checkConnection(false);
                onConnected(ctx);
                break;

            case CONNECT_FAIL:
                mIsConnected = false;
                checkConnection(true);
                onConnectFail(ctx, error);
                break;

            case CONNECT_CLOSE:
                mIsConnected = false;
                checkConnection(false);
                onDisconnected(ctx);
                break;

            case CONNECT_LOST:
                mIsConnected = false;
                checkConnection(true);
                onConnectionLost(ctx, error);
                break;

            case SUB_SUCCESS:
            case SUB_FAIL:
                String[] topics = null;
                if (bundle != null && bundle.containsKey("topics"))
                    topics = bundle.getStringArray("topics");
                onSubscribe(ctx, topics, error);
                break;

            case UNSUB_SUCCESS:
            case UNSUB_FAIL:
                String[] t2 = null;
                if (bundle != null && bundle.containsKey("topics"))
                    t2 = bundle.getStringArray("topics");
                onUnsubscribe(ctx, t2, error);
                break;

            case PUB_COMPLETE:
            case PUB_FAIL:
                onPublish(ctx, error);
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
