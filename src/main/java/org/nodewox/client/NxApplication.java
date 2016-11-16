package org.nodewox.client;

import android.app.Application;

public abstract class NxApplication extends Application {

    protected Node mRootNode = null;

    // get binding mqttservice
    protected abstract NxService getService();

    public Node getRootNode() {
        return mRootNode;
    }

    public String getTopicPrefix() {
        return "";
    }
}
