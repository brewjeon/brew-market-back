package com.brewmarket.inquiry;

import jakarta.persistence.*;

@Entity
@Table(name = "inquiry_messages")
public class InquiryMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private InquiryRoom room;

    @Column(name = "sender_id", nullable = false)
    private long senderId;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    protected InquiryMessage() {}

    InquiryMessage(InquiryRoom room, long senderId, String content) {
        this.room = room;
        this.senderId = senderId;
        this.content = content;
    }
}
