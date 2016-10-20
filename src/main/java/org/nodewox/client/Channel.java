package org.nodewox.client;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import static org.nodewox.client.Channel.DataType.RAW;

public abstract class Channel extends Node {

    private final Gender gender;
    private final DataType datatype;
    private final int datadim;

    public Channel(Thing thing, String key, Gender gender, DataType dtype) {
        super(thing.getApp(), key, thing);
        this.gender = gender;
        this.datatype = dtype;
        this.datadim = (dtype == RAW) ? 0 : 1;
    }

    public Channel(Thing thing, String key, Gender gender, DataType dtype, int dim) {
        super(thing.getApp(), key, thing);
        this.gender = gender;
        this.datatype = dtype;
        this.datadim = (dtype == RAW) ? 0 : (dim > 0 ? dim : 1);
    }

    public DataType getDataType() {
        return datatype;
    }

    public int getDataDim() {
        return datadim;
    }

    public Gender getGender() {
        return gender;
    }

    @Override
    public JSONObject asJSON() throws JSONException {
        JSONObject res = super.asJSON();

        switch (gender) {
            case MALE:
                res.put("gender", "M");
                break;
            case FEMALE:
                res.put("gender", "F");
                break;
        }

        String dt = "";
        switch (datatype) {
            case RAW:
                break;
            case BYTE:
                dt = "byte";
                break;
            case INT16:
                dt = "int16";
                break;
            case INT32:
                dt = "int32";
                break;
            case INT64:
                dt = "int64";
                break;
            case FLOAT:
                dt = "float";
                break;
            case BOOL:
                dt = "bool";
                break;
            case STRING:
                dt = "string";
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

    public enum Gender {MALE, FEMALE}

    public enum DataType {RAW, BYTE, INT16, INT32, INT64, FLOAT, STRING, BOOL}
}
