package com.brewmarket.post;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "posts")
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", nullable = false, updatable = false)
    private Long sellerId;

    @Column(name = "neighborhood_id")
    private Long neighborhoodId;

    public Long getNeighborhoodId() {
        return neighborhoodId;
    }

    void selectNeighborhood(Long neighborhoodId) {
        requireEditable();
        this.neighborhoodId = neighborhoodId;
    }

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(nullable = false)
    private Long price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PostStatus status;

    @Column(
            name = "created_at",
            nullable = false,
            insertable = false,
            updatable = false
    )
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    // 동시에 읽은 예전 게시글로 수정·삭제 상태를 덮어쓰지 못하게 합니다.
    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "reservation_room_id")
    private Long reservationRoomId;

    @Column(name = "meeting_at")
    private Instant meetingAt;

    @Column(name = "meeting_place", length = 200)
    private String meetingPlace;

    @Column(name = "image_key", length = 100)
    private String imageKey;

    public String getImageKey() {
        return imageKey;
    }

    public void changeImage(String key) {
        requireEditable();
        imageKey = key;
    }

    public void changeStatus(PostStatus nextStatus) {
        if (deletedAt != null) throw new PostNotFoundException();
        if (nextStatus == null) throw new IllegalArgumentException("거래 상태는 필수입니다.");
        // 같은 상태로 재요청하면 변경 없이 성공한다.
        if (status == nextStatus) return;
        if (status == PostStatus.COMPLETED) throw new PostStatusConflictException();
        status = nextStatus;
        if (nextStatus != PostStatus.RESERVED) {
            meetingAt = null;
            meetingPlace = null;
        }
    }

    public Long getVersion() {
        return version;
    }

    public boolean hasSelectedReservation() {
        return reservationRoomId != null;
    }

    Long getReservationRoomId() {
        return reservationRoomId;
    }

    public boolean isReservedFor(long roomId) {
        return Objects.equals(reservationRoomId, roomId);
    }

    public Instant getMeetingAt() {
        return meetingAt;
    }

    public String getMeetingPlace() {
        return meetingPlace;
    }

    public void reserveFor(long roomId, Instant meetingAt, String meetingPlace) {
        if (status != PostStatus.FOR_SALE) {
            throw new PostReservationConflictException("판매 중인 글만 예약할 수 있습니다.");
        }
        changeStatus(PostStatus.RESERVED);
        reservationRoomId = roomId;
        this.meetingAt = meetingAt;
        this.meetingPlace = meetingPlace;
    }

    public boolean rescheduleReservation(long roomId, Instant meetingAt, String meetingPlace) {
        requireActiveReservation(roomId);
        if (Objects.equals(this.meetingAt, meetingAt) && Objects.equals(this.meetingPlace, meetingPlace)) {
            return false;
        }
        this.meetingAt = meetingAt;
        this.meetingPlace = meetingPlace;
        return true;
    }

    public void cancelReservation(long roomId) {
        requireActiveReservation(roomId);
        changeStatus(PostStatus.FOR_SALE);
        reservationRoomId = null;
    }

    public void completeReservation(long roomId) {
        requireActiveReservation(roomId);
        changeStatus(PostStatus.COMPLETED);
    }

    private void requireActiveReservation(long roomId) {
        if (status != PostStatus.RESERVED || !isReservedFor(roomId)) {
            throw new PostReservationConflictException("이 대화의 진행 중인 예약을 찾을 수 없습니다.");
        }
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    protected Post() {
    }

    public Post(
            Long sellerId,
            String title,
            String description,
            Long price
    ) {
        if (sellerId == null || sellerId <= 0) {
            throw new IllegalArgumentException("판매자 ID는 양수여야 합니다.");
        }

        validateContent(title, description, price);
        this.sellerId = sellerId;
        this.title = title;
        this.description = description;
        this.price = price;
        this.status = PostStatus.FOR_SALE;
    }

    public void updateContent(String title, String description, Long price) {
        requireEditable();
        // 모든 값을 검증한 뒤 변경해 잘못된 입력으로 일부 필드만 바뀌지 않게 합니다.
        validateContent(title, description, price);
        this.title = title;
        this.description = description;
        this.price = price;
    }

    void requireEditable() {
        if (deletedAt != null) throw new PostNotFoundException();
        if (status == PostStatus.COMPLETED) {
            throw new PostStatusConflictException("거래 완료된 게시글은 수정할 수 없습니다.");
        }
    }

    private static void validateContent(String title, String description, Long price) {
        if (title == null || title.isBlank() || title.length() > 100) {
            throw new IllegalArgumentException("제목은 공백이 아닌 100자 이하여야 합니다.");
        }

        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("설명은 필수입니다.");
        }

        if (price == null || price < 0) {
            throw new IllegalArgumentException("가격은 0 이상이어야 합니다.");
        }
    }

    public Long getId() {
        return id;
    }

    public Long getSellerId() {
        return sellerId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Long getPrice() {
        return price;
    }

    public PostStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
