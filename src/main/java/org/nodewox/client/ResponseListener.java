package org.nodewox.client;

import org.json.JSONObject;

public interface ResponseListener {

    void onSuccess(final JSONObject resp);

    void onFail(final int code, final String errmsg);
}
