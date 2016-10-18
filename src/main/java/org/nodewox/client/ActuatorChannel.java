package org.nodewox.client;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

public abstract class ActuatorChannel extends Channel {

    public ActuatorChannel(Thing thing, String key, DataType dtype, int dim) {
        super(thing, key, FlowDir.IN, dtype, dim);
    }

    public ActuatorChannel(Thing thing, String key, DataType dtype) {
        super(thing, key, FlowDir.IN, dtype);
    }

    protected abstract void perform(int src, int gid, Object[] data);

    public void handlePacket(final byte[] payload) {
        // decode packet
        ByteBuffer bf = ByteBuffer.wrap(payload);

        int src = bf.getInt();
        int gid = bf.getInt();

        final int n = getDataDim();
        Object[] arr = new Object[n];

        switch (getDataType()) {
            case BYTE:
                for (int i = 0; i < n; i++) {
                    try {
                        arr[i] = bf.get();
                    } catch (BufferUnderflowException e) {
                        arr[i] = '\0';
                    }
                }
                break;

            case INT32:
                for (int i = 0; i < n; i++) {
                    try {
                        arr[i] = bf.getInt();
                    } catch (BufferUnderflowException e) {
                        arr[i] = Integer.valueOf(0);
                    }
                }
                break;

            case INT16:
                for (int i = 0; i < n; i++) {
                    try {
                        arr[i] = bf.getShort();
                    } catch (BufferUnderflowException e) {
                        arr[i] = Short.valueOf((short) 0);
                    }
                }
                break;

            case INT64:
                for (int i = 0; i < n; i++) {
                    try {
                        arr[i] = bf.getLong();
                    } catch (BufferUnderflowException e) {
                        arr[i] = 0L;
                    }
                }
                break;

            case STRING:
                for (int i = 0; i < n; i++) {
                    if (bf.get() == '\0') {
                        arr[i] = "";
                    } else {
                        try {
                            int start = bf.position();
                            int cnt = 0;
                            while (bf.get() != '\0') {
                                cnt++;
                            }
                            byte[] ss = new byte[cnt];
                            bf.get(ss, start, cnt);
                            arr[i] = new String(ss);
                        } catch (BufferUnderflowException e) {
                            arr[i] = "";
                        }
                    }
                }
                break;

            case FLOAT:
                for (int i = 0; i < n; i++) {
                    try {
                        arr[i] = bf.getFloat();
                    } catch (BufferUnderflowException e) {
                        arr[i] = Float.valueOf(0);
                    }
                }
                break;

            case BOOL:
                for (int i = 0; i < n; i++) {
                    try {
                        arr[i] = bf.get() != 0;
                    } catch (BufferUnderflowException e) {
                        arr[i] = false;
                    }
                }
                break;

            case RAW:
                int p = bf.position();
                byte[] buf = new byte[payload.length - p];
                bf.get(buf, p, payload.length - p);
                arr = new Byte[buf.length];
                for (int i = 0; i < arr.length; i++)
                    arr[i] = buf[i];
                break;
        }

        perform(src, gid, arr);
    }

}
