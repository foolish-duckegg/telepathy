package me.laiketong.telepathy.connector;

import me.laiketong.telepathy.core.ChannelComponent;
import me.laiketong.telepathy.core.MiddleWareComponent;
import me.laiketong.telepathy.core.NewServerChannelComponent;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.Queue;

public class NewServerToServerConnector extends MiddleWareComponent {

    protected final ChannelComponent serverComponent;
    protected final ChannelComponent clientComponent;
    ByteBuffer byteBuffer;

    private final Queue<SelectionKey> serviceServerKeys;
    private final Queue<SelectionKey> clientServerKeys;
    private final Queue<SelectionKey> changeableKeys;

    private final Object serviceLock;
    private final Object clientLock;
    private final Object changeableLock;

    public NewServerToServerConnector(int serverPort, int clientPort) throws IOException {
        this.serverComponent = new NewServerChannelComponent(serverPort);
        this.clientComponent = new NewServerChannelComponent(clientPort);

        this.serviceServerKeys = new LinkedList<>();
        this.changeableKeys = new LinkedList<>();
        this.clientServerKeys = new LinkedList<>();

        this.serviceLock = new Object();
        this.clientLock = new Object();
        this.changeableLock = new Object();

        byteBuffer = ByteBuffer.allocateDirect(2048);
        this.run();
    }

    public NewServerToServerConnector(ChannelComponent serverComponent, ChannelComponent clientComponent) {
        this.serverComponent = serverComponent;
        this.clientComponent = clientComponent;

        this.serviceServerKeys = new LinkedList<>();
        this.changeableKeys = new LinkedList<>();
        this.clientServerKeys = new LinkedList<>();

        this.serviceLock = new Object();
        this.clientLock = new Object();
        this.changeableLock = new Object();

        byteBuffer = ByteBuffer.allocateDirect(2048);
        this.run();
    }

    /***
     * 默认服务器端口10520和客户端端口10521
     * @throws IOException
     */
    public NewServerToServerConnector() throws IOException {
        this(10520, 10521);
    }


    private void run() {

        //服务端writableKey线程
        new Thread(() -> {
            SelectionKey key = null;
            while (true) {
                try {
                    key = serverComponent.getSelectionKey();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                System.out.println("服务端可写连接进入");
                if (key.attachment() == null) {
                    synchronized (serviceLock) {
                        serviceServerKeys.add(key);
                        serviceLock.notify();
                    }
                    System.out.println("加入等待队列");
                } else if (!((SelectionKey) key.attachment()).isValid()) {
                    key.attach(null);
                    ByteBuffer buffer = ByteBuffer.wrap("#RESET".getBytes());
                    try {
                        ((SocketChannel) key.channel()).write(buffer);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    buffer.clear();
                    setOPS(key, SelectionKey.OP_READ);
                    System.out.println("连接重置");
                } else {
                    synchronized (changeableKeys) {
                        changeableKeys.add(key);
                        synchronized (changeableLock) {
                            changeableLock.notify();
                        }
                    }
                    System.out.println("加入可写队列");
                }
            }
        }).start();

        //服务端readableKey线程
        new Thread(() -> {

            ByteBuffer buffer = ByteBuffer.allocateDirect(2048);
            while (true) {
                SelectionKey key = null;
                try {
                    key = serverComponent.getReadableKey();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                System.out.println("服务端可读连接进入");
                if (key.attachment() == null) {
                    try {
                        ((SocketChannel) key.channel()).read(buffer);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    buffer.clear();

                    setOPS(key, SelectionKey.OP_WRITE);
                    System.out.println("空连接，等待加入空闲队列");
                } else {
                    setOPS((SelectionKey) key.attachment(), SelectionKey.OP_WRITE);
                }
            }
        }).start();

        //客户端writableKey线程
        new Thread(() -> {

            while (true) {
                SelectionKey key = null;
                try {
                    key = clientComponent.getSelectionKey();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println("客户端可写连接进入");

                synchronized (changeableKeys) {
                    changeableKeys.add(key);
                    synchronized (changeableLock) {
                        changeableLock.notify();
                    }
                }
            }
        }).start();

        //客户端readableKey线程
        new Thread(() -> {

            while (true) {
                SelectionKey key = null;
                try {
                    key = clientComponent.getReadableKey();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println("客户端可读连接进入");
                if (key.attachment() == null) {
                    synchronized (clientLock) {
                        clientServerKeys.add(key);
                        clientLock.notify();
                        System.out.println("加入待配对列表");
                    }
                } else {
                    synchronized (changeableKeys) {
                        changeableKeys.add((SelectionKey) key.attachment());
                        synchronized (changeableLock) {
                            changeableLock.notify();
                        }
                    }
                }

            }
        }).start();

        //交换队列维护线程
        new Thread(() -> {
            while (true) {
                synchronized (changeableLock) {
                    if (changeableKeys.isEmpty()) {
                        try {
                            changeableLock.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }

                System.out.println("开始交换数据");

                while (!changeableKeys.isEmpty()) {
                    SelectionKey key;
                    synchronized (changeableKeys) {
                        key = changeableKeys.remove();
                    }
//                    System.out.println(key);
                    synchronized (key) {
                        synchronized (key.attachment()) {
                            try {
                                int count = 0;
                                if (((SelectionKey) key.attachment()).isValid() && key.isValid()) {
                                    count = swapData((SocketChannel) key.channel(), (SocketChannel) ((SelectionKey) key.attachment()).channel());
                                }
                                System.out.println("交换数据" + count);
                                if (count == 0 || count == -1) {

                                    ((SelectionKey) key.attachment()).channel().close();
                                    ((SelectionKey) key.attachment()).cancel();
                                    key.interestOps(SelectionKey.OP_WRITE);
                                } else {
                                    key.interestOps(SelectionKey.OP_READ);
                                    ((SelectionKey) key.attachment()).interestOps(SelectionKey.OP_READ);
                                    System.out.println("交换完毕，设置双方可读");
                                }
                            } catch (IOException e) {
                                try {
                                    key.channel().close();
                                    key.cancel();
                                    ((SelectionKey) key.attachment()).channel().close();
                                    ((SelectionKey) key.attachment()).cancel();
                                    System.out.println("in false 1");
                                    e.printStackTrace();
                                } catch (IOException ioException) {
                                    ioException.printStackTrace();
                                }
                            }
                        }
                    }
                }
            }
        }).start();

        //等待连接配对线程
        new Thread(() -> {
            SelectionKey freeServer = null;
            SelectionKey freeClient = null;
            while (true) {
                try {

                    System.out.println("正在获取服务端连接");
                    synchronized (serviceLock) {
                        if (serviceServerKeys.isEmpty()) {
                            serviceLock.wait();
                        }
                        freeServer = serviceServerKeys.remove();
                    }

                    System.out.println("正在获取客户端连接");

                    synchronized (clientLock) {
                        if (clientServerKeys.isEmpty()) {
                            clientLock.wait();
                        }
                        freeClient = clientServerKeys.remove();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                freeServer.attach(freeClient);
                freeClient.attach(freeServer);
                System.out.println("配对成功");

                synchronized (changeableLock) {
                    synchronized (changeableKeys) {
                        changeableKeys.add(freeServer);
                    }
                    changeableLock.notify();
                }
            }
        }).start();
    }


}
