package org.nodewox.client;

import java.nio.ByteBuffer;

public abstract class MaleChannel extends Channel {

    public MaleChannel(Thing thing, String key, DataType dtype, int dim) {
        super(thing, key, Gender.MALE, dtype, dim);
    }

    public MaleChannel(Thing thing, String key, DataType dtype) {
        super(thing, key, Gender.MALE, dtype);
    }

    protected void feedData(final Object[] data) {
        Node root = getApp().getRootNode();

        if (!root.isDisabled() && root instanceof MessageSensible) {
            Messenger messenger = ((MessageSensible) root).getMessenger();
            if (messenger.isConnected()) {
                // encode packet
                int n = Math.min(getDataDim(), data.length);
                int lastidx = -1;
                ByteBuffer buf = null;

                switch (getDataType()) {
                    case RAW:
                        if (data.length > 0) {
                            buf = ByteBuffer.allocate(data.length);
                            for (int i = 0; i < data.length; i++) {
                                Byte b = (Byte) data[i];
                                buf.put(i, b);
                                if (b != '\0') lastidx = i;
                            }
                        }
                        break;
                    case BYTE:
                        buf = ByteBuffer.allocate(n);
                        for (int i = 0; i < n; i++) {
                            Byte b = (Byte) data[i];
                            if (b != '\0') lastidx = i;
                            buf.put(i, b);
                        }
                        break;
                    case INT16:
                        buf = ByteBuffer.allocate(n * 2);
                        for (int i = 0; i < n; i++) {
                            if ((Short) data[i] != 0) lastidx = i;
                            buf.putShort(i * 2, (Short) data[i]);
                        }
                        break;
                    case INT32:
                        buf = ByteBuffer.allocate(n * 4);
                        for (int i = 0; i < n; i++) {
                            Integer u = (Integer) data[i];
                            if (u != 0) lastidx = i;
                            buf.putInt(i * 4, u);
                        }
                        break;
                    case INT64:
                        buf = ByteBuffer.allocate(n * 8);
                        for (int i = 0; i < n; i++) {
                            Long u = (Long) data[i];
                            if (u != 0) lastidx = i;
                            buf.putLong(i * 8, u);
                        }
                        break;
                    case FLOAT:
                        buf = ByteBuffer.allocate(n * 4);
                        for (int i = 0; i < n; i++) {
                            Float f = (Float) data[i];
                            if (f != 0) lastidx = i;
                            buf.putFloat(i * 4, f);
                        }
                        break;
                    case BOOL:
                        buf = ByteBuffer.allocate(n);
                        for (int i = 0; i < n; i++) {
                            Boolean b = (Boolean) data[i];
                            if (b) lastidx = i;
                            buf.put(i, (byte) (b ? 1 : 0));
                        }
                        break;
                    case STRING:
                        int sz = 0;
                        for (int i = 0; i < n; i++) {
                            if (((String) data[i]).length() > 0) {
                                lastidx = i;
                                sz += ((String) data[i]).getBytes().length + 1;
                            }
                        }
                        if (lastidx >= 0) {
                            buf = ByteBuffer.allocate(sz);
                            for (int i = 0; i <= lastidx; i++) {
                                byte[] s = ((String) data[i]).getBytes();
                                buf.put(s);
                                buf.put((byte) '\0');
                            }
                        }
                        break;
                }

                if (lastidx < 0)
                    buf = null;

                messenger.publish(getApp().getTopicPrefix() + getID(), buf == null ? null : buf.array(), 0, false, null);
            }
        }
    }
}
