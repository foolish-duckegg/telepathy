package me.laiketong.telepathy.connector;

import me.laiketong.telepathy.core.ChannelComponent;
import me.laiketong.telepathy.core.MiddleWareComponent;
import me.laiketong.telepathy.core.ServerChannelComponent;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class ServerToServerConnector extends MiddleWareComponent implements Runnable {

    protected ChannelComponent serverComponent;
    protected ChannelComponent clientComponent;
    ByteBuffer byteBuffer;

    public ServerToServerConnector(int serverPort, int clientPort) throws IOException {
        this.serverComponent = new ServerChannelComponent(serverPort);
        this.clientComponent = new ServerChannelComponent(clientPort);

        byteBuffer = ByteBuffer.allocateDirect(2048);
        new Thread(this).start();
    }

    public ServerToServerConnector(ChannelComponent serverComponent, ChannelComponent clientComponent) {
        this.serverComponent = serverComponent;
        this.clientComponent = clientComponent;

        byteBuffer = ByteBuffer.allocateDirect(2048);
        new Thread(this).start();
    }

    /***
     * 默认服务器端口10520和客户端端口10521
     * @throws IOException
     */
    public ServerToServerConnector() throws IOException {
        this(10520, 10521);
    }

    @Override
    public void run() {

        while (true) {
            try {
                SelectionKey serverKey;
                do {
                    serverKey= serverComponent.getSelectionKey();
                } while (!clearBuffer(serverKey));

                System.out.println("获得服务连接");

                SelectionKey clientKey;

                clientKey = clientComponent.getSelectionKey();
                System.out.println("获得客户连接");

                serverKey.attach(clientKey);
                serverKey.interestOps(SelectionKey.OP_WRITE);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }

    private boolean clearBuffer(SelectionKey key) {
        try {
            while (((SocketChannel) key.channel()).read(byteBuffer) > 0) {
                System.out.println(1);
                byteBuffer.clear();
            }
            return true;
        } catch (IOException e) {
            try {
                key.channel().close();
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
            key.cancel();
            e.printStackTrace();
            return false;
        }
    }
}
