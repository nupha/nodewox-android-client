package org.nodewox.client;

import android.app.Application;

public abstract class NodewoxApplication extends Application {

    protected Node mNode = null;

    // make REST object
    public abstract NodewoxREST getRest();

    // get binding mqttservice
    protected abstract NodewoxService getService();

    public Node getNode() {
        return mNode;
    }

}
