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
                boolean defaults = true;
                ByteBuffer buf = null;

                switch (getDataType()) {
                    case RAW:
                        if (data.length > 0) {
                            buf = ByteBuffer.allocate(data.length);
                            for (int i = 0; i < data.length; i++) {
                                Byte b = (Byte) data[i];
                                buf.put(b);
                                if (b != '\0') defaults = false;
                            }
                        }
                        break;
                    case BYTE:
                        buf = ByteBuffer.allocate(n);
                        for (int i = 0; i < n; i++) {
                            Byte b = (Byte) data[i];
                            if (b != '\0') defaults = false;
                            buf.put(b);
                        }
                        break;
                    case INT16:
                        buf = ByteBuffer.allocate(n * 2);
                        for (int i = 0; i < n; i++) {
                            if ((Short) data[i] != 0) defaults = false;
                            buf.putShort((Short) data[i]);
                        }
                        break;
                    case INT32:
                        buf = ByteBuffer.allocate(n * 4);
                        for (int i = 0; i < n; i++) {
                            Integer u = (Integer) data[i];
                            if (u != 0) defaults = false;
                            buf.putInt(u);
                        }
                        break;
                    case INT64:
                        buf = ByteBuffer.allocate(n * 8);
                        for (int i = 0; i < n; i++) {
                            Long u = (Long) data[i];
                            if (u != 0) defaults = false;
                            buf.putLong(u);
                        }
                        break;
                    case FLOAT:
                        buf = ByteBuffer.allocate(n * 4);
                        for (int i = 0; i < n; i++) {
                            Float f = (Float) data[i];
                            if (f != 0) defaults = false;
                            buf.putFloat(f);
                        }
                        break;
                    case BOOL:
                        buf = ByteBuffer.allocate(n);
                        for (int i = 0; i < n; i++) {
                            Boolean b = (Boolean) data[i];
                            if (b) defaults = false;
                            buf.put((byte) (b ? 1 : 0));
                        }
                        break;
                    case STRING:
                        int sz = 0;
                        for (int i = 0; i < n; i++)
                            sz += ((String) data[i]).getBytes().length;

                        if (sz > 0) {
                            defaults = false;
                            sz += n;
                            buf = ByteBuffer.allocate(sz);
                            for (int i = 0; i < n; i++) {
                                byte[] s = ((String) data[i]).getBytes();
                                buf.put(s);
                                buf.put((byte) '\0');
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
