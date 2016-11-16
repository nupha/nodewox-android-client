package org.nodewox.client;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public abstract class Node {

    // params of node
    private final HashMap<String, NodeParam> mParams = new HashMap<>();
    private final NxApplication mApp;

    // map of {key:child-node}
    private final HashMap<String, Node> children = new HashMap<>();

    private final Node mParent;

    protected String mName = "";
    protected String mComment = "";
    protected RestRequest mRest = null;
    private String mKey = null;
    private int mID = 0;
    private int mSeq = 0;

    public Node(NxApplication app, String key, Node parent) {
        mApp = app;
        mKey = key;
        mParent = parent;
    }

    public Node(NxApplication app, String key) {
        mApp = app;
        mKey = key;
        mParent = null;
    }

    // called after param value changed
    protected abstract void onParamChanged(final NodeParam param);

    public NxApplication getApp() {
        return mApp;
    }

    public Node getParent() {
        return mParent;
    }

    public Node getChild(int code) {
        for (Map.Entry<String, Node> pair : children.entrySet()) {
            if (pair.getValue().getID() == code)
                return pair.getValue();
        }
        return null;
    }

    public Node getChild(String key) {
        if (children.containsKey(key))
            return children.get(key);
        else
            return null;
    }

    public void addChild(Node node) {
        assert (node.getParent() == this) : "parent don't match";
        if (node.getParent() == this) {
            assert (node.getKey() != null) : "key!=null for children node";
            children.put(node.getKey(), node);
        }
    }

    public Collection<Node> getChildren() {
        return children.values();
    }

    public String getKey() {
        return mKey;
    }

    public void setKey(String v) {
        assert (mKey == null);
        mKey = v;
    }

    public int getID() {
        return mID;
    }

    public void setID(int i) {
        if (i > 0)
            mID = i;
    }

    public String getName() {
        return mName;
    }

    public void setName(String v) {
        mName = v;
    }

    public int getSeq() {
        return mSeq;
    }

    public void setSeq(int v) {
        mSeq = v;
    }

    public boolean isDisabled() {
        return mID <= 0;
    }

    public <T extends Object> NodeParam<T> addParam(String key, String name, T val) {
        NodeParam<T> p = new NodeParam<>(this, key, name, val);
        mParams.put(key, p);
        p.setSeq(mParams.size());
        return p;
    }

    public <T extends Object> NodeParam<T> addParam(String key, T val) {
        return addParam(key, "", val);
    }

    public <T extends Object> NodeParam<T> addStaticParam(String key, String name, T val) {
        NodeParam<T> p = addParam(key, name, val);
        p.setFlag(NodeParam.ParamFlag.STATIC);
        return p;
    }

    public NodeParam getParam(String key) {
        if (mParams.containsKey(key))
            return mParams.get(key);
        else
            return null;
    }

    public <T> boolean setParam(String key, T val) {
        if (mParams.containsKey(key)) {
            NodeParam<T> p = mParams.get(key);
            if (p != null && p.setValue(val))
                return true;
        }
        return false;
    }

    // config node from JSON
    protected void configure(JSONObject data) throws JSONException {
        if (data.has("id")) {
            setID(data.getInt("id"));
        }

        // params
        if (data.has("params")) {
            JSONObject params = data.getJSONObject("params");
            Iterator<String> it = params.keys();
            while (it.hasNext()) {
                String key = it.next();
                if (mParams.containsKey(key))
                    mParams.get(key).configure(params.getJSONObject(key));
            }
        }

        // configure children nodes
        if (data.has("children")) {
            JSONObject chans = data.getJSONObject("children");
            Iterator<String> it = chans.keys();
            while (it.hasNext()) {
                String key = it.next();
                Node ch = getChild(key);
                if (ch != null)
                    ch.configure(chans.getJSONObject(key));
            }
        }
    }

    public JSONObject asJSON() throws JSONException {
        JSONObject res = new JSONObject();
        res.put("key", mKey);

        if (mName.length() > 0)
            res.put("name", mName);

        if (mComment.length() > 0)
            res.put("comment", mComment);

        if (mSeq > 0)
            res.put("seq", mSeq);

        // params
        if (!mParams.isEmpty()) {
            JSONObject params = new JSONObject();
            for (Map.Entry<String, NodeParam> item : mParams.entrySet()) {
                params.put(item.getKey(), item.getValue().asJSON());
            }
            res.put("params", params);
        }

        // children
        if (!children.isEmpty()) {
            JSONObject chs = new JSONObject();
            for (Map.Entry<String, Node> item : children.entrySet()) {
                chs.put(item.getKey(), item.getValue().asJSON());
            }
            res.put("children", chs);
        }

        return res;
    }

    protected void reset() {
        mID = 0;
    }

    public RestRequest getRestRequest() {
        return mRest;
    }

    public void setRestRequest(RestRequest rest) {
        mRest = rest;
    }

    public Map<String, JSONObject> handleRequest(String action, Map<String, Object> params, int[] children) {
        boolean report_params = action.equals("status");

        if (params != null && !params.isEmpty()) {
            // set params
            for (Map.Entry<String, Object> kv : params.entrySet()) {
                NodeParam p = getParam(kv.getKey());
                if (p != null) {
                    if (p.setValue(kv.getValue()))
                        report_params = true;
                }
            }
        }

        JSONObject resp = new JSONObject();
        if (report_params && !mParams.isEmpty()) {
            try {
                JSONObject pp = new JSONObject();
                int cnt = 0;
                for (NodeParam p : mParams.values()) {
                    if (p.getFlag() != NodeParam.ParamFlag.STATIC) {
                        pp.put(p.getKey(), p.getValue());
                        cnt++;
                    }
                }
                if (cnt > 0)
                    resp.put("params", pp);
            } catch (JSONException e) {
            }
        }

        Map<String, JSONObject> res = new HashMap<>();
        res.put(mApp.getTopicPrefix() + getID() + "/r", resp);

        if (children != null && children.length > 0) {
            // request into children
            for (int nid : children) {
                Node n = getChild(nid);
                if (n != null) {
                    Map<String, JSONObject> m2 = n.handleRequest(action, null, null);
                    if (m2 != null)
                        res.putAll(m2);
                }
            }
        }

        return res;
    }

    // handle request message
    public Map<String, JSONObject> handleRequest(JSONObject req) {
        String action = "";
        if (!req.isNull("action")) {
            try {
                action = req.getString("action");
            } catch (JSONException e) {
                action = "";
            }
        }

        int[] children = null;
        if (!req.isNull("children")) {
            try {
                JSONArray arr = req.getJSONArray("children");
                children = new int[arr.length()];
                for (int i = 0; i < arr.length(); i++)
                    children[i] = arr.getInt(i);
            } catch (JSONException e) {
                children = null;
            }
        }

        Map<String, Object> params = new HashMap<>();
        if (!req.isNull("params")) {
            try {
                JSONObject obj = req.getJSONObject("params");
                Iterator<String> keys = obj.keys();
                while (keys.hasNext()) {
                    String k = keys.next();
                    NodeParam p = getParam(k);
                    if (p != null) {
                        switch (p.getType()) {
                            case INT:
                                params.put(k, obj.getInt(k));
                                break;
                            case FLOAT:
                                params.put(k, (float) obj.getDouble(k));
                                break;
                            case STRING:
                                params.put(k, obj.getString(k));
                                break;
                            case BOOL:
                                params.put(k, obj.getBoolean(k));
                                break;
                        }
                    }
                }
            } catch (JSONException e) {
                params.clear();
            }
        }

        return handleRequest(action, params, children);
    }

}
