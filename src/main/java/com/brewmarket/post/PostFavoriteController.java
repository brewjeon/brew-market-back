package com.brewmarket.post;

import com.brewmarket.auth.BrewOidcUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/favorites")
public class PostFavoriteController {
    private final PostFavoriteService service;

    public PostFavoriteController(PostFavoriteService service) {
        this.service = service;
    }

    @GetMapping
    public PostFavoriteService.FavoritePage list(@AuthenticationPrincipal BrewOidcUser user,
                                                 @RequestParam(required = false) Long beforeId) {
        return service.list(userId(user), beforeId);
    }

    @GetMapping("/{postId}")
    public PostFavoriteService.FavoriteState state(@AuthenticationPrincipal BrewOidcUser user,
                                                   @PathVariable long postId) {
        return service.state(userId(user), postId);
    }

    @PostMapping("/{postId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void add(@AuthenticationPrincipal BrewOidcUser user, @PathVariable long postId) {
        service.add(userId(user), postId);
    }

    @DeleteMapping("/{postId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal BrewOidcUser user, @PathVariable long postId) {
        service.remove(userId(user), postId);
    }

    private long userId(BrewOidcUser user) {
        if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return user.getUserId();
    }
}
