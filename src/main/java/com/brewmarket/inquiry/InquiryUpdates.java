package com.brewmarket.inquiry;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
@RequiredArgsConstructor
public class InquiryUpdates {
    record RoomChanged(long roomId, long buyerId, long sellerId) {}
    record MeetingChanged(long roomId) {}
    private record ReservationChanged(long postId, List<RoomChanged> rooms) {}
    private enum ChannelType { ROOM, ROOM_LIST, POST }
    private record Channel(long id, ChannelType type) {}

    private final ConcurrentHashMap<Channel, Set<SseEmitter>> subscriptions = new ConcurrentHashMap<>();
    private final InquiryRoomRepository rooms;
    private final ApplicationEventPublisher events;

    public void publishReservationChange(long postId) {
        var affectedRooms = rooms.findRoomParticipantIdsByPostId(postId).stream()
                .map(room -> new RoomChanged(room.getRoomId(), room.getBuyerId(), room.getSellerId()))
                .toList();
        events.publishEvent(new ReservationChanged(postId, affectedRooms));
    }

    SseEmitter subscribe(long roomId) {
        return subscribe(new Channel(roomId, ChannelType.ROOM));
    }

    SseEmitter subscribeList(long userId) {
        return subscribe(new Channel(userId, ChannelType.ROOM_LIST));
    }

    public SseEmitter subscribePost(long postId) {
        return subscribe(new Channel(postId, ChannelType.POST));
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
        notifyChannel(new Channel(change.roomId(), ChannelType.ROOM));
        notifyChannel(new Channel(change.buyerId(), ChannelType.ROOM_LIST));
        notifyChannel(new Channel(change.sellerId(), ChannelType.ROOM_LIST));
    }

    @TransactionalEventListener
    public void meetingChanged(MeetingChanged change) {
        notifyChannel(new Channel(change.roomId(), ChannelType.ROOM));
    }

    @TransactionalEventListener
    public void reservationChanged(ReservationChanged change) {
        notifyChannel(new Channel(change.postId(), ChannelType.POST));
        Set<Long> participantIds = new HashSet<>();
        for (var room : change.rooms()) {
            notifyChannel(new Channel(room.roomId(), ChannelType.ROOM));
            participantIds.add(room.buyerId());
            participantIds.add(room.sellerId());
        }
        participantIds.forEach(userId -> notifyChannel(new Channel(userId, ChannelType.ROOM_LIST)));
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
