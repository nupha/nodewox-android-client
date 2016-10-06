package org.nodewox.client;

import org.json.JSONObject;

public interface RestResponseListener extends HttpResponseListener {

    void onSuccess(final JSONObject resp);

    void onFail(final int errcode, final String errmsg);
}
