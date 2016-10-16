package org.nodewox.client;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import static org.nodewox.client.NodeParam.ParamFlag.STATIC;
import static org.nodewox.client.NodeParam.ParamFlag.VOLATILE;
import static org.nodewox.client.NodeParam.ParamValueType.BOOL;
import static org.nodewox.client.NodeParam.ParamValueType.FLOAT;
import static org.nodewox.client.NodeParam.ParamValueType.INT;
import static org.nodewox.client.NodeParam.ParamValueType.STRING;

public class NodeParam<T extends Object> {

    private Node node;
    private String key;
    private ParamValueType type;
    private ParamFlag flag = VOLATILE;
    private String name = "";
    private String comment = "";
    private int seq = 0;

    private boolean writable = true;
    private boolean disabled = false;

    private T initValue;
    private T value;

    public NodeParam(Node node, String key, String name, T val) {
        this.node = node;
        this.key = key;

        if (BuildConfig.DEBUG && key.length() == 0)
            throw new AssertionError("empty key not allowed");

        if (BuildConfig.DEBUG && val == null)
            throw new AssertionError("initial value must not be null");

        this.name = name;
        this.value = val;
        this.initValue = val;

        if (val instanceof String) {
            type = STRING;
        } else if (value instanceof Integer) {
            type = INT;
        } else if (value instanceof Float) {
            type = FLOAT;
        } else if (value instanceof Boolean) {
            type = BOOL;
        } else {
            type = STRING;
            Log.e("nodewox/param", "param data type accept only int, float, bool, string, default to string");
        }
    }

    public void reset() {
        value = initValue;
    }

    public String getKey() {
        return key;
    }

    public String getName() {
        return name;
    }

    public void setName(String v) {
        name = v;
    }

    public ParamValueType getType() {
        return type;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public ParamFlag getFlag() {
        return flag;
    }

    public void setFlag(ParamFlag flag) {
        this.flag = flag;
    }

    public T getValue() {
        if (BuildConfig.DEBUG && (T) value == null)
            throw new AssertionError("param value must not be null");
        return value;
    }

    public boolean setValue(T val) {
        if (BuildConfig.DEBUG && (T) val == null)
            throw new AssertionError("param value must not be null, but set to " + val);

        if (flag != STATIC && !disabled && !value.equals(val)) {
            value = val;
            node.onParamChanged(this);
            return true;
        } else
            return false;
    }

    public void setSeq(int v) {
        seq = v;
    }

    public boolean isReadOnly() {
        return !writable;
    }

    public void setReadOnly(boolean v) {
        writable = !v;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String v) {
        comment = v;
    }

    public JSONObject asJSON() throws JSONException {
        JSONObject obj = new JSONObject();

        obj.put("key", key);
        obj.put("name", name);
        obj.put("writable", writable);
        obj.put("seq", seq);

        if (comment.length() > 0)
            obj.put("comment", comment);

        switch (flag) {
            case VOLATILE:
                obj.put("flag", "volatile");
                break;
            case PERSISTENT:
                obj.put("flag", "persistent");
                break;
            case READONLY:
                obj.put("flag", "readonly");
                break;
            case STATIC:
                obj.put("flag", "static");
                break;
        }

        switch (type) {
            case STRING:
                obj.put("datatype", "string");
                obj.put("value", value);
                break;
            case INT:
                obj.put("datatype", "int");
                obj.put("value", value);
                break;
            case FLOAT:
                obj.put("datatype", "float");
                obj.put("value", value);
                break;
            case BOOL:
                obj.put("datatype", "bool");
                obj.put("value", value);
                break;
        }

        return obj;
    }

    public void configure(JSONObject data) throws JSONException {
        if (!data.isNull("datatype")) {
            switch (data.getString("datatype")) {
                case "string":
                    disabled = !(value instanceof String);
                    break;
                case "int":
                    disabled = !(value instanceof Integer);
                    break;
                case "float":
                    disabled = !(value instanceof Float);
                    break;
                case "bool":
                    disabled = !(value instanceof Boolean);
                    break;
                default:
                    disabled = true;
            }
        }

        if (!data.isNull("seq"))
            seq = data.getInt("seq");

        if (!data.isNull("writable")) {
            writable = data.getBoolean("writable");
        }

        if (!disabled && !data.isNull("value")) {
            switch (type) {
                case STRING:
                    setValue((T) data.getString("value"));
                    break;
                case INT:
                    setValue((T) Integer.valueOf(data.getInt("value")));
                    break;
                case FLOAT:
                    setValue((T) Double.valueOf(data.getDouble("value")));
                    break;
                case BOOL:
                    setValue((T) Boolean.valueOf(data.getBoolean("value")));
                    break;
            }
        }
    }

    public enum ParamFlag {VOLATILE, PERSISTENT, STATIC, READONLY}

    public enum ParamValueType {INT, FLOAT, STRING, BOOL}
}
