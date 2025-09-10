package br.iff.edu.ararajuba.log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.Optional;

import static java.nio.file.StandardOpenOption.*;

public class Segment {

    private final Path file;
    private final FileChannel ch;
    private volatile long nextOffset;

    Segment(Path file, long startOffset) throws IOException{
        this.file = file;
        this.ch = FileChannel.open(file, CREATE, READ, WRITE);
        this.nextOffset = startOffset;

        ch.position(ch.size());
    }

    long append(byte[] key, byte[] value) throws IOException{
        long off = this.nextOffset++;
        ByteBuffer rec = RecordCodec.encode(off, key, value);
        while(rec.hasRemaining()) ch.write(rec);

        return off;
    }

    Optional<MessageRecord> readAtOffset(long offset) throws IOException{

        long pos = 0;

        ch.position(0);

        ByteBuffer lenBuf = ByteBuffer.allocate(4);

        while(true){
            lenBuf.clear();
            int r = ch.read(lenBuf);

            if(r < 0) return Optional.empty();
            if (r < 4) return Optional.empty();

            lenBuf.flip();

            int total = lenBuf.getInt();

            ByteBuffer recBuf = ByteBuffer.allocate(total -4);
            int read = ch.read(recBuf);
            if (read < recBuf.capacity()) return Optional.empty();

            recBuf.flip();

            MessageRecord rec = RecordCodec.decode(-1, ByteBuffer.allocate(total).putInt(total).put(recBuf).flip());

            return Optional.of(rec);
        }
    }
    FileChannel channel() {return this.ch;}
    Path path() {return this.file;}
    long nextOffset() {return this.nextOffset;}
    void setNextOffset(long off) {nextOffset = off;}
    void close() throws IOException {ch.close();}
}
