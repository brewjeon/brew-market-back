package com.brewmarket.post;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ImageDeletionWorker {
    private final ImageDeletionJobRepository jobs;
    private final PostImageStorage storage;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(List<String> keys) {
        for (String key : keys) {
            if (jobs.existsById(key) && storage.delete(key)) jobs.deleteById(key);
        }
    }

}
