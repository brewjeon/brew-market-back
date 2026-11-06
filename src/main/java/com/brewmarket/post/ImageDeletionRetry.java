package com.brewmarket.post;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ImageDeletionRetry {
    private final ImageDeletionJobRepository jobs;
    private final ImageDeletionWorker worker;

    @Scheduled(fixedDelayString = "${app.images.cleanup-delay-ms:60000}")
    public void retry() {
        worker.process(jobs.findTop100ByOrderByImageKeyAsc().stream()
                .map(ImageDeletionJob::getImageKey).toList());
    }
}
