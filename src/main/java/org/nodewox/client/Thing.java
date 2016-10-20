package org.nodewox.client;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public abstract class Thing extends Node {

    private String mSecret = null;

    public Thing(NxApplication app, String key) {
        super(app, key);
    }

    protected abstract boolean loadConfig();

    protected abstract void saveConfig(JSONObject data);

    protected abstract void onUnregistered();

    public String getSecret() {
        return mSecret;
    }

    public void setSecret(String v) {
        mSecret = v;
    }

    public boolean isRegistered() {
        String k = getKey();
        if (k != null && k.length() > 0) {
            if (this instanceof MessageSensible) {
                Messenger mgr = ((MessageSensible) this).getMessenger();
                return mgr.getCert() != null && mgr.getCertPass() != null;
            }
        }
        return false;
    }

    @Override
    public Map<String, JSONObject> handleRequest(String action, Map<String, Object> params, int[] children) {
        if (action.equals("unregister")) {
            if (isRegistered()) {
                ((MessageSensible) this).getMessenger().disconnect();

                JSONObject cfg = new JSONObject();
                try {
                    cfg.put("key", getKey());
                    cfg.put("secret", getSecret());
                } catch (JSONException e) {
                    Log.w("nodewox/thing", "make initial config");
                }
                saveConfig(cfg);

                reset();
                onUnregistered();
            }
            return null;

        } else
            return super.handleRequest(action, params, children);
    }

    @Override
    public JSONObject asJSON() throws JSONException {
        JSONObject res = super.asJSON();
        if (mSecret != null)
            res.put("secret", mSecret);
        return res;
    }

    @Override
    protected void configure(JSONObject data) throws JSONException {
        super.configure(data);
        if (!data.isNull("mqtt") && this instanceof MessageSensible) {
            Messenger mgr = ((MessageSensible) this).getMessenger();
            mgr.setAddr(data.getString("mqtt"));
        }
    }

    public void register(String username, String password, final ResponseListener callback) {
        if (mRest != null) {
            if (getKey() == null) {
                callback.onFail(-1, "thing ID is missing");
                return;
            }

            byte[] data;
            try {
                JSONObject o = asJSON();
                data = o.toString().getBytes();
            } catch (JSONException e) {
                Log.e("nodewox/thing/register", e.getMessage());
                callback.onFail(-1, e.getMessage());
                return;
            }

            HashMap<String, String> headers = new HashMap<>();
            headers.put("authorization", "USERPW " + username + "\t" + password);
            headers.put("charset", "utf-8");
            headers.put("x-requested-with", "XMLHttpRequest");
            headers.put("content-type", "application/json");

            mRest.setClientCert(null, "");
            mRest.post("thing/register?trust=BKS&cert=PKCS12", data, headers, new RestResponseListener() {
                @Override
                public void onResponse(final int status, final byte[] resp) {
                }

                @Override
                public void onError(int status, String errmsg) {
                    if (status == 403)
                        callback.onFail(status, "username/password does not match");
                    else
                        callback.onFail(status, errmsg);
                }

                @Override
                public void onSuccess(JSONObject resp) {
                    try {
                        resp.put("key", getKey());
                        resp.put("secret", mSecret);
                        callback.onSuccess(resp);
                    } catch (JSONException e) {
                        Log.e("nodewox/thing/register", e.getMessage());
                        callback.onFail(-1, e.getMessage());
                    }
                }

                @Override
                public void onFail(int code, String errmsg) {
                    callback.onFail(code, errmsg);
                }
            });
        }
    }

    public void loadRemoteProfile(final ResponseListener callback) {
        if (!isRegistered()) {
            callback.onFail(-1, "not yet registered");
            return;
        }

        Messenger mgr = ((MessageSensible) this).getMessenger();

        if (mRest != null) {
            HashMap<String, String> headers = new HashMap<>();
            headers.put("authorization", "CERT " + mgr.getCertPass());
            headers.put("x-requested-with", "XMLHttpRequest");
            headers.put("charset", "utf-8");

            mRest.setClientCert(mgr.getCert(), mgr.getCertPass());
            mRest.get("thing/profile", headers, new RestResponseListener() {
                @Override
                public void onResponse(final int status, final byte[] resp) {
                }

                @Override
                public void onError(int status, String errmsg) {
                    switch (status) {
                        case 403:
                            callback.onFail(status, "invalid thing cert");
                            break;
                        case 301:
                            callback.onFail(status, "thing not exists");
                            break;
                        default:
                            if (errmsg == null || errmsg.length() == 0)
                                errmsg = String.valueOf(status);
                            callback.onFail(status, errmsg);
                    }
                }

                @Override
                public void onSuccess(JSONObject resp) {
                    try {
                        configure(resp);
                        callback.onSuccess(resp);
                    } catch (JSONException e) {
                        Log.e("nodewox/thing/configure", e.getMessage());
                        callback.onFail(-1, "cannot configure thing");
                    }
                }

                @Override
                public void onFail(int status, String errmsg) {
                    callback.onFail(status, errmsg);
                }
            });
        }
    }

}
