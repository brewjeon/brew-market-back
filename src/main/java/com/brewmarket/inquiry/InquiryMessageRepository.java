package com.brewmarket.inquiry;

import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface InquiryMessageRepository extends JpaRepository<InquiryMessage, Long> {
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
            order by m.id desc
            """)
    List<LatestMessage> findLatestMessages(
            @Param("userId") long userId,
            @Param("before") long before,
            Pageable pageable
    );

    @Query("""
            select distinct r.id from InquiryMessage m join m.room r
            where r.id in :roomIds and m.senderId <> :userId
              and m.id > case when r.buyerId = :userId
                              then r.buyerLastReadId else r.sellerLastReadId end
            """)
    Set<Long> findUnreadRoomIds(@Param("roomIds") List<Long> roomIds, @Param("userId") long userId);

    List<InquiryService.Message> findByRoom_IdOrderById(long roomId);

    boolean existsByIdAndRoom_Id(long messageId, long roomId);
}
