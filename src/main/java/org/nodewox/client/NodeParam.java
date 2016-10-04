package org.nodewox.client;


public class NodeParam {

    public String name;
    public Channel.DataType datatype;
    public String comment;
    public boolean persistent = false;
    public boolean writable = true;
    public int seq = 0;

    public int iVal;
    public double fVal;
    public String sVal;
    public boolean bVal;

    public NodeParam(String name_, Channel.DataType datatype_, String comment_, boolean persistent_) {
        name = name_;
        datatype = datatype_;
        comment = comment_;
        persistent = persistent_;
        writable = true;
    }
}
