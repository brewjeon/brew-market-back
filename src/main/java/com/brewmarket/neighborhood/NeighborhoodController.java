package com.brewmarket.neighborhood;

import com.brewmarket.auth.BrewOidcUser;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/neighborhoods")
@RequiredArgsConstructor
class NeighborhoodController {
    private final NeighborhoodService service;

    @GetMapping
    List<NeighborhoodService.ListedNeighborhood> list() {
        return service.list();
    }

    @GetMapping("/verified")
    List<NeighborhoodOption> active(@AuthenticationPrincipal BrewOidcUser user) {
        return service.active(userId(user));
    }

    @DeleteMapping("/verified/{neighborhoodId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revoke(@AuthenticationPrincipal BrewOidcUser user, @PathVariable Long neighborhoodId) {
        service.revoke(userId(user), neighborhoodId);
    }

    @PostMapping("/verify")
    NeighborhoodOption verify(@AuthenticationPrincipal BrewOidcUser user, @RequestBody Location request) {
        if (request.latitude() == null || request.longitude() == null || request.accuracy() == null) {
            throw new NeighborhoodException(HttpStatus.BAD_REQUEST, "현재 위치를 확인해 주세요.");
        }
        return service.verify(userId(user), request.latitude(), request.longitude(), request.accuracy());
    }

    private Long userId(BrewOidcUser user) {
        if (user == null) throw new NeighborhoodException(HttpStatus.UNAUTHORIZED, "다시 로그인해 주세요.");
        return user.getUserId();
    }

    record Location(Double latitude, Double longitude, Double accuracy) {}
}
