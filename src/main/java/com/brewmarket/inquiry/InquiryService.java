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

    public record Room(long id, Long postId, String title, String lastMessage,
                       long lastMessageId, boolean unread) {}
    public record Message(long id, long senderId, String content) {}
    public record RoomPage(List<Room> rooms, Long nextBefore) {}
    private record Listing(long sellerId, String title) {}

    @Transactional
    public long inquire(long postId, long buyerId, String content) {
        validate(content);
        var listings = db.query("""
                SELECT seller_id, title FROM brew_market.posts WHERE id = ? AND deleted_at IS NULL
                FOR SHARE
                """, (row, i) -> new Listing(row.getLong("seller_id"), row.getString("title")), postId);
        if (listings.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        Listing listing = listings.getFirst();
        long sellerId = listing.sellerId();
        if (sellerId == buyerId) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "내 게시글에는 문의할 수 없습니다.");
        }

        // 고유 제약을 기준으로 동시 문의도 같은 방에 연결한다.
        long roomId = db.queryForObject("""
                INSERT INTO brew_market.inquiry_rooms (post_id, buyer_id, seller_id, post_title)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (post_id, buyer_id) DO UPDATE SET buyer_id = EXCLUDED.buyer_id
                RETURNING id
                """, Long.class, postId, buyerId, sellerId, listing.title());
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
                SELECT r.id, r.post_id, COALESCE(p.title, r.post_title) AS title,
                       m.content, m.id AS message_id,
                       EXISTS (
                           SELECT 1 FROM brew_market.inquiry_messages unread
                           WHERE unread.room_id = r.id
                             AND unread.sender_id <> ?
                             AND unread.id > CASE WHEN r.buyer_id = ?
                                 THEN r.buyer_last_read_id ELSE r.seller_last_read_id END
                       ) AS has_unread
                FROM brew_market.inquiry_rooms r
                LEFT JOIN brew_market.posts p ON p.id = r.post_id
                JOIN LATERAL (
                    SELECT id, content FROM brew_market.inquiry_messages
                    WHERE room_id = r.id ORDER BY id DESC LIMIT 1
                ) m ON true
                WHERE (r.buyer_id = ? OR r.seller_id = ?) AND m.id < ?
                ORDER BY m.id DESC LIMIT 11
                """, (row, i) -> new Room(row.getLong("id"), row.getObject("post_id", Long.class),
                row.getString("title"), row.getString("content"), row.getLong("message_id"),
                row.getBoolean("has_unread")), userId, userId, userId, userId, before);
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

    @Transactional(readOnly = true)
    public long peerLastReadId(long roomId, long userId) {
        return db.query("""
                SELECT CASE WHEN buyer_id = ?
                    THEN seller_last_read_id ELSE buyer_last_read_id END AS peer_last_read_id
                FROM brew_market.inquiry_rooms
                WHERE id = ? AND (buyer_id = ? OR seller_id = ?)
                """, (row, i) -> row.getLong("peer_last_read_id"), userId, roomId, userId, userId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @Transactional
    public void markRead(long roomId, long userId, long messageId) {
        requireParticipant(roomId, userId);

        int updated = db.update("""
                UPDATE brew_market.inquiry_rooms r
                SET buyer_last_read_id = CASE WHEN r.buyer_id = ?
                        THEN GREATEST(r.buyer_last_read_id, ?) ELSE r.buyer_last_read_id END,
                    seller_last_read_id = CASE WHEN r.seller_id = ?
                        THEN GREATEST(r.seller_last_read_id, ?) ELSE r.seller_last_read_id END
                WHERE r.id = ? AND EXISTS (
                    SELECT 1 FROM brew_market.inquiry_messages m
                    WHERE m.room_id = r.id AND m.id = ?
                )
                """, userId, messageId, userId, messageId, roomId, messageId);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "해당 대화방의 메시지 번호가 필요합니다.");
        }
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
        // 같은 방의 메시지가 번호 순서대로 확정되도록 방을 먼저 잠근다.
        db.queryForObject("SELECT id FROM brew_market.inquiry_rooms WHERE id = ? FOR UPDATE",
                Long.class, roomId);
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
