package org.nodewox.client;

import android.util.Log;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;

public class RestRequest extends HttpRequest {

    @Override
    protected HttpRequestTask makeRequestTask(HttpRequestTask.HttpMethod method, URL u, byte[] data,
                                              HttpResponseListener callback) {
        if ((callback instanceof RestResponseListener)) {
            return new RestRequestTask(method, u, data, (RestResponseListener) callback);
        } else {
            Log.e("nodewox/restrequest", "a RestResponseListener is expected");
            return null;
        }
    }

}
