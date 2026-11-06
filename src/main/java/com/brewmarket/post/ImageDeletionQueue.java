package com.brewmarket.post;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@RequiredArgsConstructor
public class ImageDeletionQueue {
    private static final Logger log = LoggerFactory.getLogger(ImageDeletionQueue.class);
    private final ImageDeletionJobRepository jobs;
    private final ImageDeletionWorker worker;

    public void schedule(List<String> keys) {
        if (keys.isEmpty()) return;
        jobs.saveAll(keys.stream().map(ImageDeletionJob::new).toList());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                try {
                    worker.process(keys);
                } catch (RuntimeException failure) {
                    log.warn("사진 파일 즉시 삭제 실패; 예약 작업에서 다시 시도합니다.", failure);
                }
            }
        });
    }
}
