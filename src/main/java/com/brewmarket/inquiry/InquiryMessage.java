package com.brewmarket.inquiry;

import jakarta.persistence.*;

@Entity
@Table(name = "inquiry_messages")
public class InquiryMessage {
    public enum Kind { TEXT, SYSTEM }

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Kind kind;

    protected InquiryMessage() {}

    InquiryMessage(InquiryRoom room, long senderId, String content) {
        this(room, senderId, content, Kind.TEXT);
    }

    InquiryMessage(InquiryRoom room, long senderId, String content, Kind kind) {
        this.room = room;
        this.senderId = senderId;
        this.content = content;
        this.kind = kind;
    }
}
