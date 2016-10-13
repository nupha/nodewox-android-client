package org.nodewox.client;

import java.io.Serializable;

public interface MessageSensible {

    Messenger getMessenger();

    // handlers
    void onBeforeConnect();

    void onBeforeDisconnect();

    void onConnecting(Serializable ctx);

    void onConnected(Serializable ctx);

    void onConnectFail(Serializable ctx, String error);

    void onDisconnected(Serializable ctx);

    void onConnectionLost(Serializable ctx, String error);

    void onSubscribe(Serializable ctx, String[] topics, String error);

    void onUnsubscribe(Serializable ctx, String[] topics, String error);

    void onPublish(Serializable ctx, String error);

    void onMessage(String topic, byte[] payload, int qos, final boolean duplicate, boolean retained);

}
