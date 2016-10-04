package org.nodewox.client;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.io.Serializable;

import javax.net.SocketFactory;

public abstract class NodewoxService extends Service {

    // service actions
    public static final String ACTION_CONNECT = "connect";
    public static final String ACTION_DISCONNECT = "disconnect";
    public static final String ACTION_PUBLISH = "publish";
    public static final String ACTION_SUBSCRIBE = "subscribe";
    public static final String ACTION_UNSUBSCRIBE = "unsubscribe";

    private NodewoxApplication mApp = null;
    private MqttAsyncClient mMqttCli = null;

    private NetworkType mNetworkType = NetworkType.NONE;

    // network state change listener
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo netInfo = cm.getActiveNetworkInfo();
                NetworkType ntype = mNetworkType;
                if (netInfo != null && netInfo.isAvailable()) {
                    NetworkInfo.State state = cm.getNetworkInfo(netInfo.getType()).getState();
                    if (state == NetworkInfo.State.CONNECTED) {
                        switch (netInfo.getType()) {
                            case ConnectivityManager.TYPE_WIFI:
                                ntype = NetworkType.WIFI;
                                break;
                            case ConnectivityManager.TYPE_MOBILE:
                                ntype = NetworkType.MOBILE;
                                break;
                        }
                    } else
                        ntype = NetworkType.NONE;
                } else
                    ntype = NetworkType.NONE;

