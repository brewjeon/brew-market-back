package com.brewmarket.post;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostDraftImageRepository extends JpaRepository<PostDraftImage, Long> {
    List<PostDraftImage> findByDraftIdOrderByPositionAsc(Long draftId);
    void deleteByDraftId(Long draftId);
}
