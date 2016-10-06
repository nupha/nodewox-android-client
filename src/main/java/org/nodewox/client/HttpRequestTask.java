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

public class HttpRequestTask extends AsyncTask<String, Integer, Boolean> {

    private final HttpMethod mMethod;
    private HttpURLConnection mConn;
    private byte[] mData = null;
    private HttpResponseListener mHttpCallback = null;

    public HttpRequestTask(HttpMethod method, URL url, byte[] postdata, final HttpResponseListener callback) {
        mMethod = method;
        mData = postdata;
        mHttpCallback = callback;

        try {
            mConn = (HttpURLConnection) url.openConnection();
            mConn.setUseCaches(false);
        } catch (IOException e) {
            mConn = null;
            mHttpCallback.onError(-1, "IO Error");
        }
    }

    protected void setResponseListener(HttpResponseListener cb) {
        mHttpCallback = cb;
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
        if (mConn == null) {
            mHttpCallback.onError(-1, "not connected");
            return false;
        }

        mHttpCallback.onStart();

        if (mData != null) {
            mConn.setRequestProperty("content-length", String.valueOf(mData.length));
            mConn.setDoOutput(true);
        }

        try {
            switch (mMethod) {
                case GET:
                    mConn.setRequestMethod("GET");
                    break;
                case POST:
                    mConn.setRequestMethod("POST");
                    break;
                case PUT:
                    mConn.setRequestMethod("PUT");
                    break;
                case DELETE:
                    mConn.setRequestMethod("DELETE");
                    break;
            }

            if (mData != null) {
                OutputStream outs = mConn.getOutputStream();
                outs.write(mData);
            }

            if (mHttpCallback != null)
                mHttpCallback.onResponse(mConn.getResponseCode(), mConn);

            return true;

        } catch (ProtocolException e) {
            Log.e("nodewox/httprequst", e.getMessage());
            if (mHttpCallback != null)
                mHttpCallback.onError(-1, e.getMessage());
        } catch (IOException e) {
            Log.e("nodewox/httprequst", e.getMessage());
            if (mHttpCallback != null)
                mHttpCallback.onError(-1, e.getMessage());
        }

        return false;
    }

    public enum HttpMethod {GET, POST, DELETE, PUT}
}
