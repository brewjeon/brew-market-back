package com.brewmarket.post;

import jakarta.persistence.*;

@Entity
@Table(name = "post_images")
public class PostImage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "post_id", nullable = false)
    private Long postId;
    @Column(name = "image_key", nullable = false, length = 100)
    private String imageKey;
    @Column(nullable = false)
    private int position;

    protected PostImage() {}
    public PostImage(Long postId, String imageKey, int position) {
        this.postId = postId;
        this.imageKey = imageKey;
        this.position = position;
    }
    public Long getId() { return id; }
    public String getImageKey() { return imageKey; }
    public int getPosition() { return position; }
}
