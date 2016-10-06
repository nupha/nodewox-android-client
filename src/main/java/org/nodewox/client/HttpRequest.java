package org.nodewox.client;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;

public class HttpRequest {

    private byte[] mClientCert = null;
    private String mClientCertPass = "";

    public String getBaseUrlString() {
        return "https://www.nodewox.org/api";
    }

    public URL getBaseUrl() throws MalformedURLException {
        return new URL(getBaseUrlString());
    }

    public URL joinUrl(String aurl) throws MalformedURLException {
        return new URL(getBaseUrl(), aurl);
    }

    public int getConnectTimeout() {
        return 10000; // in ms
    }

    // return bytes of trust keystore, null to use default trust
    protected byte[] getCA() {
        return null;
    }

    // password to trust keystore
    protected String getCAPass() {
        return "";
    }

    protected boolean verifyHost(String hostname, SSLSession session) {
        return false;
    }

    public void setClientCert(byte[] cert, String pass) {
        if (cert != null && cert.length > 0) {
            mClientCert = cert;
            mClientCertPass = pass;
        } else {
            mClientCert = null;
            mClientCertPass = "";
        }
    }

    protected HttpRequestTask makeRequestTask(HttpRequestTask.HttpMethod method, URL u, byte[] data,
                                              HttpResponseListener callback) {
        return new HttpRequestTask(method, u, data, callback);
    }

    protected void request(HttpRequestTask.HttpMethod method, String aurl, byte[] data, Map<String, String> headers,
                           final HttpResponseListener callback) {
        URL u;
        try {
            u = joinUrl(aurl);
        } catch (MalformedURLException e) {
            callback.onError(-1, "invalid url");
            return;
        }

        HttpRequestTask req = makeRequestTask(method, u, data, callback);
        req.setConnectTimeout(getConnectTimeout());
        if (headers != null) {
            for (Map.Entry<String, String> item : headers.entrySet()) {
                req.setHeader(item.getKey(), item.getValue());
            }
        }

        if (u.getProtocol().equals("https")) {
            byte[] ca = getCA();
            SSLSocketFactory sslf = Utils.makeSSLSocketFactory(ca, getCAPass(), mClientCert, mClientCertPass);
            if (sslf != null) {
                req.setSSLSocketFactory(sslf);
                if (ca != null && ca.length > 0) {
                    req.setHostnameVerifier(new HostnameVerifier() {
                        public boolean verify(String hostname, SSLSession session) {
                            return verifyHost(hostname, session);
                        }
                    });
                }
            }
        }

        req.execute();
    }

    public void post(String url, byte[] data, Map<String, String> headers, final HttpResponseListener callback) {
        request(HttpRequestTask.HttpMethod.POST, url, data, headers, callback);
    }

    public void post(String url, byte[] data, final HttpResponseListener callback) {
        request(HttpRequestTask.HttpMethod.POST, url, data, null, callback);
    }

    public void get(String url, Map<String, String> headers, final HttpResponseListener callback) {
        request(HttpRequestTask.HttpMethod.GET, url, null, headers, callback);
    }

    public void get(String url, final RestResponseListener callback) {
        request(HttpRequestTask.HttpMethod.GET, url, null, null, callback);
    }

    public void delete(String url, byte[] data, Map<String, String> headers, final HttpResponseListener callback) {
        request(HttpRequestTask.HttpMethod.DELETE, url, data, headers, callback);
    }

    public void delete(String url, byte[] data, final HttpResponseListener callback) {
        request(HttpRequestTask.HttpMethod.DELETE, url, data, null, callback);
    }

}
