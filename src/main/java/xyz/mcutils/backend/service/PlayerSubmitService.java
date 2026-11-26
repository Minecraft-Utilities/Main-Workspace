package xyz.mcutils.backend.service;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.common.UUIDUtils;
import xyz.mcutils.backend.common.redis.RedisQueue;
import xyz.mcutils.backend.common.redis.RedisQueueFactory;
import xyz.mcutils.backend.metric.impl.player.PlayerSubmitOutcomesMetric;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Dedicated submit queue for tracking new players.
 * Queue entries are stored as strings: {@code playerUuid,submitterUuid} (or {@code playerUuid} when no submitter).
 */
@Service
public class PlayerSubmitService {

    public static final String QUEUE_NAME = "player-submit-queue";

    private final RedisQueue submitQueue;
    private final PlayerService playerService;

    public PlayerSubmitService(RedisQueueFactory queueFactory, @Lazy PlayerService playerService) {
        this.submitQueue = queueFactory.getQueue(QUEUE_NAME);
        this.playerService = playerService;
    }

    public int submitPlayers(List<String> players, String submittedBy) {
        UUID by = (submittedBy != null && !submittedBy.isBlank()) ? UUIDUtils.parseUuid(submittedBy.trim()) : null;
        List<UUID> toEnqueue = players.stream()
                .filter(id -> id != null && !id.isBlank())
                .map(id -> UUIDUtils.parseUuid(id.trim()))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (toEnqueue.isEmpty()) {
            return 0;
        }

        Set<UUID> existingInDb = playerService.getExistingPlayerIds(toEnqueue);
        if (!existingInDb.isEmpty()) {
            MetricService.getMetric(PlayerSubmitOutcomesMetric.class).inc(PlayerSubmitOutcomesMetric.Outcome.ALREADY_TRACKED, existingInDb.size());
        }
        toEnqueue = toEnqueue.stream().filter(uuid -> !existingInDb.contains(uuid)).toList();
        if (toEnqueue.isEmpty()) {
            return 0;
        }

        List<RedisQueue.QueueItem> items = toEnqueue.stream()
                .map(uuid -> new RedisQueue.QueueItem(new QueueEntry(uuid, by).toRedisValue(), uuid.toString()))
                .toList();
        RedisQueue.EnqueueResult result = submitQueue.enqueue(items);
        if (result.alreadyQueued() > 0) {
            MetricService.getMetric(PlayerSubmitOutcomesMetric.class).inc(PlayerSubmitOutcomesMetric.Outcome.ALREADY_QUEUED, result.alreadyQueued());
        }
        if (result.enqueued() == 0) {
            return 0;
        }

        MetricService.getMetric(PlayerSubmitOutcomesMetric.class).inc(PlayerSubmitOutcomesMetric.Outcome.ENQUEUED, result.enqueued());
        return result.enqueued();
    }

    public long getSubmissionQueueSize() {
        return submitQueue.size();
    }

    public record QueueEntry(UUID playerId, UUID submittedBy) {
        public String toRedisValue() {
            return submittedBy != null
                    ? playerId + "," + submittedBy
                    : playerId.toString();
        }

        public static Optional<QueueEntry> fromRedisValue(String s) {
            if (s == null || s.isBlank()) {
                return Optional.empty();
            }
            String[] parts = s.split(",", 2);
            UUID playerId = UUIDUtils.parseUuid(parts[0].trim());
            if (playerId == null) {
                return Optional.empty();
            }
            UUID submittedBy = parts.length > 1 && !parts[1].isBlank()
                    ? UUIDUtils.parseUuid(parts[1].trim())
                    : null;
            return Optional.of(new QueueEntry(playerId, submittedBy));
        }
    }
}
