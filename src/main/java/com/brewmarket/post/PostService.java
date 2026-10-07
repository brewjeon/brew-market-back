package com.brewmarket.post;

import com.brewmarket.user.UserRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PostService {

    private final UserRepository userRepository;
    private final PostRepository postRepository;

    @Transactional
    public Long create(
            Long sellerId,
            String title,
            String description,
            Long price
    ) {
        Post post = new Post(sellerId, title, description, price);

        if (!userRepository.existsById(sellerId)) {
            throw new IllegalArgumentException("존재하지 않는 판매자입니다.");
        }

        Post savedPost = postRepository.saveAndFlush(post);

        return savedPost.getId();
    }
}
