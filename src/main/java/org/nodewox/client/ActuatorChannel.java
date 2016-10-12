package org.nodewox.client;

import java.util.Map;

public abstract class ActuatorChannel extends Channel {

    public ActuatorChannel(Thing thing, String key, DataType datatype, int dim) {
        super(thing, key, FlowDir.IN, datatype, dim);
    }

    public ActuatorChannel(Thing thing, String key, DataType datatype) {
        super(thing, key, FlowDir.IN, datatype);
    }

    protected abstract void perform(int src, int gid, Object[] data);

    @Override
    public final void handlePacket(NodeTalk.Packet packet) {
        Object[] data = null;
        boolean ok = false;

        switch (getDataType()) {
            case ANY:
                ok = true;
                break;
            case INT:
                data = packet.getIntArray().getValueList().toArray(new Long[0]);
                ok = data.length > 0;
                break;
            case FLOAT:
                data = packet.getNumArray().getValueList().toArray(new Double[0]);
                ok = data.length > 0;
                break;
            case STRING:
                data = packet.getStrArray().getValueList().toArray(new String[0]);
                ok = data.length > 0;
                break;
            case BOOL:
                data = packet.getBoolArray().getValueList().toArray(new Boolean[0]);
                ok = data.length > 0;
                break;
        }

        if (ok)
            perform(packet.getSrc(), packet.getGid(), data);
    }

}
