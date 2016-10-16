package org.nodewox.client;

import java.nio.ByteBuffer;

public abstract class SourceChannel extends Channel {

    public SourceChannel(Thing thing, String key, DataType dtype, int dim) {
        super(thing, key, FlowDir.OUT, dtype, dim);
    }

    public SourceChannel(Thing thing, String key, DataType dtype) {
        super(thing, key, FlowDir.OUT, dtype);
    }

    protected void feedData(final Object[] data) {
        Node root = getApp().getRootNode();

        if (!root.isDisabled() && root instanceof MessageSensible) {
            Messenger messenger = ((MessageSensible) root).getMessenger();
            if (messenger.isConnected()) {
                // encode packet
                int n = getDataDim();
                boolean defaults = true;
                ByteBuffer buf = null;

                switch (getDataType()) {
                    case RAW:
                        if (data.length > 0) {
                            buf = ByteBuffer.allocate(data.length);
                            for (int i = 0; i < data.length; i++) {
                                if (i < data.length) {
                                    Byte b = (Byte) data[i];
                                    if (b != 0b0) defaults = false;
                                    buf.put(b);
                                } else
                                    buf.put((byte) '\0');
                            }
                        }
                        break;
                    case BYTE:
                        buf = ByteBuffer.allocate(n);
                        for (int i = 0; i < n; i++) {
                            if (i < data.length) {
                                Byte b = (Byte) data[i];
                                if (b != 0b0) defaults = false;
                                buf.put(b);
                            } else
                                buf.put((byte) 0);
                        }
                        break;
                    case SHORT:
                        buf = ByteBuffer.allocate(n * 2);
                        for (int i = 0; i < n; i++) {
                            if (i < data.length) {
                                if ((Short) data[i] != 0) defaults = false;
                                buf.putShort((Short) data[i]);
                            } else
                                buf.putShort((short) 0);
                        }
                        break;
                    case INT:
                        buf = ByteBuffer.allocate(n * 4);
                        for (int i = 0; i < n; i++) {
                            if (i < data.length) {
                                Integer u = (Integer) data[i];
                                if (u != 0) defaults = false;
                                buf.putInt(u);
                            } else
                                buf.putInt(0);
                        }
                        break;
                    case LONG:
                        buf = ByteBuffer.allocate(n * 8);
                        for (int i = 0; i < n; i++) {
                            if (i < data.length) {
                                Long u = (Long) data[i];
                                if (u != 0) defaults = false;
                                buf.putLong(u);
                            } else
                                buf.putLong(0L);
                        }
                        break;
                    case FLOAT:
                        buf = ByteBuffer.allocate(n * 4);
                        for (int i = 0; i < n; i++) {
                            if (i < data.length) {
                                Float f = (Float) data[i];
                                if (f != 0) defaults = false;
                                buf.putFloat(f);
                            } else
                                buf.putFloat(0);
                        }
                        break;
                    case BOOL:
                        buf = ByteBuffer.allocate(n);
                        for (int i = 0; i < n; i++) {
                            if (i < data.length) {
                                Boolean b = (Boolean) data[i];
                                if (b) defaults = false;
                                buf.put((byte) (b ? 1 : 0));
                            } else
                                buf.put((byte) 0);
                        }
                        break;
                    case STRING:
                        int sz = 0;
                        for (int i = 0; i < n && i < data.length; i++)
                            sz += ((String) data[i]).getBytes().length;

                        if (sz > 0) {
                            defaults = false;
                            sz += n;
                            buf = ByteBuffer.allocate(sz);
                            for (int i = 0; i < n; i++) {
                                if (i < data.length) {
                                    byte[] s = ((String) data[i]).getBytes();
                                    buf.put(s);
                                    buf.put((byte) 0);
                                } else
                                    buf.put((byte) 0);
                            }
                        }
                        break;
                }

                if (defaults)
                    buf = null;

                messenger.publish("/NX/" + getID(), buf == null ? null : buf.array(), 0, false, null);
            }
        }
    }
}
