package org.nodewox.client;

import java.net.HttpURLConnection;

public interface HttpResponseListener {

    void onResponse(final int status, final byte[] resp);

    void onError(final int errcode, final String errmsg);
}
