package org.nodewox.client;

import org.json.JSONObject;

import java.net.HttpURLConnection;

public interface HttpResponseListener {

    void onStart();

    void onResponse(final int status, final HttpURLConnection conn);

    void onError(final int status, final String errmsg);
}
