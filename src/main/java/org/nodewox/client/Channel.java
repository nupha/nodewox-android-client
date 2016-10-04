package org.nodewox.client;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public abstract class Channel {
    protected final Thing mThing;

    protected String mKey = "";
    protected String mName = "";
    protected FlowType mFlow = FlowType.IN;
    protected DataType mDatatype = DataType.STRING;
    protected int mSeq = 0;
    protected String mComment = "";
    protected Map<String, NodeParam> mParams = new HashMap<>();

    private int mID = 0;

    public Channel(Thing thing) {
        mThing = thing;
    }

    public Thing getThing() {
        return mThing;
    }

    public abstract void perform(byte[] payload);

    public String getKey() {
        return mKey;
    }

    public int getID() {
        return mID;
    }

    public void setID(int id) {
        mID = id;
    }

    public FlowType getFlow() {
        return mFlow;
    }

    public Map<String, NodeParam> getParams() {
        return mParams;
    }

    public JSONObject request(int from, JSONObject content) {
        boolean reply = false;
        JSONObject res = new JSONObject();

        try {
            if (content == null) {
                res.put("status", getStatus());
                reply = true;

            } else {
                if (content.has("status")) {
                    res.put("status", getStatus());
                    reply = true;
                }

                if (content.has("config")) {
                    if (config(content.getJSONObject("config")) > 0) {
                        res.put("status", getStatus());
                        reply = true;
                    }
                }
            }
        } catch (JSONException e) {
        }

        if (reply)
            return res;
        else
            return null;
    }

    public void checkParamConfig(String key, NodeParam param) {
    }

    public int config(JSONObject data) {
        int cnt = 0;

        for (Map.Entry<String, NodeParam> item : mParams.entrySet()) {
            String pkey = item.getKey();

            if (data.has(pkey)) {
                NodeParam param = item.getValue();
                switch (param.datatype) {
                    case INT:
                        try {
                            param.iVal = data.getInt(pkey);
                            cnt++;
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        break;
                    case FLOAT:
                        try {
                            param.fVal = data.getDouble(pkey);
                            cnt++;
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        break;
                    case BOOL:
                        try {
                            param.bVal = data.getBoolean(pkey);
                            cnt++;
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        break;
                    case STRING:
                        try {
                            param.sVal = data.getString(pkey);
                            cnt++;
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        break;
                }

                checkParamConfig(pkey, param);
            }
        }

        return cnt;
    }

    public JSONObject getStatus() {
        JSONObject cfg = new JSONObject();
        try {
            for (Map.Entry<String, NodeParam> p : mParams.entrySet()) {
                switch (p.getValue().datatype) {
                    case INT:
                        cfg.put(p.getKey(), p.getValue().iVal);
                        break;
                    case FLOAT:
                        cfg.put(p.getKey(), p.getValue().fVal);
                        break;
                    case BOOL:
                        cfg.put(p.getKey(), p.getValue().bVal);
                        break;
                    default:
                        cfg.put(p.getKey(), p.getValue().sVal);
                        break;
                }
            }

            return cfg;

        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    public JSONObject asJSON() {
        JSONObject res = new JSONObject();
        try {
            res.put("name", mName);
            res.put("seq", mSeq);

            switch (mFlow) {
                case IN:
                    res.put("flow", "I");
                    break;
                case OUT:
                    res.put("flow", "O");
                    break;
            }

            switch (mDatatype) {
                case INT:
                    res.put("datatype", "int");
                    break;
                case FLOAT:
                    res.put("datatype", "float");
                    break;
                case BOOL:
                    res.put("datatype", "bool");
                    break;
                case STRING:
                    res.put("datatype", "string");
                    break;
                case JSON:
                    res.put("datatype", "json");
                    break;
            }

            if (mComment.length() > 0)
                res.put("comment", mComment);

            // params
            JSONObject configs = new JSONObject();
            for (Map.Entry<String, NodeParam> item : mParams.entrySet()) {
                JSONObject cfg = new JSONObject();

                cfg.put("name", item.getValue().name);
                switch (item.getValue().datatype) {
                    case INT:
                        cfg.put("datatype", "int");
                        break;
                    case FLOAT:
                        cfg.put("datatype", "float");
                        break;
                    case BOOL:
                        cfg.put("datatype", "bool");
                        break;
                    case STRING:
                        cfg.put("datatype", "string");
                        break;
                    case JSON:
                        cfg.put("datatype", "json");
                        break;
                }
                cfg.put("comment", item.getValue().comment);
                cfg.put("persistent", item.getValue().persistent);
                cfg.put("writable", item.getValue().writable);
                cfg.put("seq", item.getValue().seq);

                switch (item.getValue().datatype) {
                    case INT:
                        cfg.put("value", item.getValue().iVal);
                        break;
                    case FLOAT:
                        cfg.put("value", item.getValue().fVal);
                        break;
                    case BOOL:
                        cfg.put("value", item.getValue().bVal);
                        break;
                    case JSON:
                        break;
                    default:
                        if (item.getValue().sVal != null)
                            cfg.put("value", item.getValue().sVal);
                }

                configs.put(item.getKey(), cfg);
            }

            if (configs.length() > 0)
                res.put("params", configs);

            return res;

        } catch (JSONException e) {
            Log.e("nodewox", e.toString());
            return null;
        }
    }

    public enum FlowType {IN, OUT}

    public enum DataType {INT, FLOAT, STRING, JSON, BOOL, ANY}
}
