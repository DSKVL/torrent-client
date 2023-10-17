package ru.nsu.fit.dskvl.torrent.network.message;

import ru.nsu.fit.dskvl.torrent.network.MessageProcessor;
import ru.nsu.fit.dskvl.torrent.network.PeerConnection;

import java.nio.ByteBuffer;
import java.util.BitSet;

public class BitfieldMessage extends Message {
    private final BitSet bitfield;

    public BitfieldMessage(byte[] buf, PeerConnection source) {
        int bitfieldLength = source.getTorrent().getNumberOfPieces();
        this.buffer = ByteBuffer.allocate(buf.length);
        bitfield = new BitSet(bitfieldLength);

        for (int i = 0; i < bitfieldLength/8; i++) {
            for (int j = 0; j < 8; j++) {
                if ((buf[i+1]>>(7-j)) % 2 != 0) {
                    bitfield.set(i*8 + j);
                }
            }
        }

        for (int i = 0; i < bitfieldLength%8; i++) {
            if ((buf[buf.length - 1]>>(7-i)) % 2 != 0) {
                bitfield.set(bitfieldLength - bitfieldLength%8 + i);
            }
        }
        buffer.rewind();
    }

    public BitfieldMessage(BitSet bitSet) {
        bitfield = bitSet;
        int bitfieldLength = Math.ceilDiv(bitfield.length(), 8);
        buffer = ByteBuffer.allocate(5 + bitfieldLength);

        buffer.putInt(1 + bitfieldLength);
        buffer.put((byte) 5);
        byte b = 0;
        int inByte = 0;
        for (int i = 0; i < bitfield.length(); i++) {
            if (bitfield.get(i)) {
                b |= (1 << (7 - i%8));
            } else {
                b &= ~(1 << (7 - i%8));
            }
            inByte++;
            if (inByte == 8) {
                buffer.put(b);
                inByte = 0;
            }
        }
        if (inByte != 0) {
            buffer.put(b);
        }
        buffer.rewind();
    }

    public BitSet getBitfield() {
        return bitfield;
    }

    @Override
    public String toString() {
        return "BITFIELD" + bitfield.toString();
    }

    @Override
    public void accept(MessageProcessor processor) {
        processor.process(this);
    }
}
