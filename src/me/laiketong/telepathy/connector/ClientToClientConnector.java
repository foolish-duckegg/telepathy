package me.laiketong.telepathy.connector;

import me.laiketong.telepathy.core.ClientChannelComponent;
import me.laiketong.telepathy.core.MiddleWareComponent;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.util.LinkedList;
import java.util.Queue;

public class ClientToClientConnector extends MiddleWareComponent {

    protected final ClientChannelComponent serverComponent;
    protected final ClientChannelComponent clientComponent;
    protected int num;

    private final Object keysLock;
    private final Object clientLock;
    private final Object serverLock;

    private final Queue<SelectionKey> changeableKeys;
    private final Queue<SelectionKey> freeClient;
    private final Queue<SelectionKey> freeServer;

    private final ByteBuffer writeByteBuffer;
    private final ByteBuffer compareByteBuffer;
    private final ByteBuffer swapByteBuffer;

    /**
     * @param serverAdd  代理服务器地址
     * @param serverPort 代理服务器端口
     * @param clientAdd  真实服务器地址
     * @param clientPort 真实服务器端口
     * @param num        最大连接数量
     * @throws IOException
     */
    public ClientToClientConnector(String serverAdd, int serverPort, String clientAdd, int clientPort, int num) throws IOException {
        serverComponent = new ClientChannelComponent(serverPort, serverAdd, num);
        this.num = num;
        clientComponent = new ClientChannelComponent(clientPort, clientAdd);

        changeableKeys = new LinkedList<>();
        freeClient = new LinkedList<>();
        freeServer = new LinkedList<>();

        keysLock = new Object();
        clientLock = new Object();
        serverLock = new Object();

        writeByteBuffer = ByteBuffer.allocateDirect(2048);
        swapByteBuffer = ByteBuffer.allocateDirect(2048);
        compareByteBuffer = ByteBuffer.wrap("#RESET".getBytes());

        this.run();
    }

    public ClientToClientConnector(ClientChannelComponent serverComponent, ClientChannelComponent clientComponent, int num) {
        this.num = num;
        this.serverComponent = serverComponent;
        this.clientComponent = clientComponent;

        changeableKeys = new LinkedList<>();
        freeServer = new LinkedList<>();
        freeClient = new LinkedList<>();

        keysLock = new Object();
        clientLock = new Object();
        serverLock = new Object();

        writeByteBuffer = ByteBuffer.allocateDirect(2048);
        swapByteBuffer = ByteBuffer.allocateDirect(2048);
        compareByteBuffer = ByteBuffer.wrap("#RESET".getBytes());

        this.run();
    }

