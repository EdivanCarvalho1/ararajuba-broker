package br.iff.edu.ararajuba.core;

import br.iff.edu.ararajuba.log.CommitLog;
import br.iff.edu.ararajuba.log.MessageRecord;
import br.iff.edu.ararajuba.state.ConsumerStateStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.Session;

import java.util.*;
import java.util.concurrent.*;


@ApplicationScoped
public class DeliveryService {

    @Inject
    CommitLog log;
    @Inject
    ConsumerStateStore state;
    @Inject Dispatcher dispatcher;

    private int MAX_IN_FLIGHT;
    private long ACK_TIMEOUT_MS;

    private final ObjectMapper om = new ObjectMapper();

    private static final class ClientCtx {
        final String topic;
        final String group;
        final Session session;

        long nextOffset;

        final Map<String, Long> pending = new ConcurrentHashMap<>();

        final NavigableSet<Long> ackedNotCommitted = new ConcurrentSkipListSet<>();

        ClientCtx(String topic, String group, Session session, long nextOffset) {
            this.topic = topic;
            this.group = group;
            this.session = session;
            this.nextOffset = nextOffset;
        }
    }

    private final Map<Session, ClientCtx> clients = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    @PostConstruct
    void init() {

        MAX_IN_FLIGHT = Integer.parseInt(System.getProperty("andorinha.delivery.max-in-flight",
                System.getenv().getOrDefault("ANDORINHA_MAX_IN_FLIGHT", "100")));
        ACK_TIMEOUT_MS = Long.parseLong(System.getProperty("andorinha.delivery.ack-timeout.ms",
                System.getenv().getOrDefault("ANDORINHA_ACK_TIMEOUT_MS", "15000")));


        scheduler.scheduleAtFixedRate(this::tick, 200, 50, TimeUnit.MILLISECONDS);


        scheduler.scheduleAtFixedRate(this::retryUnacked, 1, 1, TimeUnit.SECONDS);
    }

    public void register(String topic, String group, Session s) {
        try {
            long committed = state.getCommitted(topic, group);
            long next = committed + 1;
            ClientCtx ctx = new ClientCtx(topic, group, s, next);
            clients.put(s, ctx);
            deliverMore(ctx);
        } catch (Exception e) {
            closeQuiet(s);
        }
    }

    public void unregister(Session s) {
        clients.remove(s);
        closeQuiet(s);
    }


    public void ack(Session s, String json) {
        ClientCtx ctx = clients.get(s);
        if (ctx == null) return;
        try {
            var node = om.readTree(json);
            if (!node.has("ack")) return;

            String deliveryId = node.get("ack").asText();
            Long off = ctx.pending.remove(deliveryId);
            if (off == null) {

                return;
            }

            long committed = state.getCommitted(ctx.topic, ctx.group);

            if (off == committed + 1) {

                long advance = off;


                while (ctx.ackedNotCommitted.contains(advance + 1)) {
                    ctx.ackedNotCommitted.remove(advance + 1);
                    advance = advance + 1;
                }
                state.commit(ctx.topic, ctx.group, advance);
            } else if (off > committed + 1) {
                ctx.ackedNotCommitted.add(off);
            }

            deliverMore(ctx);

        } catch (Exception ignored) {}
    }

    private void deliverMore(ClientCtx ctx) {

        if (ctx == null || ctx.session == null || !ctx.session.isOpen()) return;

        while (ctx.pending.size() < MAX_IN_FLIGHT) {
            try {

                java.util.Optional<MessageRecord> recOpt =
                        log.read(ctx.topic, ctx.nextOffset);

                if (recOpt.isEmpty()) break;

                MessageRecord r = recOpt.get();

                String deliveryId = java.util.UUID.randomUUID().toString();

                java.util.Map<String, Object> frame = new java.util.LinkedHashMap<>();
                frame.put("deliveryId", deliveryId);
                frame.put("offset", r.offset());
                frame.put("ts", r.ts());
                frame.put("key", (r.key() == null ? null
                        : new String(r.key(), java.nio.charset.StandardCharsets.UTF_8)));
                frame.put("value", (r.value() == null ? null
                        : new String(r.value(), java.nio.charset.StandardCharsets.UTF_8)));

                String payload = om.writeValueAsString(frame);
                ctx.session.getAsyncRemote().sendText(payload);

                ctx.pending.put(deliveryId, r.offset());

                ctx.nextOffset = r.offset() + 1;

                scheduler.schedule(
                        () -> timeoutCheck(ctx, deliveryId),
                        ACK_TIMEOUT_MS,
                        java.util.concurrent.TimeUnit.MILLISECONDS
                );

            } catch (java.io.IOException io) {
                closeQuiet(ctx.session);
                break;

            } catch (Exception ex) {
                break;
            }
        }
    }


    private void timeoutCheck(ClientCtx ctx, String deliveryId) {
        if (!clients.containsKey(ctx.session)) return;

        Long off = ctx.pending.remove(deliveryId);
        if (off != null) {
            if (off < ctx.nextOffset) {
                ctx.nextOffset = off;
            }
            deliverMore(ctx);
        }
    }


    private void tick() {
        clients.values().forEach(this::deliverMore);
    }

    private void retryUnacked() {
        for (ClientCtx ctx : clients.values()) {
            if (!ctx.session.isOpen()) continue;

            if (dispatcher.hasNewData(ctx.topic)) {
                deliverMore(ctx);
            }


            if (ctx.pending.isEmpty()) {
                deliverMore(ctx);
            }
        }

        try {
            Set<String> signaled = new HashSet<>();
            for (ClientCtx ctx : clients.values()) {
                if (dispatcher.hasNewData(ctx.topic)) signaled.add(ctx.topic);
            }
            for (String t : signaled) dispatcher.clear(t);
        } catch (Exception ignored) {}
    }

    private void closeQuiet(Session s) {
        try { s.close(); } catch (Exception ignored) {}
    }
}
