package org.nodewox.client;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public abstract class Thing extends Node {

    protected final Map<String, Channel> channels = new HashMap<>();

    public Thing(NodewoxApplication app) {
        super(app);
    }

    public abstract JSONObject asJSON();

    @Override
    public boolean isThing() {
        return true;
    }

    @Override
    public boolean isRegistered() {
        return mKey != null && mKey.length() > 0 && mSecret != null && mCertP12 != null;
    }

    public Map<String, Channel> getChannels() {
        return channels;
    }

    public Channel getChannelByKey(String key) {
        return channels.get(key);
    }

    public Channel getChannelByID(int id) {
        if (id > 0) {
            for (Channel ch : channels.values()) {
                if (ch.getID() == id)
                    return ch;
            }
        }
        return null;
    }

    protected boolean setProfile(JSONObject data) {
        try {
            mID = data.getInt("id");
            if (data.has("mqtt"))
                setMqttURI(data.getString("mqtt"));

            // setup channel id/param
            if (data.has("channels")) {
                JSONObject chans = data.getJSONObject("channels");
                Iterator<String> it = chans.keys();
                while (it.hasNext()) {
                    String key = it.next();
                    Channel ch = getChannelByKey(key);
                    if (ch != null) {
                        JSONObject vals = chans.getJSONObject(key);
                        ch.setID(vals.getInt("id"));
                        ch.config(vals);
                    }
                }
            }

            return true;

        } catch (JSONException e) {
            Log.e("nodewox", e.getMessage());
        }

        return false;
    }

    public void register(String username, String password, final ThingRegisterListener callback) {
        JSONObject o = asJSON();
        if (mKey == null || o == null) {
            callback.onFail("not a thing");
            return;
        }
        byte[] data = o.toString().getBytes();

        HashMap<String, String> headers = new HashMap<>();
        headers.put("authorization", "USERPW " + username + "\t" + password);
        headers.put("charset", "utf-8");
        headers.put("x-requested-with", "XMLHttpRequest");
        headers.put("content-type", "application/json");

        mRest.setClientCert(null, "");
        mRest.post("thing/register?trust=BKS&cert=PKCS12", data, headers, new HttpRequestListener() {
            @Override
            public void onStart() {
                callback.onStart();
            }

            @Override
            public void onError(int status, String errmsg) {
                if (status == 403)
                    callback.onFail("username/password does not match");
                else
                    callback.onFail(errmsg);
            }

            @Override
            public void onSuccess(JSONObject resp) {
                try {
                    resp.put("key", mKey);
                    resp.put("secret", mSecret);
                    loadConfig();
                    callback.onSuccess();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onFail(String errmsg) {
                callback.onFail(errmsg);
            }
        });
    }

    public void loadRemoteProfile(final ThingProfileListener callback) {
        if (!isRegistered()) {
            callback.onFail("not yet registered");
            return;
        }

        HashMap<String, String> headers = new HashMap<>();
        headers.put("authorization", "CERT " + mSecret);
        headers.put("x-requested-with", "XMLHttpRequest");
        headers.put("charset", "utf-8");

        mRest.setClientCert(getCert(), getSecret());
        mRest.get("thing/profile", headers, new HttpRequestListener() {
            @Override
            public void onStart() {
                callback.onStart();
            }

            @Override
            public void onError(int status, String errmsg) {
                switch (status) {
                    case 403:
                        callback.onFail("invalid thing cert");
                        break;
                    case 301:
                        callback.onFail("thing not exists");
                        break;
                    default:
                        if (errmsg == null || errmsg.length() == 0)
                            errmsg = String.valueOf(status);
                        callback.onFail(errmsg);
                }
            }

            @Override
            public void onSuccess(JSONObject resp) {
                Log.v("nodewox", "response " + resp.toString());
                setProfile(resp);
                callback.onSuccess();
            }

            @Override
            public void onFail(String errmsg) {
                callback.onFail(errmsg);
            }
        });
    }

    public interface ThingRegisterListener {
        void onStart();

        void onSuccess();

        void onFail(final String errmsg);
    }

    public interface ThingProfileListener {
        void onStart();

        void onSuccess();

        void onFail(final String errmsg);
    }

}