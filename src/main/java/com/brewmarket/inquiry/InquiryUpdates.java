package com.brewmarket.inquiry;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
class InquiryUpdates {
    record RoomChanged(long roomId, long buyerId, long sellerId) {}
    private record Channel(long id, boolean roomList) {}

    private final ConcurrentHashMap<Channel, Set<SseEmitter>> subscriptions = new ConcurrentHashMap<>();

    SseEmitter subscribe(long roomId) {
        return subscribe(new Channel(roomId, false));
    }

    SseEmitter subscribeList(long userId) {
        return subscribe(new Channel(userId, true));
    }

    private SseEmitter subscribe(Channel channel) {
        var emitter = new SseEmitter(300_000L);
        emitter.onCompletion(() -> remove(channel, emitter));
        emitter.onTimeout(emitter::complete);
        emitter.onError(error -> remove(channel, emitter));

        subscriptions.compute(channel, (id, emitters) -> {
            if (emitters == null) {
                emitters = ConcurrentHashMap.newKeySet();
            }
            emitters.add(emitter);
            return emitters;
        });
        send(channel, emitter, SseEmitter.event().comment("connected"));
        return emitter;
    }

    @TransactionalEventListener
    public void roomChanged(RoomChanged change) {
        notifyChannel(new Channel(change.roomId(), false));
        notifyChannel(new Channel(change.buyerId(), true));
        notifyChannel(new Channel(change.sellerId(), true));
    }

    private void notifyChannel(Channel channel) {
        var emitters = subscriptions.get(channel);
        if (emitters != null) {
            emitters.forEach(emitter -> send(channel, emitter, SseEmitter.event().data("changed")));
        }
    }

    @Scheduled(fixedDelay = 20_000)
    public void keepConnectionsAlive() {
        subscriptions.forEach((channel, emitters) -> emitters.forEach(
                emitter -> send(channel, emitter, SseEmitter.event().comment("keepalive"))));
    }

    private void send(Channel channel, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException ex) {
            remove(channel, emitter);
        }
    }

    private void remove(Channel channel, SseEmitter emitter) {
        subscriptions.computeIfPresent(channel, (id, emitters) -> {
            emitters.remove(emitter);
            return emitters.isEmpty() ? null : emitters;
        });
    }
}
