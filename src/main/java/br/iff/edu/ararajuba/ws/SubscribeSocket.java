package br.iff.edu.ararajuba.ws;

import br.iff.edu.ararajuba.core.DeliveryService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;

@ServerEndpoint("/ws/subscribe")
@ApplicationScoped
public class SubscribeSocket {

    @Inject
    DeliveryService delivery;

    @OnOpen
    public void onOpen(Session session) {
        var q = session.getRequestParameterMap();
        String topic = q.getOrDefault("topic", java.util.List.of("default")).get(0);
        String group = q.getOrDefault("group", java.util.List.of("anon")).get(0);
        delivery.register(topic, group, session);
    }

    @OnMessage
    public void onMessage(Session session, String text) {
        delivery.ack(session, text);
    }

    @OnClose
    public void onClose(Session session) {
        delivery.unregister(session);
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        delivery.unregister(session);
    }
}
