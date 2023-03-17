package me.laiketong.telepathy.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

public abstract class ChannelComponentImp implements ChannelComponent{

    ByteBuffer buffer;

    public ChannelComponentImp() {
        buffer = ByteBuffer.allocateDirect(2048);
    }

    protected int swapData(WritableByteChannel accept, ReadableByteChannel send) throws IOException {

        int sum = 0;

        while (send.read(buffer) > 0) {
            System.out.println(2);
            buffer.flip();
            sum += accept.write(buffer);
        }

        if (sum == -1 || sum == 0) {
            send.close();
        }

        buffer.clear();

        return sum;

    }

}
