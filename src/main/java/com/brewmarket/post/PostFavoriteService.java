package com.brewmarket.post;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PostFavoriteService {
    private final PostFavoriteRepository favorites;
    private final PostRepository posts;

    public PostFavoriteService(PostFavoriteRepository favorites, PostRepository posts) {
        this.favorites = favorites;
        this.posts = posts;
    }

    public record FavoritePage(List<PostSummaryResponse> items, boolean hasNext, Long nextId) {}
    public record FavoriteState(boolean favorite) {}
    @Transactional
    public void add(long userId, long postId) {
        if (postId <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        int inserted = favorites.addIfAbsent(userId, postId);
        if (inserted == 0 && !activePostExists(postId)) {
            throw new PostNotFoundException();
        }
    }

    @Transactional
    public void remove(long userId, long postId) {
        favorites.remove(userId, postId);
    }

    @Transactional(readOnly = true)
    public FavoriteState state(long userId, long postId) {
        if (!activePostExists(postId)) throw new PostNotFoundException();
        return new FavoriteState(favorites.existsByUserIdAndPost_Id(userId, postId));
    }

    @Transactional(readOnly = true)
    public FavoritePage list(long userId, Long beforeId) {
        if (beforeId != null && beforeId <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        List<PostFavorite> rows = favorites.findPage(userId,
                beforeId == null ? Long.MAX_VALUE : beforeId, PageRequest.of(0, 21));
        boolean hasNext = rows.size() > 20;
        List<PostFavorite> visible = rows.subList(0, Math.min(rows.size(), 20));
        return new FavoritePage(visible.stream().map(f -> PostSummaryResponse.from(f.getPost())).toList(),
                hasNext, hasNext ? visible.getLast().getId() : null);
    }

    private boolean activePostExists(long postId) {
        return posts.findByIdAndDeletedAtIsNull(postId).isPresent();
    }
}
