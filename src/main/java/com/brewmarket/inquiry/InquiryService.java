package com.brewmarket.inquiry;

import com.brewmarket.post.Post;
import com.brewmarket.post.PostRepository;
import java.util.List;
import java.util.Set;
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

    public InquiryService(
            PostRepository postRepository,
            InquiryRoomRepository roomRepository,
            InquiryMessageRepository messageRepository
    ) {
        this.postRepository = postRepository;
        this.roomRepository = roomRepository;
        this.messageRepository = messageRepository;
    }

    public record Room(long id, Long postId, String title, String lastMessage,
                       long lastMessageId, boolean unread) {}
    public record Message(long id, long senderId, String content) {}
    public record RoomPage(List<Room> rooms, Long nextBefore) {}

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
        return room.id;
    }

    @Transactional
    public void send(long roomId, long userId, String content) {
        validate(content);

        // 방 잠금을 획득한 뒤 메시지 번호를 생성해 읽음 위치와 저장 순서를 맞춘다.
        InquiryRoom room = requireParticipant(roomId, userId, true);
        messageRepository.save(new InquiryMessage(room, userId, content));
    }

    @Transactional(readOnly = true)
    public Long findRoom(long postId, long buyerId) {
        return roomRepository.findRoomId(postId, buyerId).orElse(null);
    }

    @Transactional(readOnly = true)
    public RoomPage rooms(long userId, long before) {
        var rows = messageRepository.findLatestMessages(userId, before, PageRequest.of(0, 11));
        if (rows.isEmpty()) {
            return new RoomPage(List.of(), null);
        }

        var page = rows.subList(0, Math.min(10, rows.size()));
        List<Long> roomIds = page.stream().map(InquiryMessageRepository.LatestMessage::getRoomId).toList();
        Set<Long> unreadRoomIds = messageRepository.findUnreadRoomIds(roomIds, userId);

        List<Room> rooms = page.stream().map(row -> new Room(
                row.getRoomId(),
                row.getPostId(),
                row.getTitle(),
                row.getContent(),
                row.getMessageId(),
                unreadRoomIds.contains(row.getRoomId())
        )).toList();
        return new RoomPage(rooms, rows.size() > 10 ? rooms.getLast().lastMessageId() : null);
    }

    @Transactional(readOnly = true)
    public List<Message> messages(long roomId, long userId) {
        requireParticipant(roomId, userId, false);
        return messageRepository.findByRoom_IdOrderById(roomId);
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
        room.markRead(userId, messageId);
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