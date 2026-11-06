package com.brewmarket.inquiry;

import com.brewmarket.auth.BrewOidcUser;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/inquiries")
public class InquiryController {
    private final InquiryService service;

    public InquiryController(InquiryService service) {
        this.service = service;
    }

    public record InquiryRequest(Long postId, String content) {}
    public record InquiryResponse(long roomId) {}
    public record MessageRequest(String content) {}

    @PostMapping
    public InquiryResponse inquire(@AuthenticationPrincipal BrewOidcUser user, @RequestBody InquiryRequest request) {
        if (request.postId() == null || request.postId() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        return new InquiryResponse(service.inquire(request.postId(), userId(user), request.content()));
    }

    @GetMapping
    public InquiryService.RoomPage rooms(@AuthenticationPrincipal BrewOidcUser user,
                                         @RequestParam(value = "before", required = false) Long before) {
        return service.rooms(userId(user), before == null ? Long.MAX_VALUE : before);
    }

    @GetMapping("/{id}/messages")
    public List<InquiryService.Message> messages(@AuthenticationPrincipal BrewOidcUser user, @PathVariable("id") long id) {
        return service.messages(id, userId(user));
    }

    @PostMapping("/{id}/messages")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void send(@AuthenticationPrincipal BrewOidcUser user, @PathVariable("id") long id,
                     @RequestBody MessageRequest request) {
        service.send(id, userId(user), request.content());
    }

    private long userId(BrewOidcUser user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return user.getUserId();
    }
}
