package br.iff.edu.ararajuba.state;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class ConsumerStateStore {

    private Path baseDir;
    private final ObjectMapper om = new ObjectMapper();

    private static class State {
        public long committedOffset = -1L;
    }

    private final Map<String, State> cache = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        String dir = System.getProperty("ararajuba.data.dir");
        if (dir == null) dir = System.getenv().getOrDefault("ARARAJUBA_DATA_DIR", "data");
        baseDir = Paths.get(dir).resolve("consumer-state");
        try {
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Path file(String topic, String group) {
        return baseDir.resolve(topic + "__" + group + ".json");
    }

    private String key(String topic, String group) {
        return topic + "::" + group;
    }

    public synchronized long getCommitted(String topic, String group) {
        String k = key(topic, group);
        State st = cache.computeIfAbsent(k, kk -> {
            Path f = file(topic, group);
            if (Files.exists(f)) {
                try {
                    return om.readValue(f.toFile(), State.class);
                } catch (IOException e) {
                    return new State();
                }
            }
            return new State();
        });
        return st.committedOffset;
    }

    public synchronized void commit(String topic, String group, long offset) {
        String k = key(topic, group);
        State st = cache.computeIfAbsent(k, kk -> new State());
        if (offset > st.committedOffset) {
            st.committedOffset = offset;
            try {
                om.writerWithDefaultPrettyPrinter().writeValue(file(topic, group).toFile(), st);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
