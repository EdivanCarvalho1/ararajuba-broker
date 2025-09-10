package br.iff.edu.ararajuba.log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.CRC32;

public final class RecordCodec {

    private RecordCodec(){

    }

    public static ByteBuffer encode(long offset, byte[] key, byte[] value){
        long ts = System.currentTimeMillis();

        byte [] k = key == null ? new byte[0] : key;
        byte [] v = value == null? new byte[0] : value;

        int payload = Long.BYTES + Integer.BYTES + Integer.BYTES + k.length + v.length;
        int total = Integer.BYTES + Integer.BYTES + payload;

        ByteBuffer buf = ByteBuffer.allocate(total).order(ByteOrder.BIG_ENDIAN);
        buf.putInt(total);

        int crcPos = buf.position();

        buf.putInt(0);
        buf.putLong(ts);
        buf.putInt(k.length);
        buf.putInt(v.length);
        buf.put(k);
        buf.put(v);

        CRC32 crc = new CRC32();

        ByteBuffer view = buf.duplicate().order(ByteOrder.BIG_ENDIAN);

        view.position(crcPos + Integer.BYTES);
        byte[] data = new byte[payload];
        view.get(data);
        crc.update(data);

        buf.putInt(crcPos, (int) crc.getValue());
        buf.flip();

        return buf;
    }
    public static MessageRecord decode(long offset, ByteBuffer buf){
        buf.order(ByteOrder.BIG_ENDIAN);

        int total = buf.getInt();
        int crc = buf.getInt();
        long ts = buf.getLong();
        int klen = buf.getInt();
        int vlen = buf.getInt();

        byte [] k = new byte[klen];
        byte [] v = new byte[vlen];

        buf.get(k);
        buf.get(v);

        return new MessageRecord(offset, ts, k, v);
    }
}
