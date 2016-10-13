package org.nodewox.client;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public abstract class Channel extends Node {

    private final FlowDir flow;
    private DataType datatype;
    private int datadim;

    public Channel(Thing thing, String key, FlowDir flow, DataType datatype, int dim) {
        super(thing.getApp(), key, thing);
        this.flow = flow;
        this.datatype = datatype;
        this.datadim = datatype != datatype.ANY ? dim : 0;
    }

    public Channel(Thing thing, String key, FlowDir flow, DataType datatype) {
        super(thing.getApp(), key, thing);
        this.flow = flow;
        this.datatype = datatype;
        this.datadim = 0;
    }

    // handle incomming packet
    public abstract void handlePacket(NodeTalk.Packet packet);

    public void setDataType(DataType dtype, int dim) {
        datatype = dtype;
        this.datadim = dtype != datatype.ANY ? dim : 0;
    }

    public DataType getDataType() {
        return datatype;
    }

    public int getDataDim() {
        return datadim;
    }

    public FlowDir getFlow() {
        return flow;
    }

    @Override
    public JSONObject asJSON() throws JSONException {
        JSONObject res = super.asJSON();

        switch (flow) {
            case IN:
                res.put("flow", "I");
                break;
            case OUT:
                res.put("flow", "O");
                break;
        }

        String dt = "";
        switch (datatype) {
            case ANY:
                break;
            case INT:
                dt = "int";
                break;
            case FLOAT:
                dt = "number";
                break;
            case BOOL:
                dt = "bool";
                break;
            case STRING:
                dt = "string";
                break;
            case BIN:
                dt = "bin";
                break;
        }

        if (dt.length() > 0) {
            JSONArray arr = new JSONArray();
            arr.put(dt);
            arr.put(datadim);
            res.put("datatype", arr);
        }

        return res;
    }

    public enum FlowDir {IN, OUT}
}
