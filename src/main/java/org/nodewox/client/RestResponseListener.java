package org.nodewox.client;

import org.json.JSONObject;

public interface RestResponseListener extends HttpResponseListener {

    void onSuccess(JSONObject resp);

    void onFail(int code, String errmsg);
}
