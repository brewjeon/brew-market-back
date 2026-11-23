package com.brewmarket.inquiry;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface InquiryRoomRepository extends JpaRepository<InquiryRoom, Long> {
    String PARTICIPANT_QUERY = """
            select r from InquiryRoom r
            where r.id = :roomId and (r.buyerId = :userId or r.sellerId = :userId)
            """;

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<InquiryRoom> findByPostIdAndBuyerId(long postId, long buyerId);

    @Query("select r.id from InquiryRoom r where r.postId = :postId and r.buyerId = :buyerId")
    Optional<Long> findRoomId(@Param("postId") long postId, @Param("buyerId") long buyerId);

    interface RoomParticipantIds {
        long getRoomId();
        long getBuyerId();
        long getSellerId();
    }

    @Query("""
            select r.id as roomId, r.buyerId as buyerId, r.sellerId as sellerId
            from InquiryRoom r where r.postId = :postId
            """)
    List<RoomParticipantIds> findRoomParticipantIdsByPostId(@Param("postId") long postId);

    @Query(PARTICIPANT_QUERY)
    Optional<InquiryRoom> findParticipantRoom(@Param("roomId") long roomId, @Param("userId") long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(PARTICIPANT_QUERY)
    Optional<InquiryRoom> findParticipantRoomForUpdate(@Param("roomId") long roomId, @Param("userId") long userId);
}
