package com.brewmarket.post;

import jakarta.persistence.*;

@Entity
@Table(name = "post_draft_images")
public class PostDraftImage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "draft_id", nullable = false)
    private Long draftId;
    @Column(name = "image_key", nullable = false, length = 100)
    private String imageKey;
    @Column(nullable = false)
    private int position;
    protected PostDraftImage() {}
    public PostDraftImage(Long draftId, String imageKey, int position) {
        this.draftId = draftId;
        this.imageKey = imageKey;
        this.position = position;
    }
    public String getImageKey() { return imageKey; }
    public int getPosition() { return position; }
}
