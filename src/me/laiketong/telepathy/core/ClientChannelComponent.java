package me.laiketong.telepathy.core;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

public class ClientChannelComponent extends ChannelComponentImp implements Runnable {

    final int port;
    final String ipAddress;
    final Queue<SelectionKey> readableKeys;
    final Queue<SelectionKey> writableKeys;
    final Object writableLock;
    final Object readableLock;
    final Selector selector;

    public ClientChannelComponent(int port, String ipAddress, int counter) throws IOException {
        this.port = port;
        this.ipAddress = ipAddress;

        writableLock = new Object();
        readableLock = new Object();
        writableKeys = new LinkedList<>();
        readableKeys = new LinkedList<>();
        selector = Selector.open();

        makeConnection(counter);

        new Thread(this).start();
    }

    public ClientChannelComponent(int serverPort, String serverAdd) throws IOException {
        this(serverPort, serverAdd, 0);
    }

    public void makeConnection(int num) throws IOException {
        for (int i = 0; i < num; i++) {
            System.out.println(i);
            makeConnection();
        }
    }

    public void makeConnection() throws IOException {
        SocketChannel socketChannel = SocketChannel.open();
        socketChannel.connect(new InetSocketAddress(ipAddress, port));
        socketChannel.configureBlocking(false);
        socketChannel.register(selector, SelectionKey.OP_WRITE);
    }

    /**
     * 获取准备就绪的 WriteableKey
     * @return
     * @throws InterruptedException
     */
    @Override
    public SelectionKey getSelectionKey() throws InterruptedException {
        synchronized (writableLock) {
            if (writableKeys.isEmpty()) writableLock.wait();
        }
        synchronized (writableKeys) {
            return writableKeys.remove();
        }
    }

    /**
     * 从准备就绪的 writableKey 中获取其中一个，取消注册并获取该socket
     * @return
     * @throws InterruptedException
     */
    @Override
    public Socket getSocket() throws InterruptedException {
        SelectionKey key = getSelectionKey();
        Socket socket = ((SocketChannel) key.channel()).socket();
        key.cancel();
        return socket;
    }

    /**
     * 从准备就绪的 writableKey 中获取其中一个，取消注册并获取该socketChannel
     * @return
     * @throws InterruptedException
     */
    @Override
    public SocketChannel getSocketChannel() throws InterruptedException {
        SelectionKey key = getSelectionKey();

        SocketChannel socketChannel = (SocketChannel) key.channel();
        key.cancel();
        return socketChannel;
    }

    @Override
    public SelectionKey getReadableKey() throws InterruptedException {
        synchronized (readableLock) {
            if (readableKeys.isEmpty()) readableLock.wait();
        }
        synchronized (readableKeys) {
            return readableKeys.remove();
        }
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
                        if (key.isConnectable()) {
                            key.interestOps(SelectionKey.OP_WRITE);
                            System.out.println(1);
                        }
                        if (key.isReadable() && key.isValid()) {
                            synchronized (readableKeys) {
                                readableKeys.add(key);
                                synchronized (readableLock) {
                                    readableLock.notify();
                                }
                            }
                            key.interestOps(0);
                        }
                        if (key.isWritable() && key.isValid()) {
                            synchronized (writableKeys) {
                                writableKeys.add(key);
                                synchronized (writableLock) {
                                    writableLock.notify();
                                }
                            }
                            System.out.println(key.isValid());
                            key.interestOps(0);
                        }
                    }
                    iterator.remove();
                }
            }
        }

    }

    @Override
    public int getPreparedNums() {
        return selector.keys().size();
    }
}
