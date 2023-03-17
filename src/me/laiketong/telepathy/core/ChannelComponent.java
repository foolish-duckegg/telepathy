package me.laiketong.telepathy.core;

import java.net.Socket;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public interface ChannelComponent {


    SelectionKey getSelectionKey() throws InterruptedException;

    public Socket getSocket() throws InterruptedException;

    public SocketChannel getSocketChannel() throws InterruptedException;

    public SelectionKey getReadableKey() throws InterruptedException;

    public int getPreparedNums();
}
