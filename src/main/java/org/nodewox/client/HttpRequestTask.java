package org.nodewox.client;

import android.os.AsyncTask;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

public class HttpRequestTask extends AsyncTask<String, Integer, Boolean> {

    private final HttpMethod mMethod;
    private HttpURLConnection mConn = null;
    private byte[] mData = null;
    private HttpResponseListener mHttpCallback = null;

    private byte[] mResp = null;
    private int mCode = 0;
    private String mErrMsg = null;

    public HttpRequestTask(HttpMethod method, URL url, byte[] postdata, final HttpResponseListener callback) {
        mMethod = method;
        mData = postdata;
        mHttpCallback = callback;

        try {
            mConn = (HttpURLConnection) url.openConnection();
            mCode = 0;
            mErrMsg = null;
        } catch (IOException e) {
            mConn = null;
            mCode = -1;
            mErrMsg = e.getMessage();
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

    @Override
    protected Boolean doInBackground(String... params) {
        if (mConn != null) {
            mConn.setUseCaches(false);

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

                mCode = mConn.getResponseCode();
                mResp = readContent(mConn);
                return true;

            } catch (ProtocolException e) {
                mResp = null;
                mCode = -1;
                mErrMsg = e.getMessage();
            } catch (IOException e) {
                mResp = null;
                mCode = -1;
                mErrMsg = e.getMessage();
            }
        }

        return false;
    }

    @Override
    public void onPostExecute(Boolean ok) {
        if (mHttpCallback != null) {
            if (ok)
                mHttpCallback.onResponse(mCode, mResp);
            else
                mHttpCallback.onError(mCode, mErrMsg);
        }
    }

    private byte[] readContent(HttpURLConnection conn) throws IOException {
        final int BUF_SIZE = 512;
        InputStream ins = conn.getInputStream();
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        byte[] buf = new byte[BUF_SIZE];
        int count = -1;
        while ((count = ins.read(buf, 0, BUF_SIZE)) != -1)
            os.write(buf, 0, count);

        return os.toByteArray();
    }

    public enum HttpMethod {GET, POST, DELETE, PUT}
}
