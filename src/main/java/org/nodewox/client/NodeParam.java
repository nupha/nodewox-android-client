package org.nodewox.client;

import org.json.JSONException;
import org.json.JSONObject;

public class NodeParam<T extends Object> {

    private final Node node;
    private final String key;
    private final T initValue;
    private String name = "";
    private String comment = "";
    private int seq = 0;
    private boolean persistent = false;
    private boolean writable = true;
    private boolean disabled = false;
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

    public boolean isDisabled() {
        return disabled;
    }

    public T getValue() {
        if (BuildConfig.DEBUG && (T) value == null)
            throw new AssertionError("param value must not be null");
        return value;
    }

    public boolean setValue(T val) {
        if (BuildConfig.DEBUG && (T) val == null)
            throw new AssertionError("param value must not be null, but set to " + val);

        if (!disabled && !value.equals(val)) {
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
        obj.put("persistent", persistent);
        obj.put("writable", writable);
        obj.put("seq", seq);

        if (comment.length() > 0)
            obj.put("comment", comment);

        if (value instanceof String) {
            obj.put("datatype", "string");
            obj.put("value", value);
        } else if (value instanceof Integer) {
            obj.put("datatype", "int");
            obj.put("value", value);
        } else if (value instanceof Float) {
            obj.put("datatype", "number");
            obj.put("value", value);
        } else if (value instanceof Boolean) {
            obj.put("datatype", "bool");
            obj.put("value", value);
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
            if (value instanceof String)
                setValue((T) data.getString("value"));
            else if (value instanceof Integer)
                setValue((T) Integer.valueOf(data.getInt("value")));
            else if (value instanceof Float)
                setValue((T) Double.valueOf(data.getDouble("value")));
            else if (value instanceof Boolean)
                setValue((T) Boolean.valueOf(data.getBoolean("value")));
        }
    }

}
