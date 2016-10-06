package org.nodewox.client;

import android.app.AlarmManager;
import android.app.Application;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Base64;
import android.util.Log;

import org.json.JSONObject;

import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;

import static android.content.Context.ALARM_SERVICE;

public abstract class Node {

    protected final NodewoxApplication mApp;
    private final Handler mEventHandler = new MessagingEventHandler(this);

    protected String mKey = null;
    protected int mID = 0;
    protected String mSecret = null;
    protected byte[] mCertP12 = null;

    protected String mMqttURI = "";
    protected String mMqttUsername = "";
    protected String mMqttPassword = "";
    protected int mMqttKeepAlive = 60;

    // trust ca keystore (BKS format)
    protected byte[] mMqttCa = null;
    protected String mMqttCaPass = "";

    protected RestRequest mRest = null;

    private boolean mIsConnected = false;
    private PendingIntent mConnCheckIntent = null;

    public Node(NodewoxApplication app) {
        mApp = app;
    }

    protected abstract boolean loadConfig();

    protected abstract void saveConfig(JSONObject data);

    protected abstract void handleUserEvent(int event, Bundle data);

    protected abstract int dispatchMessage(
            final String topic,
            final byte[] payload,
            final int qos,
            final boolean duplicate,
            final boolean retained);

    // messaging handlers
    protected abstract void onConnecting(Serializable ctx);

    protected abstract void onConnected(Serializable ctx);

    protected abstract void onConnectFail(Serializable ctx, String error);

    protected abstract void onDisconnected(Serializable ctx);

    protected abstract void onConnectionLost(Serializable ctx, String error);

    protected abstract void onSubscribe(Serializable ctx, String[] topics, String error);

    protected abstract void onUnsubscribe(Serializable ctx, String[] topics, String error);

    protected abstract void onPublish(Serializable ctx, String error);

    public boolean isThing() {
        return false;
    }

    public boolean isRegistered() {
        return false;
    }

    public String getKey() {
        return mKey;
    }

    public int getID() {
        return mID;
    }

    public void setUsernamePassword(String u, String p) {
        mMqttUsername = u;
        mMqttPassword = p;
    }

    public String getMqttUsername() {
        return mMqttUsername;
    }

    public String getMqttPassword() {
        return mMqttPassword;
    }

    public void setMqttCA(byte[] bks, String keypass) {
        if (bks != null && bks.length > 0) {
            mMqttCa = bks;
            mMqttCaPass = keypass;
        } else {
            mMqttCa = null;
            mMqttCaPass = "";
        }
    }

    public byte[] getMqttCA() {
        return mMqttCa;
    }

    public String getMqttCAPass() {
        return mMqttCaPass;
    }

    public void setClientCert(byte[] cert, String certkey) {
        if (cert != null && cert.length > 0) {
            mCertP12 = cert;
            mSecret = certkey;
        } else {
            mCertP12 = null;
            mSecret = "";
        }
    }

    public byte[] getCert() {
        return mCertP12;
    }

    public String getSecret() {
        return mSecret;
    }

    public String getMqttURI() {
        return mMqttURI;
    }

    public boolean setMqttURI(String auri) {
        URI uri;
        try {
            uri = new URI(auri);
            mMqttURI = uri.getScheme();
            if (mMqttURI.equals("mqtts"))
                mMqttURI = "ssl";
            mMqttURI += "://" + uri.getHost();
            mMqttURI += ":" + uri.getPort();
            return true;
        } catch (URISyntaxException e) {
            Log.v("nodewox", "invalid mqtt uri " + auri);
            return false;
        }
    }

    public int getMqttKeepAlive() {
        return mMqttKeepAlive;
    }

    public void setMqttKeepAlive(int v) {
        if (v > 0)
            mMqttKeepAlive = v;
    }

    protected void reset() {
        mID = 0;
        mKey = null;
        mSecret = null;
        mCertP12 = null;
    }

    public RestRequest getRestRequest() {
        return mRest;
    }

    public void setRestRequest(RestRequest rest) {
        mRest = rest;
    }

    public boolean isConnected() {
        return mIsConnected;
    }

    public Handler getEventHandler() {
        return mEventHandler;
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
                it.setAction(NodewoxService.ACTION_CONNECT);
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

    public synchronized void unsubscribe(final String[] topics, Serializable ctx) {
        if (isConnected()) {
            mApp.getService().subscribe(topics, ctx);
        }
    }

    public synchronized void unsubscribe(final String[] topics) {
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

    // messaging event handler class
    private static class MessagingEventHandler extends Handler {
        private final Node mMsgr;
        private final NodewoxService.EventType[] etypes = NodewoxService.EventType.values();

        public MessagingEventHandler(Node msgr) {
            mMsgr = msgr;
        }

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
            NodewoxService.EventType etype;
            if (msg.what >= 0 && msg.what < etypes.length)
                etype = etypes[msg.what];
            else
                etype = NodewoxService.EventType.UNKOWN;

            switch (etype) {
                case CONNECTING:
                    mMsgr.onConnecting(ctx);
                    break;

                case CONNECT_SUCCESS:
                    mMsgr.mIsConnected = true;
                    mMsgr.checkConnection(false);
                    mMsgr.onConnected(ctx);
                    break;

                case CONNECT_FAIL:
                    mMsgr.mIsConnected = false;
                    mMsgr.checkConnection(true);
                    mMsgr.onConnectFail(ctx, error);
                    break;

                case CONNECT_CLOSE:
                    mMsgr.mIsConnected = false;
                    mMsgr.checkConnection(false);
                    mMsgr.onDisconnected(ctx);
                    break;

                case CONNECT_LOST:
                    mMsgr.mIsConnected = false;
                    mMsgr.checkConnection(true);
                    mMsgr.onConnectionLost(ctx, error);
                    break;

                case SUB_SUCCESS:
                case SUB_FAIL:
                    String[] topics = null;
                    if (bundle != null && bundle.containsKey("topics"))
                        topics = bundle.getStringArray("topics");
                    mMsgr.onSubscribe(ctx, topics, error);
                    break;

                case UNSUB_SUCCESS:
                case UNSUB_FAIL:
                    String[] t2 = null;
                    if (bundle != null && bundle.containsKey("topics"))
                        t2 = bundle.getStringArray("topics");
                    mMsgr.onUnsubscribe(ctx, t2, error);
                    break;

                case PUB_COMPLETE:
                case PUB_FAIL:
                    mMsgr.onPublish(ctx, error);
                    break;

                case MESSAGE:
                    String topic = bundle.getString("topic");
                    mMsgr.dispatchMessage(
                            topic,
                            bundle.getByteArray("payload"),
                            bundle.getInt("qos"),
                            bundle.getBoolean("duplicate"),
                            bundle.getBoolean("retained"));
                    break;

                default:
                    mMsgr.handleUserEvent(msg.what, bundle);
            }
        }
    }

}
