package com.brewmarket.inquiry;

import com.brewmarket.post.Post;
import com.brewmarket.post.PostRepository;
import com.brewmarket.post.PostReservationConflictException;
import com.brewmarket.post.PostStatus;
import java.util.List;
import java.util.HashMap;
import java.util.Objects;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InquiryService {
    private final PostRepository postRepository;
    private final InquiryRoomRepository roomRepository;
    private final InquiryMessageRepository messageRepository;
    private final ApplicationEventPublisher events;

    public InquiryService(
            PostRepository postRepository,
            InquiryRoomRepository roomRepository,
            InquiryMessageRepository messageRepository,
            ApplicationEventPublisher events
    ) {
        this.postRepository = postRepository;
        this.roomRepository = roomRepository;
        this.messageRepository = messageRepository;
        this.events = events;
    }

    public record Room(long id, Long postId, String title, String lastMessage,
                       long lastMessageId, boolean unread, long unreadCount) {}
    public record Message(long id, long senderId, String content) {}
    public record RoomPage(List<Room> rooms, Long nextBefore, long unreadRoomCount) {}
    public enum ReservationState { AVAILABLE, RESERVED_HERE, RESERVED_ELSEWHERE, LEGACY_RESERVED, COMPLETED_HERE, COMPLETED, UNAVAILABLE }
    public enum ReservationAction { CONFIRM, CANCEL, COMPLETE }
    public record Reservation(ReservationState state, Long version) {}

    @Transactional(readOnly = true)
    public Reservation reservation(long roomId, long userId) {
        InquiryRoom room = requireParticipant(roomId, userId, false);
        if (room.postId == null) return new Reservation(ReservationState.UNAVAILABLE, null);
        return postRepository.findByIdAndDeletedAtIsNull(room.postId)
                .map(post -> toReservation(post, room, userId))
                .orElseGet(() -> new Reservation(ReservationState.UNAVAILABLE, null));
    }

    @Transactional
    public void changeReservation(long roomId, long userId, ReservationAction action, long version) {
        InquiryRoom room = requireParticipant(roomId, userId, false);
        if (room.sellerId != userId) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "판매자만 예약을 변경할 수 있습니다.");
        }
        if (room.postId == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);

        // 서로 다른 대화에서 들어온 요청도 동일한 게시글 행에서 순서를 결정한다.
        Post post = postRepository.findByIdForUpdate(room.postId)
                .filter(active -> active.getDeletedAt() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (post.getSellerId() != userId) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if (!Objects.equals(post.getVersion(), version)) {
            throw new PostReservationConflictException("게시글 상태가 변경되었습니다. 확인한 뒤 다시 시도해 주세요.");
        }
        switch (action) {
            case CONFIRM -> post.reserveFor(roomId);
            case CANCEL -> post.cancelReservation(roomId);
            case COMPLETE -> post.completeReservation(roomId);
        }
        events.publishEvent(new InquiryUpdates.ReservationChanged(roomRepository.findRoomIdsByPostId(room.postId)));
    }

    private Reservation toReservation(Post post, InquiryRoom room, long userId) {
        ReservationState state;
        if (post.getStatus() == PostStatus.FOR_SALE) {
            state = ReservationState.AVAILABLE;
        } else if (post.getStatus() == PostStatus.COMPLETED) {
            state = post.isReservedFor(room.id) ? ReservationState.COMPLETED_HERE : ReservationState.COMPLETED;
        } else if (post.isReservedFor(room.id)) {
            state = ReservationState.RESERVED_HERE;
        } else {
            state = post.hasSelectedReservation() ? ReservationState.RESERVED_ELSEWHERE : ReservationState.LEGACY_RESERVED;
        }
        return new Reservation(state, post.getSellerId() == userId ? post.getVersion() : null);
    }

    @Transactional
    public long inquire(long postId, long buyerId, String content) {
        validate(content);

        // 같은 게시글의 첫 문의는 방 조회와 생성이 겹치지 않도록 순서대로 처리한다.
        Post post = postRepository.findByIdForUpdate(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (post.getDeletedAt() != null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if (post.getSellerId() == buyerId) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "내 게시글에는 문의할 수 없습니다.");
        }

        InquiryRoom room = roomRepository.findByPostIdAndBuyerId(postId, buyerId)
                .orElseGet(() -> roomRepository.save(new InquiryRoom(post, buyerId)));
        messageRepository.save(new InquiryMessage(room, buyerId, content));
        publishRoomChanged(room);
        return room.id;
    }

    @Transactional
    public void send(long roomId, long userId, String content) {
        validate(content);

        // 방 잠금을 획득한 뒤 메시지 번호를 생성해 읽음 위치와 저장 순서를 맞춘다.
        InquiryRoom room = requireParticipant(roomId, userId, true);
        messageRepository.save(new InquiryMessage(room, userId, content));
        publishRoomChanged(room);
    }

    @Transactional(readOnly = true)
    public Long findRoom(long postId, long buyerId) {
        return roomRepository.findRoomId(postId, buyerId).orElse(null);
    }

    @Transactional(readOnly = true)
    public RoomPage rooms(long userId, long before) {
        return rooms(userId, before, false);
    }

    @Transactional(readOnly = true)
    public RoomPage rooms(long userId, long before, boolean unreadOnly) {
        long unreadRoomCount = messageRepository.countUnreadRooms(userId);
        var rows = messageRepository.findLatestMessages(userId, before, unreadOnly, PageRequest.of(0, 11));
        if (rows.isEmpty()) {
            return new RoomPage(List.of(), null, unreadRoomCount);
        }

        var page = rows.subList(0, Math.min(10, rows.size()));
        List<Long> roomIds = page.stream().map(InquiryMessageRepository.LatestMessage::getRoomId).toList();
        var unreadCounts = new HashMap<Long, Long>();
        messageRepository.findUnreadCounts(roomIds, userId)
                .forEach(row -> unreadCounts.put(row.getRoomId(), row.getCount()));

        List<Room> rooms = page.stream().map(row -> {
            long count = unreadCounts.getOrDefault(row.getRoomId(), 0L);
            return new Room(row.getRoomId(), row.getPostId(), row.getTitle(), row.getContent(),
                    row.getMessageId(), count > 0, count);
        }).toList();
        return new RoomPage(rooms, rows.size() > 10 ? rooms.getLast().lastMessageId() : null, unreadRoomCount);
    }

    @Transactional(readOnly = true)
    public List<Message> messages(long roomId, long userId) {
        requireParticipant(roomId, userId, false);
        return messageRepository.findByRoom_IdOrderById(roomId);
    }

    @Transactional(readOnly = true)
    public void checkParticipant(long roomId, long userId) {
        requireParticipant(roomId, userId, false);
    }

    @Transactional(readOnly = true)
    public long peerLastReadId(long roomId, long userId) {
        InquiryRoom room = requireParticipant(roomId, userId, false);
        return room.buyerId == userId ? room.sellerLastReadId : room.buyerLastReadId;
    }

    @Transactional
    public void markRead(long roomId, long userId, long messageId) {
        InquiryRoom room = requireParticipant(roomId, userId, true);
        if (!messageRepository.existsByIdAndRoom_Id(messageId, roomId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "해당 대화방의 메시지 번호가 필요합니다.");
        }
        long previousReadId = room.buyerId == userId ? room.buyerLastReadId : room.sellerLastReadId;
        if (messageId > previousReadId) {
            room.markRead(userId, messageId);
            publishRoomChanged(room);
        }
    }

    private void publishRoomChanged(InquiryRoom room) {
        events.publishEvent(new InquiryUpdates.RoomChanged(room.id, room.buyerId, room.sellerId));
    }

    private InquiryRoom requireParticipant(long roomId, long userId, boolean forUpdate) {
        var room = forUpdate
                ? roomRepository.findParticipantRoomForUpdate(roomId, userId)
                : roomRepository.findParticipantRoom(roomId, userId);
        return room.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private void validate(String content) {
        if (content == null || content.length() > 2000
                || content.codePoints().allMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "메시지는 공백 외 문자를 포함해 2000자 이하로 입력해 주세요.");
        }
    }
}
