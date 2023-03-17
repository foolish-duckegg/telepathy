package me.laiketong.telepathy.core;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class NewServerChannelComponent extends ChannelComponentImp implements Runnable {

    final int port;
    final Queue<SelectionKey> readableKeys;
    final Queue<SelectionKey> writableKeys;
    final ReentrantLock writableLock;
    final ReentrantLock readableLock;
    final Condition writableCondition;
    final Condition readableCondition;
    ServerSocketChannel serverSocketChannel;
    final Selector selector;

    public NewServerChannelComponent(int port) throws IOException {
        this.port = port;
        readableKeys = new LinkedList<>();
        writableKeys = new LinkedList<>();
        writableLock = new ReentrantLock();
        readableLock = new ReentrantLock();
        writableCondition = writableLock.newCondition();
        readableCondition = readableLock.newCondition();
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.bind(new InetSocketAddress(port));
        selector = Selector.open();
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        new Thread(this).start();
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
                        if (key.isAcceptable()) {
                            try {
                                SocketChannel socketChannel = ((ServerSocketChannel) key.channel()).accept();
                                socketChannel.configureBlocking(false);
                                socketChannel.register(selector, SelectionKey.OP_READ);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                        if (key.isWritable() && key.isValid()) {
                            writableLock.lock();
                            try {
                                writableKeys.add(key);
                                writableCondition.signal();
                            } finally {
                                writableLock.unlock();
                            }
                            key.interestOps(0);
                        }
                        if (key.isReadable() && key.isValid()) {
                            readableLock.lock();
                            try {
                                readableKeys.add(key);
                                readableCondition.signal();
                            } finally {
                                readableLock.unlock();
                            }
                            key.interestOps(0);
                        }
                    }
                    iterator.remove();
                }
            }
        }
    }

    @Override
    public SelectionKey getSelectionKey() throws InterruptedException {
        writableLock.lock();
        try {
            if (writableKeys.isEmpty()) {
                writableCondition.await();
            }
            return writableKeys.remove();
        } finally {
            writableLock.unlock();
        }
    }

    @Override
    public Socket getSocket() throws InterruptedException {
        SelectionKey key = getSelectionKey();
        Socket socket = ((SocketChannel) key.channel()).socket();
        key.cancel();
        return socket;
    }

    @Override
    public SocketChannel getSocketChannel() throws InterruptedException {
        SelectionKey key = getSelectionKey();

        SocketChannel socketChannel = (SocketChannel) key.channel();
        key.cancel();
        return socketChannel;
    }

    @Override
    public SelectionKey getReadableKey() throws InterruptedException {
        readableLock.lock();
        try {
            if (readableKeys.isEmpty()) {
                readableCondition.await();
            }
            return readableKeys.remove();
        } finally {
            readableLock.unlock();
        }

    }

    @Override
    public int getPreparedNums() {
        return selector.keys().size();
    }
}
