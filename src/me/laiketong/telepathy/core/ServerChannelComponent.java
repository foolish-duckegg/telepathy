package me.laiketong.telepathy.core;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

public class ServerChannelComponent extends ChannelComponentImp implements Runnable {

    int port;
    final Object keysLock;
    final ServerSocketChannel serverSocketChannel;
    final Queue<SelectionKey> keys;
    final Object recordsLock;
    final Queue<OPSRecord> records;
    final Selector selector;
    ByteBuffer buffer;

    public ServerChannelComponent(int port) throws IOException {
        this.port = port;

        keys = new LinkedList<>();

        records = new LinkedList<>();

        keysLock = new Object();

        recordsLock = new Object();

        serverSocketChannel = ServerSocketChannel.open();

        serverSocketChannel.configureBlocking(false);

        serverSocketChannel.bind(new InetSocketAddress(port));

        selector = Selector.open();

        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        buffer = ByteBuffer.wrap("#RESET".getBytes());

        new Thread(this).start();

        new Thread(() -> {
        }).start();
    }

    @Override
    public SelectionKey getSelectionKey() throws InterruptedException {
        synchronized (keysLock) {
            if (keys.isEmpty()) keysLock.wait();
        }
        synchronized (keys) {
            return keys.remove();
        }
    }

    @Override
    public Socket getSocket() throws InterruptedException {
        return null;
    }

    @Override
    public SocketChannel getSocketChannel() throws InterruptedException {
        return null;
    }

    @Override
    public SelectionKey getReadableKey() throws InterruptedException {
        return null;
    }

    @Override
    public int getPreparedNums() {
        return 0;
    }

    public boolean putSelectionKey(SelectionKey key) {
        if (key == null) return false;
        synchronized (keys) {
            keys.add(key);
        }
        synchronized (keysLock) {
            keysLock.notify();
        }
        return true;
    }

    @Override
    public void run() {
        while (true) {
            int selected = 0;
            try {
                selected = selector.select(2);
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (selected > 0) {
                Iterator iterator = selector.selectedKeys().iterator();
                while (iterator.hasNext()) {
                    SelectionKey key = (SelectionKey) iterator.next();
                    synchronized (key) {
                        if (key.isAcceptable() && key.isValid()) {
                            try {
                                ((ServerSocketChannel) key.channel()).accept().configureBlocking(false).register(selector, SelectionKey.OP_READ);
                            } catch (IOException e) {
                                try {
                                    key.channel().close();
                                } catch (IOException ioException) {
                                    ioException.printStackTrace();
                                }
                                key.cancel();
                                e.printStackTrace(); //连接中断意外
                            }
                        }

                        if (key.isReadable() && key.isValid()) {
                            if (key.attachment() != null) {
                                System.out.println(key.isValid());
                                setOPS((SelectionKey) key.attachment(), SelectionKey.OP_WRITE);
                                System.out.println(port + " 可读，为对方注册写事件");
                            } else {
                                putSelectionKey(key);
                            }
                            key.interestOps(0);
                        }

                        if (key.isWritable() && key.isValid()) {
                            SelectionKey acceptKey = (SelectionKey) key.attachment();

                            System.out.println("in");
                            int counter;
                            try {
                                counter = swapData((SocketChannel) key.channel(), (SocketChannel) acceptKey.channel());
                            } catch (IOException e) {
                                try {
                                    key.channel().close();
                                    key.cancel();
                                    setOPS(acceptKey, -1);
                                } catch (IOException ioException) {
                                    ioException.printStackTrace();
                                }
                                e.printStackTrace();
                                continue;
                            }
                            System.out.println("counter = " + counter);
                            // 若返回值为0，则说明客户端断开连接，可重新分配
                            if (counter == 0 && !acceptKey.isValid()) {
                                acceptKey.cancel();
                                key.attach(null);
                                SocketChannel socketChannel = (SocketChannel) key.channel();
                                try {
                                    buffer = ByteBuffer.wrap("#RESET".getBytes());
                                    socketChannel.write(buffer);
                                    buffer.clear();
                                    System.out.println(port + " 写入对象为空，对方已断开连接，发起重置请求，为本方注册读事件");
                                } catch (IOException e) {
                                    try {
                                        socketChannel.close();
                                    } catch (IOException ioException) {
                                        ioException.printStackTrace();
                                    }
                                    key.cancel();
                                    System.out.println("服务器连接失效，取消连接");
                                }
                            } else {
                                acceptKey.attach(key);
                                setOPS(acceptKey, SelectionKey.OP_READ);

                                System.out.println(port + " 已写入" + counter + "，为对方注册读事件，为本方注册读事件");
                                key.interestOps(SelectionKey.OP_READ);
                            }

                        }
                    }
                    iterator.remove();
                }
            }
        }
    }

    private void setOPS(SelectionKey key, int ops) {
        synchronized (records) {
            records.add(new OPSRecord(key, ops));
            synchronized (recordsLock) {
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
                OPSRecord record = records.remove();
                synchronized (record) {
                    SelectionKey key = record.getKey();
                    if (key.isValid()) {
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
