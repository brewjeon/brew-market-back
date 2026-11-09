package com.brewmarket.inquiry;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface InquiryMessageRepository extends JpaRepository<InquiryMessage, Long> {
    String UNREAD = """
            m.senderId <> :userId
              and m.id > case when r.buyerId = :userId
                              then r.buyerLastReadId else r.sellerLastReadId end
            """;

    interface UnreadCount {
        long getRoomId();
        long getCount();
    }

    interface LatestMessage {
        long getRoomId();
        Long getPostId();
        String getTitle();
        String getContent();
        long getMessageId();
    }

    @Query("""
            select r.id as roomId, p.id as postId, coalesce(p.title, r.postTitle) as title,
                   m.content as content, m.id as messageId
            from InquiryMessage m join m.room r left join Post p on p.id = r.postId
            where (r.buyerId = :userId or r.sellerId = :userId)
              and m.id < :before
              and m.id = (select max(latest.id) from InquiryMessage latest where latest.room = r)
              and (:unreadOnly = false or exists (
                  select unread.id from InquiryMessage unread
                  where unread.room = r and unread.senderId <> :userId
                    and unread.id > case when r.buyerId = :userId
                                         then r.buyerLastReadId else r.sellerLastReadId end
              ))
            order by m.id desc
            """)
    List<LatestMessage> findLatestMessages(
            @Param("userId") long userId,
            @Param("before") long before,
            @Param("unreadOnly") boolean unreadOnly,
            Pageable pageable
    );

    @Query("""
            select r.id as roomId, count(m) as count from InquiryMessage m join m.room r
            where r.id in :roomIds and
            """ + UNREAD + " group by r.id")
    List<UnreadCount> findUnreadCounts(@Param("roomIds") List<Long> roomIds, @Param("userId") long userId);

    @Query("""
            select count(distinct r.id) from InquiryMessage m join m.room r
            where (r.buyerId = :userId or r.sellerId = :userId) and
            """ + UNREAD)
    long countUnreadRooms(@Param("userId") long userId);

    List<InquiryService.Message> findByRoom_IdOrderById(long roomId);

    boolean existsByIdAndRoom_Id(long messageId, long roomId);
}
