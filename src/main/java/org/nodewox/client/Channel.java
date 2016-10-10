package org.nodewox.client;

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
        mNodeType = NodeType.CHANNEL;
    }

    public Channel(Thing thing, String key, FlowDir flow, DataType datatype) {
        super(thing.getApp(), key, thing);
        this.flow = flow;
        this.datatype = datatype;
        this.datadim = 0;
        mNodeType = NodeType.CHANNEL;
    }

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

        switch (datatype) {
            case ANY:
                break;
            case INT:
                res.put("datatype", "int");
                break;
            case FLOAT:
                res.put("datatype", "number");
                break;
            case BOOL:
                res.put("datatype", "bool");
                break;
            case STRING:
                res.put("datatype", "string");
                break;
            case BIN:
                res.put("datatype", "bin");
                break;
        }

        if (datadim > 0)
            res.put("datadim", datadim);

        return res;
    }

    public enum FlowDir {IN, OUT}
}
