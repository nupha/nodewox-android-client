package org.nodewox.client;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static org.nodewox.client.Node.NodeType.UNKNOWN;

public abstract class Node {

    // params of node
    protected final Map<String, NodeParam> mParams = new HashMap<>();
    private final NxApplication mApp;
    // map of {key:child-node}
    private final HashMap<String, Node> children = new HashMap<>();

    private final Node mParent;

    protected NodeType mNodeType = UNKNOWN;

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

    // handle incomming packet
    public abstract Map<String, NodeTalk.Response> handlePacket(NodeTalk.Packet packet);

    public NxApplication getApp() {
        return mApp;
    }

    public NodeType getNodeType() {
        return mNodeType;
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

    public <T extends Object> NodeParam<T> addParam(String key, String name, T val) {
        NodeParam<T> p = new NodeParam<>(this, key, name, val);
        p.setSeq(mParams.size());
        mParams.put(key, p);
        return p;
    }

    public <T extends Object> NodeParam<T> addParam(String key, T val) {
        NodeParam<T> p = new NodeParam<>(this, key, "", val);
        p.setSeq(mParams.size());
        mParams.put(key, p);
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
                Log.v("nodewox", key + ", " + chans.getJSONObject(key));
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
        mKey = null;
    }

    public RestRequest getRestRequest() {
        return mRest;
    }

    public void setRestRequest(RestRequest rest) {
        mRest = rest;
    }

    // handle request message
    public Map<String, NodeTalk.Response> handleRequest(NodeTalk.Request msg) {
        if (BuildConfig.DEBUG && msg == null)
            throw new AssertionError("request message must not be null for handleRequest()");

        Map<String, NodeTalk.Response> res = new HashMap<>();
        NodeTalk.Response.AckType acktype = null;
        boolean check_param = false;

        switch (msg.getAction()) {
            case ACTION_CHECK_ALIVE:
                acktype = NodeTalk.Response.AckType.ACK_OK;
                break;

            case ACTION_CHECK_PARAM:
                if (!mParams.isEmpty()) {
                    acktype = NodeTalk.Response.AckType.ACK_OK;
                    check_param = true;
                }
                break;

            case ACTION_CHECK_PARAM_ALIVE:
                acktype = NodeTalk.Response.AckType.ACK_OK;
                if (!mParams.isEmpty())
                    check_param = true;
                break;

            case ACTION_SET_PARAM:
                if (msg.getParamsCount() > 0) {
                    for (Map.Entry<String, NodeTalk.Variant> pair : msg.getParamsMap().entrySet()) {
                        NodeParam p = getParam(pair.getKey());
                        if (p != null) {
                            Object v = Utils.getValueFromVariant(pair.getValue());
                            if (v != null) {
                                if (p.setValue(v)) {
                                    acktype = NodeTalk.Response.AckType.ACK_OK;
                                    check_param = true;
                                }
                            }
                        }
                    }
                }
                break;
        }

        if (acktype != null) {
            NodeTalk.Response.Builder b = NodeTalk.Response.newBuilder();
            b.setAcktype(acktype);

            if (check_param && !mParams.isEmpty()) {
                for (Map.Entry<String, NodeParam> pair : mParams.entrySet())
                    b.putParams(pair.getKey(), Utils.makeVariant(pair.getValue().getValue()));
            }

            res.put("/NX/" + getID() + "/r", b.build());
        }

        // request to children
        if (msg.getChildrenCount() > 0) {
            for (Integer nid : msg.getChildrenList()) {
                Node n = getChild(nid);
                if (n != null) {
                    Map<String, NodeTalk.Response> res2 = n.handleRequest(msg);
                    if (res2 != null)
                        res.putAll(res2);
                }
            }
        }

        return res;
    }

    public enum NodeType {THING, CHANNEL, USER, UNKNOWN}

    public enum DataType {ANY, INT, FLOAT, STRING, BOOL, BIN}

}
