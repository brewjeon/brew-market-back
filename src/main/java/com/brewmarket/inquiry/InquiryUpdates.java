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
    record RoomChanged(long roomId) {}

    private final ConcurrentHashMap<Long, Set<SseEmitter>> subscriptions = new ConcurrentHashMap<>();

    SseEmitter subscribe(long roomId) {
        var emitter = new SseEmitter(300_000L);
        emitter.onCompletion(() -> remove(roomId, emitter));
        emitter.onTimeout(emitter::complete);
        emitter.onError(error -> remove(roomId, emitter));

        subscriptions.compute(roomId, (id, emitters) -> {
            if (emitters == null) {
                emitters = ConcurrentHashMap.newKeySet();
            }
            emitters.add(emitter);
            return emitters;
        });
        send(roomId, emitter, SseEmitter.event().comment("connected"));
        return emitter;
    }

    @TransactionalEventListener
    public void roomChanged(RoomChanged change) {
        var emitters = subscriptions.get(change.roomId());
        if (emitters != null) {
            emitters.forEach(emitter -> send(change.roomId(), emitter, SseEmitter.event().data("changed")));
        }
    }

    @Scheduled(fixedDelay = 20_000)
    public void keepConnectionsAlive() {
        subscriptions.forEach((roomId, emitters) -> emitters.forEach(
                emitter -> send(roomId, emitter, SseEmitter.event().comment("keepalive"))));
    }

    private void send(long roomId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException ex) {
            remove(roomId, emitter);
        }
    }

    private void remove(long roomId, SseEmitter emitter) {
        subscriptions.computeIfPresent(roomId, (id, emitters) -> {
            emitters.remove(emitter);
            return emitters.isEmpty() ? null : emitters;
        });
    }
}
