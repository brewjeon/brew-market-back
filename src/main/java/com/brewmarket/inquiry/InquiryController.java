package com.brewmarket.inquiry;

import com.brewmarket.auth.BrewOidcUser;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/inquiries")
public class InquiryController {
    private final InquiryService service;
    private final InquiryUpdates updates;

    InquiryController(InquiryService service, InquiryUpdates updates) {
        this.service = service;
        this.updates = updates;
    }

    public record InquiryRequest(Long postId, String content) {}
    public record InquiryResponse(long roomId) {}
    public record MessageRequest(String content) {}
    public record ReadRequest(Long messageId) {}
    public record ReadState(long peerLastReadId) {}

    @GetMapping(value = "/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> events(@AuthenticationPrincipal BrewOidcUser user, @PathVariable("id") long id) {
        service.checkParticipant(id, userId(user));
        return ResponseEntity.ok()
                .header("Cache-Control", "no-cache, no-transform")
                .header("X-Accel-Buffering", "no")
                .body(updates.subscribe(id));
    }

    @GetMapping("/{id}/read")
    public ReadState readState(@AuthenticationPrincipal BrewOidcUser user, @PathVariable("id") long id) {
        return new ReadState(service.peerLastReadId(id, userId(user)));
    }

    @PutMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@AuthenticationPrincipal BrewOidcUser user, @PathVariable("id") long id,
                         @RequestBody ReadRequest request) {
        if (request.messageId() == null || request.messageId() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        service.markRead(id, userId(user), request.messageId());
    }

    @PostMapping
    public InquiryResponse inquire(@AuthenticationPrincipal BrewOidcUser user, @RequestBody InquiryRequest request) {
        if (request.postId() == null || request.postId() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        return new InquiryResponse(service.inquire(request.postId(), userId(user), request.content()));
    }

    @GetMapping("/by-post/{postId}")
    public ExistingRoomResponse existingRoom(@AuthenticationPrincipal BrewOidcUser user,
                                             @PathVariable("postId") long postId) {
        return new ExistingRoomResponse(service.findRoom(postId, userId(user)));
    }

    public record ExistingRoomResponse(Long roomId) {}

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
