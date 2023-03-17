package me.laiketong.telepathy.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.WritableByteChannel;
import java.util.LinkedList;
import java.util.Queue;

public abstract class MiddleWareComponent implements ChannelMiddleWareInterface {

    private final ByteBuffer buffer;
    private final Queue<OPSRecord> records;
    private final Object recordsLock;


    public MiddleWareComponent() {
        this.buffer = ByteBuffer.allocateDirect(2048);
        this.records = new LinkedList<>();
        this.recordsLock = new Object();
        new Thread(this::maintainKeys).start();
    }

    protected int swapData(WritableByteChannel accept, ReadableByteChannel send) throws IOException {

        int sum = 0;
        while (send.read(buffer) > 0) {
            buffer.flip();
            sum += accept.write(buffer);
            buffer.clear();
        }
        return sum;

    }

    protected void setOPS(SelectionKey key, int ops) {
        synchronized (records) {
            synchronized (recordsLock) {
                records.add(new OPSRecord(key, ops));
                recordsLock.notify();
            }
        }
    }

    private void maintainKeys() {
        while (true) {
            synchronized (recordsLock) {
                if (records.isEmpty()) {
                    try {
                        recordsLock.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }

            while (!records.isEmpty()) {
                OPSRecord record;
                synchronized (records) {
                    record = records.remove();
                }
                SelectionKey key = record.getKey();
                synchronized (key) {
                    if (!key.isValid()) {
                        continue;
                    }
                    if (record.getOps() == -1) {
                        try {
                            key.channel().close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        key.cancel();
                    } else {
                        key.interestOps(record.getOps());
                        System.out.println("设置成功");
                    }
                }
            }
        }
    }


    private class OPSRecord {
        SelectionKey key;
        int ops;

        public SelectionKey getKey() {
            return key;
        }

        public int getOps() {
            return ops;
        }

        public OPSRecord(SelectionKey key, Integer ops) {
            this.key = key;
            this.ops = ops;
        }
    }


}
