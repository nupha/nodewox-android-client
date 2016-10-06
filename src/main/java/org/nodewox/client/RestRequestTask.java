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

        super.setResponseListener(new HttpResponseListener() {
            @Override
            public void onStart() {
                mRestCallback.onStart();
            }

            @Override
            public void onResponse(int code, final HttpURLConnection conn) {
                byte[] data = readContent(conn);
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
            public void onError(int status, String errmsg) {
                mRestCallback.onError(status, errmsg);
            }
        });
    }

    private byte[] readContent(HttpURLConnection conn) {
        final int BUF_SIZE = 512;
        byte[] res;

        try {
            InputStream ins = conn.getInputStream();
            ByteArrayOutputStream os = new ByteArrayOutputStream();

            byte[] buf = new byte[BUF_SIZE];
            int count = -1;
            while ((count = ins.read(buf, 0, BUF_SIZE)) != -1)
                os.write(buf, 0, count);
            res = os.toByteArray();

        } catch (IOException e) {
            Log.e("nodewox/restrequest", e.getMessage());
            res = null;
        }

        return res;
    }
}
