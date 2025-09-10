package br.iff.edu.ararajuba.log;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.nio.file.StandardOpenOption.*;

@ApplicationScoped
public class CommitLog {

    private Path baseDir;

    private static class TopicState {
        Segment segment;
        long baseOffset;

        final NavigableMap<Long, Long> sparseIndex = new TreeMap<>();
    }

    private final Map<String, TopicState> topics = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {

        String dir = System.getProperty("ararajuba.data.dir");
        if (dir == null) {
            dir = System.getenv().getOrDefault("ARARAJUBA_DATA_DIR", "data");
        }
        baseDir = Paths.get(dir).resolve("topics");
        try {
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            throw new RuntimeException("Falha ao criar diretório base: " + baseDir, e);
        }
    }

    private TopicState ensureTopic(String topic) throws IOException {
        return topics.computeIfAbsent(topic, t -> {
            try {
                Path tdir = baseDir.resolve(t);
                Files.createDirectories(tdir);
                Path logFile = tdir.resolve("000000000000.log");

                long startOffset = 0;
                Segment seg = new Segment(logFile, startOffset);

                TopicState st = new TopicState();
                st.segment = seg;
                st.baseOffset = 0;

                try (FileChannel ch = FileChannel.open(logFile, CREATE, READ)) {
                    ch.position(0);
                    long filePos = 0;
                    long off = 0;

                    ByteBuffer lenBuf = ByteBuffer.allocate(4);
                    while (true) {
                        lenBuf.clear();
                        int r = ch.read(lenBuf);
                        if (r < 0) break;
                        if (r < 4) break;
                        lenBuf.flip();
                        int total = lenBuf.getInt();

                        ByteBuffer body = ByteBuffer.allocate(total - 4);
                        int n = ch.read(body);
                        if (n < body.capacity()) break;

                        filePos += total;
                        if (off % 100 == 0) {
                            st.sparseIndex.put(off, filePos - total);
                        }
                        off++;
                    }
                    seg.setNextOffset(off);
                } catch (IOException ex) {
                    throw new RuntimeException("Falha ao reconstruir índice do tópico " + t, ex);
                }

                return st;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public synchronized long append(String topic, byte[] key, byte[] value) throws IOException {
        TopicState st = ensureTopic(topic);
        long off = st.segment.append(key, value);

        FileChannel ch = st.segment.channel();
        long filePos = ch.position();

        if (off % 100 == 0) {
            st.sparseIndex.put(off, filePos - (long) getLastRecordSize(st.segment));
        }
        return off;
    }

    private int getLastRecordSize(Segment seg) throws IOException {
        FileChannel ch = seg.channel();
        long end = ch.position();
        if (end < 4) return 0;

        long pos = end - 4;
        ByteBuffer lenBuf = ByteBuffer.allocate(4);
        ch.position(pos);
        int r = ch.read(lenBuf);
        if (r < 4) {
            ch.position(end);
            return 0;
        }
        lenBuf.flip();
        int total = lenBuf.getInt();

        ch.position(end);
        return total;
    }

    public Optional<MessageRecord> read(String topic, long offset) throws IOException {
        TopicState st = ensureTopic(topic);
        Path file = st.segment.path();

        try (FileChannel ch = FileChannel.open(file, READ)) {
            Map.Entry<Long, Long> floor = st.sparseIndex.floorEntry(offset);
            long startPos = (floor != null) ? floor.getValue() : 0L;
            long currentOff = (floor != null) ? floor.getKey() : 0L;

            ch.position(startPos);
            ByteBuffer lenBuf = ByteBuffer.allocate(4);

            while (true) {
                lenBuf.clear();
                int r = ch.read(lenBuf);
                if (r < 0) return Optional.empty();   // EOF
                if (r < 4) return Optional.empty();   // registro incompleto

                lenBuf.flip();
                int total = lenBuf.getInt();

                ByteBuffer recBuf = ByteBuffer.allocate(total - 4);
                int read = ch.read(recBuf);
                if (read < recBuf.capacity()) return Optional.empty(); // truncado
                recBuf.flip();

                ByteBuffer full = ByteBuffer.allocate(total);
                full.putInt(total);
                full.put(recBuf);
                full.flip();

                MessageRecord rec = RecordCodec.decode(currentOff, full);

                if (currentOff == offset) {
                    return Optional.of(rec);
                }
                currentOff++;
            }
        }
    }

    public long nextOffset(String topic) throws IOException {
        TopicState st = ensureTopic(topic);
        return st.segment.nextOffset();
    }

    public List<String> listTopics() throws IOException {
        if (!Files.exists(baseDir)) return List.of();
        try (var s = Files.list(baseDir)) {
            return s.filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .sorted()
                    .toList();
        }
    }
}
