package org.nodewox.client;

import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

public class HttpRequest extends AsyncTask<String, Integer, Boolean> {

    private final HttpRequestListener mCallback;
    private final HttpMethod mMethod;
    private HttpURLConnection mConn;
    private byte[] mPostData = null;

    public HttpRequest(HttpMethod method, URL url, byte[] postdata, final HttpRequestListener callback) {
        mMethod = method;
        mPostData = postdata;
        mCallback = callback;

        try {
            mConn = (HttpURLConnection) url.openConnection();
            mConn.setUseCaches(false);
        } catch (IOException e) {
            mConn = null;
            mCallback.onFail("IO Error");
        }
    }

    public void setConnectTimeout(int to) {
        if (mConn != null)
            mConn.setConnectTimeout(to);
    }

    public void setSSLSocketFactory(SSLSocketFactory fa) {
        if (mConn != null)
            ((HttpsURLConnection) mConn).setSSLSocketFactory(fa);
    }

    public void setHostnameVerifier(HostnameVerifier v) {
        if (mConn != null)
            ((HttpsURLConnection) mConn).setHostnameVerifier(v);
    }

    public void setHeader(String key, String val) {
        if (mConn != null)
            mConn.setRequestProperty(key, val);
    }

    private JSONObject processResponse(HttpURLConnection conn) {
        JSONObject res = null;
        try {
            InputStream ins = conn.getInputStream();
            ByteArrayOutputStream os = new ByteArrayOutputStream();

            byte[] buf = new byte[1024];
            int count = -1;
            while ((count = ins.read(buf, 0, 1024)) != -1)
                os.write(buf, 0, count);

            String content = new String(os.toByteArray(), "utf-8");
            res = new JSONObject(content);

        } catch (JSONException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return res;
    }

    @Override
    protected Boolean doInBackground(String... params) {
        if (mConn != null) {
            mCallback.onStart();

            try {
                if (mMethod == HttpMethod.POST) {
                    if (mPostData != null) {
                        mConn.setRequestProperty("content-length", String.valueOf(mPostData.length));
                        mConn.setDoOutput(true);
                    }

                    mConn.setRequestMethod("POST");
                    if (mPostData != null) {
                        OutputStream outs = mConn.getOutputStream();
                        outs.write(mPostData);
                    }
                } else
                    mConn.setRequestMethod("GET");

                switch (mConn.getResponseCode()) {
                    case 200:
                        JSONObject res = processResponse(mConn);
                        if (res.optInt("status", -1) == 0) {
                            mCallback.onSuccess(res.optJSONObject("response"));
                            return true;
                        } else
                            mCallback.onFail(res.optString("response", "error"));
                        break;
                    case 501:
                        mCallback.onError(501, "service not implemented");
                        break;
                    default:
                        mCallback.onError(mConn.getResponseCode(), "Error code: " + mConn.getResponseCode());
                }

            } catch (ProtocolException e) {
                mCallback.onError(-1, "Protocol error");
            } catch (IOException e) {
                mCallback.onError(-1, "IO error");
                Log.e("PaaN", e.getMessage());
            }
        }

        return false;
    }

    public enum HttpMethod {GET, POST}

}
