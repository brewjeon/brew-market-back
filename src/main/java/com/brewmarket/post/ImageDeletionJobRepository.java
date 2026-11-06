package com.brewmarket.post;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImageDeletionJobRepository extends JpaRepository<ImageDeletionJob, String> {
    List<ImageDeletionJob> findTop100ByOrderByImageKeyAsc();
}
