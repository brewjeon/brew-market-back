package com.brewmarket.post;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExpiredDraftCleanup {
    private final PostDraftService drafts;

    @Scheduled(fixedDelayString = "${app.drafts.cleanup-delay-ms:3600000}")
    public void run() { drafts.deleteExpired(); }
}
