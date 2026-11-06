package com.brewmarket.post;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "image_deletion_jobs")
public class ImageDeletionJob {
    @Id
    @Column(name = "image_key", length = 100)
    private String imageKey;

    protected ImageDeletionJob() {}

    public ImageDeletionJob(String imageKey) { this.imageKey = imageKey; }
    public String getImageKey() { return imageKey; }
}