    public void run() {

        //服务端writableKey线程
        new Thread(() -> {
            while (true) {
                SelectionKey key = null;
                try {
                    key = serverComponent.getSelectionKey();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println("服务端可写连接进入");
                if (key.attachment() == null) {
                    synchronized (key) {
                        SocketChannel channel = (SocketChannel) key.channel();
                        writeByteBuffer.clear();
                        writeByteBuffer.put("\n".getBytes());
                        writeByteBuffer.flip();
                        try {
                            channel.write(writeByteBuffer);
                            setOPS(key, SelectionKey.OP_READ);
                            System.out.println("无配对连接，为新连接，发送空字符串确认连接，并注册读事件");
                        } catch (IOException e) {
                            try {
                                key.channel().close();
                            } catch (IOException ioException) {
                                ioException.printStackTrace();
                            }
                            key.cancel();
                            System.out.println("写入错误，断开连接");
                            e.printStackTrace();
                        }
                    }
                } else {
                    synchronized (changeableKeys) {
                        changeableKeys.add(key);
                        synchronized (keysLock) {
                            keysLock.notify();
                        }
                    }

                    System.out.println("进入写队列");
                }
            }
        }).start();

        //服务端readableKey线程
        new Thread(() -> {
            while (true) {
                SelectionKey key = null;
                try {
                    key = serverComponent.getReadableKey();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println("服务端可读连接读入");
                if (key.attachment() == null) {
                    System.out.println("无配对连接");
                    try {
                        clientComponent.makeConnection();
                    } catch (IOException e) {
                        System.out.println("新建客户端连接时发生错误，取消连接");
                        try {
                            key.channel().close();
                        } catch (IOException ioException) {
                            ioException.printStackTrace();
                        }
                        key.cancel();
                        e.printStackTrace();
                        continue;
                    }
                    synchronized (serverLock) {
                        freeServer.add(key);
                        serverLock.notify();
                    }
                } else {
                    setOPS((SelectionKey) key.attachment(), SelectionKey.OP_WRITE);
                    System.out.println("为对方注册写事件");
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
                System.out.println("客户端获取可写连接");
                if (key.attachment() == null) {
                    synchronized (clientLock) {
                        freeClient.add(key);
                        clientLock.notify();
                    }
                    System.out.println("无配对，加入待配对队列");
                } else {
                    synchronized (changeableKeys) {
                        changeableKeys.add(key);
                        synchronized (keysLock) {
                            keysLock.notify();
                        }
                    }
                    System.out.println("进入写队列");
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
                setOPS((SelectionKey) key.attachment(), SelectionKey.OP_WRITE);
                System.out.println("客户端可读事件，为对方注册写事件");
            }
        }).start();

        //可交换数据连接队列维护线程
        new Thread(() -> {
            while (true) {
                try {
                    synchronized (keysLock) {
                        if (changeableKeys.isEmpty()) keysLock.wait();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                while (!changeableKeys.isEmpty()) {
                    System.out.println(changeableKeys.size());
                    SelectionKey writableKey;
                    synchronized (changeableKeys) {
                        writableKey = changeableKeys.remove();
                    }
                    SelectionKey readableKey = (SelectionKey) writableKey.attachment();
                    synchronized (writableKey) {
                        synchronized (readableKey) {
                            int count = 0;
                            try {
                                count = swapData((SocketChannel) writableKey.channel(), (SocketChannel) readableKey.channel());
                            } catch (IOException e) {
                                System.out.println("写失败，两端取消连接");
                                try {
                                    writableKey.channel().close();
                                    readableKey.channel().close();
                                } catch (IOException ioException) {
                                    ioException.printStackTrace();
                                }
                                writableKey.cancel();
                                readableKey.cancel();
                                continue;
                            }
                            if (count == 0) {
                                System.out.println("连接失效，为两端取消连接");
                                try {
                                    writableKey.channel().close();
                                    readableKey.channel().close();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                writableKey.cancel();
                                readableKey.cancel();
                            } else if (count == -1) {
                                try {
                                    writableKey.channel().close();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                writableKey.cancel();
                                readableKey.attach(null);
                                readableKey.interestOps(SelectionKey.OP_WRITE);
                                System.out.println("连接中断请求，断开客户端连接，等待新配对");
                            } else {
                                System.out.println("写入成功" + count);
                                writableKey.interestOps(SelectionKey.OP_READ);
                                readableKey.interestOps(SelectionKey.OP_READ);
                            }
                        }
                    }
                }
            }
        }).start();

        //连接数量维护线程
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(2);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                int prepared = serverComponent.getPreparedNums();
                if (prepared < num) {
                    try {
                        serverComponent.makeConnection(num - prepared);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();

        //空连接配对线程
        new Thread(() -> {
            while (true) {
                SelectionKey serverKey;
                SelectionKey clientKey;

                synchronized (serverLock) {
                    if (freeServer.isEmpty()) {
                        try {
                            serverLock.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    serverKey = freeServer.remove();
                }

                synchronized (clientLock) {
                    if (freeClient.isEmpty()) {
                        try {
                            clientLock.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    clientKey = freeClient.remove();
                }

                serverKey.attach(clientKey);
                clientKey.attach(serverKey);
                synchronized (changeableKeys) {
                    changeableKeys.add(clientKey);
                    synchronized (keysLock) {
                        keysLock.notify();
                    }
                }
            }
        }).start();

    }

    @Override
    protected int swapData(WritableByteChannel accept, ReadableByteChannel send) throws IOException {

        send.read(swapByteBuffer);
        swapByteBuffer.flip();

        if (compareByteBuffer.compareTo(swapByteBuffer) == 0) {
            swapByteBuffer.clear();
            return -1;
        }
        int sum = 0;
        sum += accept.write(swapByteBuffer);
        swapByteBuffer.clear();

        while (send.read(swapByteBuffer) > 0) {
            swapByteBuffer.flip();
            sum += accept.write(swapByteBuffer);
            swapByteBuffer.clear();
        }

        swapByteBuffer.clear();
        return sum;

    }
}
