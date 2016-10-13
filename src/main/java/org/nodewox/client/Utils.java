package org.nodewox.client;

import android.util.Log;

import com.google.protobuf.ByteString;

import junit.framework.Assert;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Date;
import java.util.List;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

public class Utils {

    //
    // make ssl socket context
    //
    public static SSLSocketFactory makeSSLSocketFactory(byte[] ca, String capass, byte[] cert_p12, String certpass) {
        // trust CA, set ca==null to use build in CA
        TrustManager[] cam = null;
        if (ca != null && ca.length > 0) {
            KeyStore kstore;
            try {
                kstore = KeyStore.getInstance("BKS");
                kstore.load((new ByteArrayInputStream(ca)), capass.toCharArray());
            } catch (KeyStoreException e) {
                Log.e("nodewox", e.getMessage());
                return null;
            } catch (IOException e) {
                Log.e("nodewox", e.getMessage());
                return null;
            } catch (NoSuchAlgorithmException e) {
                Log.e("nodewox", e.getMessage());
                return null;
            } catch (CertificateException e) {
                Log.e("nodewox", e.getMessage());
                return null;
            }

            try {
                TrustManagerFactory tmf = TrustManagerFactory.getInstance("X509");
                tmf.init(kstore);
                cam = tmf.getTrustManagers();
            } catch (NoSuchAlgorithmException e) {
                Log.e("nodewox", e.getMessage());
                return null;
            } catch (KeyStoreException e) {
                Log.e("nodewox", e.getMessage());
                return null;
            }
        }

        // Client cert
        KeyManager[] keys = null;
        if (cert_p12 != null && cert_p12.length > 0) {
            KeyStore cli_store;
            try {
                cli_store = KeyStore.getInstance("PKCS12");
                cli_store.load(new ByteArrayInputStream(cert_p12), certpass.toCharArray());
            } catch (KeyStoreException e) {
                Log.e("nodewox", e.getMessage());
                return null;
            } catch (IOException e) {
                Log.e("nodewox", e.getMessage());
                return null;
            } catch (NoSuchAlgorithmException e) {
                Log.e("nodewox", e.getMessage());
                return null;
            } catch (CertificateException e) {
                Log.e("nodewox", e.getMessage());
                return null;
            }

            try {
                KeyManagerFactory kmf = KeyManagerFactory.getInstance("X509");
                kmf.init(cli_store, null);
                keys = kmf.getKeyManagers();
            } catch (UnrecoverableKeyException e) {
                Log.e("nodewox", e.getMessage());
                return null;
            } catch (NoSuchAlgorithmException e) {
                Log.e("nodewox", e.getMessage());
                return null;
            } catch (KeyStoreException e) {
                Log.e("nodewox", e.getMessage());
                return null;
            }
        }

        // make ssl context
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(keys, cam, new SecureRandom());
            return ctx.getSocketFactory();

        } catch (NoSuchAlgorithmException e) {
            Log.e("nodewox", e.getMessage());
        } catch (KeyManagementException e) {
            Log.e("nodewox", e.getMessage());
        }

        return null;
    }


    // make Variant
    public static NodeTalk.Variant makeVariant(Object v) {
        NodeTalk.Variant.Builder b = NodeTalk.Variant.newBuilder();

        if (v == null)
            b.setBinVal(null);
        else if (v instanceof Integer)
            b.setIntVal((Integer) v);
        else if (v instanceof Long || v instanceof Integer)
            b.setIntVal((Long) v);
        else if (v instanceof Boolean)
            b.setBoolVal((Boolean) v);
        else if (v instanceof String)
            b.setStrVal((String) v);
        else if (v instanceof Float)
            b.setNumVal((Float) v);
        else if (v instanceof Double)
            b.setNumVal((Double) v);
        else if (v instanceof Date)
            b.setTimeVal(((Date) v).getTime());
        else if (BuildConfig.DEBUG)
            throw new AssertionError("object can't convert to variant: " + v + ", class=" + v.getClass());

        return b.build();
    }

    public static NodeTalk.Variant makeVariant(byte[] bin) {
        NodeTalk.Variant.Builder b = NodeTalk.Variant.newBuilder();
        if (bin != null && bin.length > 0)
            b.setBinVal(ByteString.copyFrom(bin));
        else
            b.setBinVal(null);
        return b.build();
    }

    public static Object getValueFromVariant(NodeTalk.Variant va) {
        Object v = null;
        switch (va.getValueCase()) {
            case STR_VAL:
                v = va.getStrVal();
                break;
            case INT_VAL:
                v = new Long(va.getIntVal());
                break;
            case NUM_VAL:
                v = new Double(va.getNumVal());
                break;
            case BOOL_VAL:
                v = new Boolean(va.getBoolVal());
                break;
            case TIME_VAL:
                v = new Date(va.getTimeVal());
                break;
            case BIN_VAL:
                if (va.getBinVal().isEmpty())
                    v = null;
                else
                    v = va.getBinVal();
                break;
        }
        return v;
    }

}
