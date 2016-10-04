package org.nodewox.client;

import org.json.JSONObject;

public interface HttpRequestListener {

    void onStart();

    void onSuccess(JSONObject resp);

    void onFail(String errmsg);

    void onError(int status, String errmsg);
}
