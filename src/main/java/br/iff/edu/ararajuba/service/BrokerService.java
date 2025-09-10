package br.iff.edu.ararajuba.service;

import br.iff.edu.ararajuba.core.Dispatcher;
import br.iff.edu.ararajuba.dto.AckDTO;
import br.iff.edu.ararajuba.dto.MessageDTO;
import br.iff.edu.ararajuba.dto.MessageView;
import br.iff.edu.ararajuba.log.CommitLog;
import br.iff.edu.ararajuba.log.MessageRecord;
import br.iff.edu.ararajuba.state.ConsumerStateStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class BrokerService {

    @Inject CommitLog log;
    @Inject ConsumerStateStore state;
    @Inject Dispatcher dispatcher;

    public long publish(String topic, MessageDTO messageDTO) throws Exception {
        if (messageDTO == null) {
            throw new BadRequestException("Corpo JSON ausente ou inválido. Ex.: {\"key\":\"k\",\"value\":\"mensagem\"}");
        }
        if (messageDTO.value() == null) {
            throw new BadRequestException("Campo \"value\" é obrigatório.");
        }

        byte[] key = messageDTO.key() == null ? null : messageDTO.key().getBytes(StandardCharsets.UTF_8);
        byte[] val = messageDTO.value().getBytes(StandardCharsets.UTF_8);

        long off = log.append(topic, key, val);
        dispatcher.enqueue(topic, off);
        return off;
    }

    public List<MessageView> poll(String topic, String consumerGroup, int max, long timeout) throws Exception {
        if (max <= 0) max = 1;
        if (max > 1000) max = 1000;

        final boolean stateless = (consumerGroup == null || consumerGroup.isBlank());

        long start = System.currentTimeMillis();
        List<MessageView> out = new ArrayList<>();

        // Sem consumerGroup -> começa do offset 0 e não persiste estado
        long next = stateless ? 0 : state.getCommitted(topic, consumerGroup) + 1;

        while ((System.currentTimeMillis() - start) < timeout) {
            for (int i = 0; i < max; i++) {
                Optional<MessageRecord> ropt = log.read(topic, next);
                if (ropt.isEmpty()) break;

                MessageRecord r = ropt.get();
                String key = r.key() == null ? null : new String(r.key(), StandardCharsets.UTF_8);
                String val = r.value() == null ? null : new String(r.value(), StandardCharsets.UTF_8);

                out.add(new MessageView(r.offset(), r.ts(), key, val));
                next = r.offset() + 1;

                if (out.size() >= max) break;
            }

            if (!out.isEmpty()) break;
            Thread.sleep(50);
        }
        return out;
    }


    public void ack(String topic, String consumerGroup, AckDTO ackDTO) {
        if (consumerGroup == null || consumerGroup.isBlank()) return;
        if (ackDTO == null || ackDTO.ids() == null || ackDTO.ids().isEmpty()) return;

        long committed = state.getCommitted(topic, consumerGroup);
        long nextExpected = committed + 1;

        boolean advanced;
        do {
            advanced = false;
            for (String id : ackDTO.ids()) {
                try {
                    long off = Long.parseLong(id);
                    if (off == nextExpected) {
                        state.commit(topic, consumerGroup, off);
                        nextExpected = off + 1;
                        advanced = true;
                    }
                } catch (NumberFormatException ignored) {}
            }
        } while (advanced);
    }

    public List<String> listTopics() throws Exception {
        return log.listTopics();
    }
}
