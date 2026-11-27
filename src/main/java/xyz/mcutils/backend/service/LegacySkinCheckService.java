package xyz.mcutils.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.common.redis.RedisQueue;
import xyz.mcutils.backend.common.redis.RedisQueueFactory;

import java.util.Collection;
import java.util.List;

/**
 * Queue of newly tracked skin textures awaiting legacy status resolution.
 */
@Service
@Slf4j
public class LegacySkinCheckService {

    public static final String QUEUE_NAME = "legacy-skin-check-queue";

    private final RedisQueue checkQueue;

    public LegacySkinCheckService(RedisQueueFactory queueFactory) {
        this.checkQueue = queueFactory.getQueue(QUEUE_NAME);
    }

    /**
     * Enqueues a skin texture for legacy status resolution.
     */
    public void enqueue(String textureId) {
        if (textureId == null || textureId.isBlank()) {
            return;
        }
        enqueue(List.of(textureId.trim()));
    }

    /**
     * Enqueues multiple skin textures, skipping any already queued.
     */
    public int enqueue(Collection<String> textureIds) {
        if (textureIds == null || textureIds.isEmpty()) {
            return 0;
        }
        List<RedisQueue.QueueItem> items = textureIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim)
                .distinct()
                .map(RedisQueue.QueueItem::of)
                .toList();
        if (items.isEmpty()) {
            return 0;
        }
        RedisQueue.EnqueueResult result = checkQueue.enqueue(items);
        if (result.enqueued() > 0 && log.isDebugEnabled()) {
            log.debug("Enqueued {} skins for legacy check (queue size: {})", result.enqueued(), checkQueue.size());
        }
        return result.enqueued();
    }

    public long getQueueSize() {
        return checkQueue.size();
    }
}
