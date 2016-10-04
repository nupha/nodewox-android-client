package org.nodewox.client;

import android.app.AlarmManager;
import android.app.Application;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

public abstract class NodewoxApplication extends Application {

    // mqtt event processing handler
    public final Handler EventHandler = new MqttEventHandler(this);

    protected Thing thing = null;

    private boolean mIsConnected = false;
    private PendingIntent mConnCheckIntent = null;

    // get binding mqttservice
    protected abstract NodewoxService getService();

    protected abstract void mqttHandleUserEvent(int event, Bundle data);

    protected abstract int mqttDispatchMessage(String topic, byte[] payload, int qos, boolean duplicate, boolean retained);

    // when mqtt client is about to connect
    protected abstract void onMqttConnecting(Serializable ctx);

    // when mqtt client connect successfully or failed
    protected abstract void onMqttConnected(Serializable ctx, String error);

    // when mqtt client disconnected
    protected abstract void onMqttDisconnected(Serializable ctx);

    protected abstract void onMqttConnectionLost(Serializable ctx, String error);

    // when mqtt subscribe success or fail
    protected abstract void onMqttSub(Serializable ctx, String[] topics, String error);

    // when mqtt unsubscribe success or fail
    protected abstract void onMqttUnsub(Serializable ctx, String[] topics, String error);

    // when mqtt publish success or fail
    protected abstract void onMqttPub(Serializable ctx, String error);

    public Thing getThing() {
        return thing;
    }

    // get REST service
    public NodewoxREST getRest() {
        return new NodewoxREST();
    }

    public boolean mqttIsConnected() {
        return mIsConnected;
    }

    public synchronized void mqttConnect() {
        Log.v("nodewox", "call mqttConnect()");
        if (!mIsConnected) {
            checkConnection(false);  // stop check connection
            getService().connect();
        }
    }

    public synchronized void mqttDisconnect() {
        checkConnection(false);
        getService().disconnect();
    }

    public synchronized void mqttSub(String[] topics, Serializable ctx) {
        if (mqttIsConnected()) {
            getService().subscribe(topics, ctx);
        }
    }

    public synchronized void mqttSub(String[] topics) {
        mqttSub(topics, null);
    }

    public synchronized void mqttUnsub(final String[] topics, Serializable ctx) {
        if (mIsConnected) {
            getService().subscribe(topics, ctx);
        }
    }

    public synchronized void mqttUnsub(final String[] topics) {
        mqttUnsub(topics, null);
    }

    public synchronized void mqttPub(String topic, byte[] payload, int qos, boolean retained, Serializable ctx) {
        if (mIsConnected) {
            getService().publish(topic, payload, qos, retained, ctx);
        }
    }

    public synchronized void mqttPub(String topic, byte[] payload, int qos, boolean retained) {
        mqttPub(topic, payload, qos, retained, null);
    }

    public int getConnectionCheckInterval() {
        return 5000;  // in sec
    }

    private void checkConnection(boolean on) {
        if (on && mConnCheckIntent == null) {
            int timeout = getConnectionCheckInterval();
            if (timeout > 0) {
                Log.v("nodewox/service", "schedule to connect after " + timeout + "ms");
                Intent it = new Intent(this, getService().getClass());
                it.setAction(NodewoxService.ACTION_CONNECT);
                mConnCheckIntent = PendingIntent.getService(this, 0, it, 0);
                long now = System.currentTimeMillis();
                ((AlarmManager) getSystemService(ALARM_SERVICE)).setRepeating(
                        AlarmManager.RTC_WAKEUP, now + 1000, timeout, mConnCheckIntent);
            }
        } else if (!on && mConnCheckIntent != null) {
            ((AlarmManager) getSystemService(ALARM_SERVICE)).cancel(mConnCheckIntent);
            mConnCheckIntent = null;
        }
    }

    // mqtt event handler class
    private static class MqttEventHandler extends Handler {
        private final NodewoxApplication mApp;
        private final NodewoxService.EventType[] etypes = NodewoxService.EventType.values();

        public MqttEventHandler(NodewoxApplication app) {
            mApp = app;
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
                    mApp.onMqttConnecting(ctx);
                    break;

                case CONNECT_SUCCESS:
                    mApp.mIsConnected = true;
                    mApp.checkConnection(false);
                    mApp.onMqttConnected(ctx, null);
                    break;

                case CONNECT_FAIL:
                    mApp.mIsConnected = false;
                    mApp.checkConnection(true);
                    mApp.onMqttConnected(ctx, error);
                    break;

                case CONNECT_CLOSE:
                    mApp.mIsConnected = false;
                    mApp.checkConnection(false);
                    mApp.onMqttDisconnected(ctx);
                    break;

                case CONNECT_LOST:
                    mApp.mIsConnected = false;
                    mApp.checkConnection(true);
                    mApp.onMqttConnectionLost(ctx, error);
                    break;

                case SUB_SUCCESS:
                case SUB_FAIL:
                    String[] topics = null;
                    if (bundle != null && bundle.containsKey("topics"))
                        topics = bundle.getStringArray("topics");
                    mApp.onMqttSub(ctx, topics, error);
                    break;

                case UNSUB_SUCCESS:
                case UNSUB_FAIL:
                    String[] t2 = null;
                    if (bundle != null && bundle.containsKey("topics"))
                        t2 = bundle.getStringArray("topics");
                    mApp.onMqttUnsub(ctx, t2, error);
                    break;

                case PUB_COMPLETE:
                case PUB_FAIL:
                    mApp.onMqttPub(ctx, error);
                    break;

                case MESSAGE:
                    String topic = bundle.getString("topic");
                    mApp.mqttDispatchMessage(
                            topic,
                            bundle.getByteArray("payload"),
                            bundle.getInt("qos"),
                            bundle.getBoolean("duplicate"),
                            bundle.getBoolean("retained"));
                    break;

                default:
                    mApp.mqttHandleUserEvent(msg.what, bundle);
            }
        }
    }

}
