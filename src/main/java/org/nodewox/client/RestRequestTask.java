package org.nodewox.client;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;

public class RestRequestTask extends HttpRequestTask {

    private RestResponseListener mRestCallback;

    public RestRequestTask(HttpMethod method, URL url, byte[] postdata, final RestResponseListener callback) {
        super(method, url, postdata, null);
        mRestCallback = callback;

        // NOTE: following handler runs in main thread
        super.setResponseListener(new HttpResponseListener() {
            @Override
            public void onResponse(final int code, final byte[] data) {
                if (data == null || data.length == 0) {
                    mRestCallback.onError(code, "response contains no data");
                    return;
                }

                try {
                    String content = new String(data, "utf-8");
                    switch (code) {
                        case 200:
                            JSONObject res = new JSONObject(content);
                            int status = res.optInt("status", -1);
                            if (status == 0)
                                mRestCallback.onSuccess(res.optJSONObject("response"));
                            else
                                mRestCallback.onFail(status, res.optString("response", "error"));
                            break;
                        case 501:
                            mRestCallback.onError(501, content);
                            break;
                        default:
                            mRestCallback.onError(code, content);
                    }
                } catch (UnsupportedEncodingException e) {
                    mRestCallback.onError(-1, e.getMessage());
                } catch (JSONException e) {
                    mRestCallback.onError(-1, e.getMessage());
                }
            }

            @Override
            public void onError(final int status, final String errmsg) {
                mRestCallback.onError(status, errmsg);
            }
        });
    }

}