                if (ntype != mNetworkType) {
                    mNetworkType = ntype;
                    onNetworkChange();
                }
            }
        }
    };

    // return service binder
    protected abstract IBinder getBinder();

    // mqtt server url
    public abstract String getMqttAddr();

    protected boolean onMqttBeforeConnect() {
        return true;  // returns false to prevent connect
    }

    protected boolean onMqttBeforeDisconnect() {
        return true;  // returns false to prevent disconnect
    }

    @Override
    public IBinder onBind(Intent arg0) {
        return getBinder();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mApp = (NodewoxApplication) getApplication();
        IntentFilter mFilter = new IntentFilter();
        mFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(mReceiver, mFilter);
    }

    @Override
    public void onDestroy() {
        disconnect();
        unregisterReceiver(mReceiver);
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            Log.w("nodewox/service", "onStartCommand(): intent is null");
            return super.onStartCommand(intent, flags, startId);
        }

        String action = intent.getAction();
        Log.v("nodewox/service", "onStartCommand() action=" + action);
        if (action != null)
            execAction(action, intent.getExtras());

        return super.onStartCommand(intent, flags, startId);
    }

    public void execAction(String action, Bundle bundle) {
        Serializable ctx = null;
        if (bundle != null) {
            if (bundle.containsKey("context"))
                ctx = bundle.getSerializable("context");
        }

        switch (action) {
            case ACTION_CONNECT:
                connect();
                break;

            case ACTION_DISCONNECT:
                disconnect();
                break;

            case ACTION_SUBSCRIBE:
                if (bundle != null && bundle.containsKey("topics")) {
                    String[] topics = bundle.getStringArray("topics");
                    subscribe(topics, ctx);
                } else
                    _event(EventType.SUB_FAIL, null, ctx, new Throwable("invalid call"));
                break;

            case ACTION_UNSUBSCRIBE:
                if (bundle != null && bundle.containsKey("topics")) {
                    String[] topics = bundle.getStringArray("topics");
                    unsubscribe(topics, ctx);
                } else
                    _event(EventType.UNSUB_FAIL, null, ctx, new Throwable("invalid call"));
                break;

            case ACTION_PUBLISH:
                if (bundle != null && bundle.containsKey("topic")) {
                    String topic = bundle.getString("topic");
                    byte[] payload = null;
                    int qos = 0;
                    boolean retained = false;

                    if (bundle.containsKey("payload"))
                        payload = bundle.getByteArray("payload");

                    if (bundle.containsKey("qos"))
                        qos = bundle.getInt("qos");

                    if (bundle.containsKey("retained"))
                        retained = bundle.getBoolean("retained");

                    publish(topic, payload, qos, retained, ctx);
                } else
                    _event(EventType.PUB_FAIL, null, ctx, new Throwable("invalid call"));
                break;

            default:
                Log.w("nodewox/service", "unkown action: " + action);
        }
    }

    protected NetworkType getNetworkType() {
        return mNetworkType;
    }

    protected String getMqttClientId() {
        return "";
    }

    protected boolean isMqttClear() {
        return true;
    }

    protected String getMqttUsername() {
        return "";
    }

    protected String getMqttPassword() {
        return "";
    }

    // get trust ca
    protected byte[] getMqttCa() {
        return null;
    }

    protected String getMqttCaPass() {
        return "";
    }

    // return client cert in pkcs12 format
    protected byte[] getMqttCert() {
        return null;
    }

    // client cert password
    protected String getMqttCertPass() {
        return "";
    }

    protected int getMqttKeepAlive() {
        return 60;
    }

    protected String getMqttWill() {
        return "";
    }

    protected byte[] getMqttWillPayload() {
        return "".getBytes();
    }

    protected int getMqttWillQos() {
        return 0;
    }

    protected void onNetworkChange() {
        switch (mNetworkType) {
            case WIFI:
            case MOBILE:
                // network available, try connect
                Log.v("nodewox/servce", "network available");
                if (getMqttAddr().length() > 0)
                    connect();
                break;
            case NONE:
                // network unavailable
                break;
        }
    }

    // emit mqtt event
    private synchronized void _event(EventType eventType, Bundle data, Object ctx, Throwable error) {
        if (mApp.EventHandler != null) {
            if (ctx != null) {
                if (data == null)
                    data = new Bundle();
                data.putSerializable("context", (Serializable) ctx);
            }

            if (error != null) {
                if (data == null)
                    data = new Bundle();
                data.putString("error", error.getMessage());
            }

            Message m = new Message();
            m.what = eventType.ordinal();
            if (data != null)
                m.setData(data);

            mApp.EventHandler.sendMessage(m);
        }
    }

    public synchronized boolean connect() {
        if (mMqttCli != null && mMqttCli.isConnected()) {
            // we are already connected
            return true;
        }

        if (getMqttAddr().length() == 0) {
            // invalid addr
            return false;
        }

        if (!onMqttBeforeConnect()) {
            // connect prevented
            return false;
        }

        _event(EventType.CONNECTING, null, null, null);

        // setup connect options
        MqttConnectOptions opts = new MqttConnectOptions();
        String username = getMqttUsername();
        String password = getMqttPassword();
        String cid = getMqttClientId();
        if (cid == null) cid = "";

        if (username.length() > 0) {
            opts.setUserName(username);
            opts.setPassword(password.toCharArray());
        }

        if (cid.length() == 0)
            opts.setCleanSession(true);
        else
            opts.setCleanSession(isMqttClear());

        opts.setKeepAliveInterval(getMqttKeepAlive());

        String will = getMqttWill();
        if (will != null && will.length() > 0) {
            opts.setWill(will, getMqttWillPayload(), getMqttWillQos(), false);
        }

        // make ssl socket
        SocketFactory sf = mApp.makeSSLSocketFactory(getMqttCa(), getMqttCaPass(), getMqttCert(), getMqttCertPass());
        if (sf != null)
            opts.setSocketFactory(sf);

        // create mqtt client
        try {
            mMqttCli = new MqttAsyncClient(getMqttAddr(), cid, new MemoryPersistence());
        } catch (MqttException e) {
            mMqttCli = null;
            _event(EventType.CONNECT_FAIL, null, null, e);
            return false;
        }

        // setup mqtt callback listener
        mMqttCli.setCallback(new LocalMqttCallback(this));

        try {
            mMqttCli.connect(opts, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken token) {
                    Log.v("nodewox/service", "connected");
                    _event(EventType.CONNECT_SUCCESS, null, token.getUserContext(), null);
                }

                @Override
                public void onFailure(IMqttToken token, Throwable e) {
                    mMqttCli = null;
                    _event(EventType.CONNECT_FAIL, null, token.getUserContext(), e);
                }
            });
        } catch (MqttException e) {
            _event(EventType.CONNECT_FAIL, null, null, e);
        }

        return true;
    }

    public synchronized void disconnect() {
        if (mMqttCli != null) {
            if (mMqttCli.isConnected() && onMqttBeforeDisconnect()) {
                try {
                    mMqttCli.disconnect();
                    mMqttCli.close();
                } catch (MqttException e) {
                    e.printStackTrace();
                }
            }

            mMqttCli = null;
            _event(EventType.CONNECT_CLOSE, null, null, null);
        }
    }

    public synchronized boolean isMqttConnected() {
        return (mMqttCli != null && mMqttCli.isConnected());
    }

    public synchronized void subscribe(String[] topics, Serializable ctx) {
        if (!isMqttConnected()) {
            Bundle data = new Bundle();
            data.putStringArray("topics", topics);
            _event(EventType.SUB_FAIL, data, ctx, new Throwable("not connected"));
            return;
        }

        try {
            int[] qos = new int[topics.length];
            for (int i = 0; i < topics.length; i++)
                qos[i] = 2;

            mMqttCli.subscribe(topics, qos, ctx, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken token) {
                    Bundle data = new Bundle();
                    data.putStringArray("topics", token.getTopics());
                    _event(EventType.SUB_SUCCESS, data, token.getUserContext(), null);
                }

                @Override
                public void onFailure(IMqttToken token, Throwable e) {
                    Bundle data = new Bundle();
                    data.putStringArray("topics", token.getTopics());
                    _event(EventType.SUB_FAIL, data, token.getUserContext(), e);
                }
            });

        } catch (MqttException e) {
            Bundle data = new Bundle();
            data.putStringArray("topics", topics);
            _event(EventType.SUB_FAIL, data, ctx, e);
        }
    }

    public synchronized void unsubscribe(String[] topics, Serializable ctx) {
        if (!isMqttConnected()) {
            Bundle data = new Bundle();
            data.putStringArray("topics", topics);
            _event(EventType.UNSUB_FAIL, data, ctx, new Throwable("not connected"));
            return;
        }

        try {
            mMqttCli.unsubscribe(topics, ctx, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken token) {
                    Bundle data = new Bundle();
                    data.putStringArray("topics", token.getTopics());
                    _event(EventType.UNSUB_SUCCESS, data, token.getUserContext(), null);
                }

                @Override
                public void onFailure(IMqttToken token, Throwable e) {
                    Bundle data = new Bundle();
                    data.putStringArray("topics", token.getTopics());
                    _event(EventType.UNSUB_FAIL, data, token.getUserContext(), e);
                }
            });

        } catch (MqttException e) {
            Bundle data = new Bundle();
            data.putStringArray("topics", topics);
            _event(EventType.UNSUB_FAIL, data, ctx, e);
        }
    }

    public void publish(String topic, byte[] payload, int qos, boolean retained, Serializable ctx) {
        if (!isMqttConnected()) {
            _event(EventType.PUB_FAIL, null, ctx, new Throwable("not connected"));
            return;
        }

        MqttMessage msg = new MqttMessage();
        msg.setQos(qos);
        msg.setRetained(retained);
        if (payload != null && payload.length > 0)
            msg.setPayload(payload);

        try {
            mMqttCli.publish(topic, msg, ctx, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken token) {
                }

                @Override
                public void onFailure(IMqttToken token, Throwable e) {
                    _event(EventType.PUB_FAIL, null, token.getUserContext(), e);
                }
            });

        } catch (MqttException e) {
            _event(EventType.PUB_FAIL, null, ctx, e);
        }
    }

    public boolean publishSync(String topic, byte[] payload, int qos, boolean retained, int timeout) {
        if (!isMqttConnected())
            return false;

        MqttMessage msg = new MqttMessage();
        msg.setQos(qos);
        msg.setRetained(retained);
        if (payload != null && payload.length > 0)
            msg.setPayload(payload);

        try {
            IMqttDeliveryToken token = mMqttCli.publish(topic, payload, qos, retained);
            token.waitForCompletion(timeout);
            return true;
        } catch (MqttException e) {
            return false;
        }
    }

    // mqtt event types
    public enum EventType {
        CONNECTING,
        CONNECT_SUCCESS,
        CONNECT_FAIL,
        CONNECT_CLOSE,
        CONNECT_LOST,
        SUB_SUCCESS,
        SUB_FAIL,
        UNSUB_SUCCESS,
        UNSUB_FAIL,
        PUB_COMPLETE,
        PUB_FAIL,
        MESSAGE,
        UNKOWN,
    }

    public enum NetworkType {
        NONE, WIFI, MOBILE
    }

    private static class LocalMqttCallback implements MqttCallback {
        private final NodewoxService mCtx;

        public LocalMqttCallback(NodewoxService ctx) {
            mCtx = ctx;
        }

        @Override
        public void connectionLost(Throwable e) {
            mCtx._event(EventType.CONNECT_LOST, null, null, e);
        }

        @Override
        public void messageArrived(String topic, MqttMessage msg) throws Exception {
            Bundle data = new Bundle();
            data.putString("topic", topic);
            if (msg.getPayload() != null)
                data.putByteArray("payload", msg.getPayload());
            data.putInt("qos", msg.getQos());
            data.putBoolean("duplicate", msg.isDuplicate());
            data.putBoolean("retained", msg.isRetained());

            mCtx._event(EventType.MESSAGE, data, null, null);
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
            mCtx._event(EventType.PUB_COMPLETE, null, token.getUserContext(), null);
        }
    }

}
