package com.brewmarket.inquiry;

import com.brewmarket.post.Post;
import jakarta.persistence.*;

@Entity
@Table(name = "inquiry_rooms")
public class InquiryRoom {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "post_id")
    Long postId;

    @Column(name = "buyer_id", nullable = false)
    long buyerId;

    @Column(name = "seller_id", nullable = false)
    long sellerId;

    @Column(name = "post_title", nullable = false, length = 100)
    String postTitle;

    @Column(name = "buyer_last_read_id", nullable = false)
    long buyerLastReadId;

    @Column(name = "seller_last_read_id", nullable = false)
    long sellerLastReadId;

    protected InquiryRoom() {}

    InquiryRoom(Post post, long buyerId) {
        this.postId = post.getId();
        this.buyerId = buyerId;
        this.sellerId = post.getSellerId();
        this.postTitle = post.getTitle();
    }

    void markRead(long userId, long messageId) {
        if (buyerId == userId) {
            buyerLastReadId = Math.max(buyerLastReadId, messageId);
        } else {
            sellerLastReadId = Math.max(sellerLastReadId, messageId);
        }
    }
}
