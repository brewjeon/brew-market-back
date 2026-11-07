package com.brewmarket.inquiry;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InquiryService {
    private final JdbcTemplate db;

    public InquiryService(JdbcTemplate db) {
        this.db = db;
    }

    public record Room(long id, long postId, String title, String lastMessage, long lastMessageId) {}
    public record Message(long id, long senderId, String content) {}
    public record RoomPage(List<Room> rooms, Long nextBefore) {}

    @Transactional
    public long inquire(long postId, long buyerId, String content) {
        validate(content);
        var sellers = db.query("""
                SELECT seller_id FROM brew_market.posts WHERE id = ? AND deleted_at IS NULL
                FOR SHARE
                """, (row, i) -> row.getLong("seller_id"), postId);
        if (sellers.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        long sellerId = sellers.getFirst();
        if (sellerId == buyerId) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "내 게시글에는 문의할 수 없습니다.");
        }

        // 고유 제약을 기준으로 동시 문의도 같은 방에 연결한다.
        long roomId = db.queryForObject("""
                INSERT INTO brew_market.inquiry_rooms (post_id, buyer_id, seller_id)
                VALUES (?, ?, ?)
                ON CONFLICT (post_id, buyer_id) DO UPDATE SET buyer_id = EXCLUDED.buyer_id
                RETURNING id
                """, Long.class, postId, buyerId, sellerId);
        append(roomId, buyerId, content);
        return roomId;
    }

    @Transactional
    public void send(long roomId, long userId, String content) {
        validate(content);
        requireParticipant(roomId, userId);
        append(roomId, userId, content);
    }

    @Transactional(readOnly = true)
    public Long findRoom(long postId, long buyerId) {
        return db.query("""
                SELECT id FROM brew_market.inquiry_rooms
                WHERE post_id = ? AND buyer_id = ?
                """, (row, i) -> row.getLong("id"), postId, buyerId)
                .stream()
                .findFirst()
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public RoomPage rooms(long userId, long before) {
        List<Room> rows = db.query("""
                SELECT r.id, r.post_id, p.title, m.content, m.id AS message_id
                FROM brew_market.inquiry_rooms r
                JOIN brew_market.posts p ON p.id = r.post_id
                JOIN LATERAL (
                    SELECT id, content FROM brew_market.inquiry_messages
                    WHERE room_id = r.id ORDER BY id DESC LIMIT 1
                ) m ON true
                WHERE (r.buyer_id = ? OR r.seller_id = ?) AND m.id < ?
                ORDER BY m.id DESC LIMIT 11
                """, (row, i) -> new Room(row.getLong("id"), row.getLong("post_id"),
                row.getString("title"), row.getString("content"), row.getLong("message_id")),
                userId, userId, before);
        List<Room> page = rows.subList(0, Math.min(10, rows.size()));
        return new RoomPage(page, rows.size() > 10 ? page.getLast().lastMessageId() : null);
    }

    @Transactional(readOnly = true)
    public List<Message> messages(long roomId, long userId) {
        requireParticipant(roomId, userId);
        return db.query("""
                SELECT id, sender_id, content FROM brew_market.inquiry_messages
                WHERE room_id = ? ORDER BY id
                """, (row, i) -> new Message(row.getLong("id"), row.getLong("sender_id"),
                row.getString("content")), roomId);
    }

    private void requireParticipant(long roomId, long userId) {
        boolean allowed = Boolean.TRUE.equals(db.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM brew_market.inquiry_rooms
                    WHERE id = ? AND (buyer_id = ? OR seller_id = ?)
                )
                """, Boolean.class, roomId, userId, userId));
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    private void append(long roomId, long userId, String content) {
        db.update("""
                INSERT INTO brew_market.inquiry_messages (room_id, sender_id, content)
                VALUES (?, ?, ?)
                """, roomId, userId, content);
    }

    private void validate(String content) {
        if (content == null || content.length() > 2000
                || content.codePoints().allMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "메시지는 공백 외 문자를 포함해 2000자 이하로 입력해 주세요.");
        }
    }
}
