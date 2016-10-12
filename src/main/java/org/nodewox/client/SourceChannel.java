package org.nodewox.client;

import android.util.Log;

import java.util.Arrays;
import java.util.Map;

public abstract class SourceChannel<T extends Object> extends Channel {

    public SourceChannel(Thing thing, String key, DataType datatype, int dim) {
        super(thing, key, FlowDir.OUT, datatype, dim);
    }

    public SourceChannel(Thing thing, String key, DataType datatype) {
        super(thing, key, FlowDir.OUT, datatype);
    }

    @Override
    public final void handlePacket(NodeTalk.Packet packet) {
    }

}
