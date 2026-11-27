package xyz.mcutils.backend.job.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import xyz.mcutils.backend.Main;
import xyz.mcutils.backend.common.AdaptiveRateLimiter;
import xyz.mcutils.backend.common.FutureUtils;
import xyz.mcutils.backend.common.ImageUtils;
import xyz.mcutils.backend.common.WebRequest;
import xyz.mcutils.backend.common.redis.RedisQueue;
import xyz.mcutils.backend.common.redis.RedisQueueFactory;
import xyz.mcutils.backend.exception.impl.RateLimitException;
import xyz.mcutils.backend.job.Job;
import xyz.mcutils.backend.job.JobContext;
import xyz.mcutils.backend.job.JobResult;
import xyz.mcutils.backend.job.JobTrigger;
import xyz.mcutils.backend.repository.postgres.SkinRepository;
import xyz.mcutils.backend.service.LegacySkinCheckService;
import xyz.mcutils.backend.service.StorageService;

import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;

/**
 * Background worker that resolves 64×32 legacy skin status for newly tracked skins.
 * Queue payloads are skin {@code textureId} strings.
 */
@SuppressWarnings("UnstableApiUsage")
@Component
@Slf4j
public class LegacySkinCheckJob implements Job {

    private static final int BATCH_SIZE = 500;
    private static final int RATE_LIMIT = 100;
    private static final Duration EMPTY_QUEUE_BLOCK = Duration.ofSeconds(2);

    private final AdaptiveRateLimiter rateLimiter = new AdaptiveRateLimiter("legacy-skin-check", RATE_LIMIT);
    private final RedisQueue checkQueue;
    private final SkinRepository skinRepository;
    private final WebRequest webRequest;
    private final StorageService storageService;
    private final String texturesEndpoint;

    public LegacySkinCheckJob(
            RedisQueueFactory queueFactory,
            SkinRepository skinRepository,
            WebRequest webRequest,
            StorageService storageService,
            @Value("${mc-utils.mojang.textures-url:https://textures.minecraft.net}") String texturesEndpoint
    ) {
        this.texturesEndpoint = texturesEndpoint;
        this.checkQueue = queueFactory.getQueue(LegacySkinCheckService.QUEUE_NAME);
        this.skinRepository = skinRepository;
        this.webRequest = webRequest;
        this.storageService = storageService;
    }

    @Override
    public String name() {
        return "legacy-skin-check";
    }

    @Override
    public JobTrigger trigger() {
        return JobTrigger.continuous(Duration.ZERO);
    }

    @Override
    public JobResult run(JobContext context) {
        Optional<List<String>> batch = checkQueue.blockingTakeBatch(BATCH_SIZE, EMPTY_QUEUE_BLOCK);
        if (batch.isEmpty()) {
            return JobResult.IDLE;
        }
        processBatch(batch.get(), context);
        return JobResult.WORKED;
    }

    /**
     * Checks the batch. Anything popped that is not settled when this returns, whether by shutdown or a failure,
     * goes back on the queue with its dedupe key intact.
     */
    private void processBatch(List<String> batch, JobContext context) {
        List<String> textureIds = batch.stream()
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim)
                .toList();
        Set<String> settled = new HashSet<>();
        try {
            checkBatch(textureIds, context, settled);
        } finally {
            List<String> unsettled = textureIds.stream().filter(id -> !settled.contains(id)).toList();
            if (!unsettled.isEmpty()) {
                checkQueue.requeue(unsettled);
            }
        }
    }

    private void checkBatch(List<String> textureIds, JobContext context, Set<String> settled) {
        Queue<CheckResult> results = new ConcurrentLinkedQueue<>();
        List<Future<Void>> futures = new ArrayList<>();

        for (String textureId : textureIds) {
            if (!context.isRunning()) {
                break;
            }
            rateLimiter.acquire();
            futures.add(Main.EXECUTOR.submit(() -> {
                results.add(checkLegacy(textureId));
                return null;
            }));
        }

        FutureUtils.awaitAll(futures, "legacy-skin-check");

        List<String> completed = new ArrayList<>();
        List<String> legacyIds = new ArrayList<>();
        List<String> modernIds = new ArrayList<>();

        for (CheckResult result : results) {
            switch (result.outcome()) {
                case RETRY -> {
                    // Not completed: it stays unsettled so processBatch requeues it.
                }
                case DROP -> completed.add(result.textureId());
                case SUCCESS -> {
                    (result.legacy() ? legacyIds : modernIds).add(result.textureId());
                    completed.add(result.textureId());
                }
            }
        }
        if (!legacyIds.isEmpty()) {
            skinRepository.updateLegacyByTextureIds(legacyIds, true);
        }
        if (!modernIds.isEmpty()) {
            skinRepository.updateLegacyByTextureIds(modernIds, false);
        }

        settled.addAll(completed);
        if (!completed.isEmpty()) {
            checkQueue.removeDedupeKeys(completed);
        }
    }

    private CheckResult checkLegacy(String textureId) {
        try {
            String textureUrl = texturesEndpoint + "/texture/" + textureId;
            byte[] bytes = webRequest.request(textureUrl).asBytes();
            rateLimiter.speedUp();
            if (bytes == null) {
                log.debug("No response fetching skin texture {}", textureId);
            }
            BufferedImage image = ImageUtils.decodeImage(bytes);
            boolean legacy = image.getWidth() == 64 && image.getHeight() == 32;
            String fileName = textureId + ".png";
            if (!storageService.exists(StorageService.Bucket.SKINS, fileName)) {
                storageService.upload(StorageService.Bucket.SKINS, fileName, MediaType.IMAGE_PNG_VALUE, bytes);
            }
            return CheckResult.success(textureId, legacy);
        } catch (RateLimitException e) {
            rateLimiter.slowDown();
            log.debug("Rate limited checking legacy status for {}, will retry", textureId);
            return CheckResult.retry(textureId);
        } catch (ResourceAccessException e) {
            log.debug("Timed out checking legacy status for {}, will retry", textureId);
            return CheckResult.retry(textureId);
        } catch (IllegalStateException e) {
            log.debug("Failed to decode skin texture {}: {}", textureId, e.getMessage());
            return CheckResult.success(textureId, false);
        } catch (Exception e) {
            log.warn("Unexpected error checking legacy status for {}, dropping", textureId, e);
            return CheckResult.drop(textureId);
        }
    }

    private record CheckResult(String textureId, Outcome outcome, boolean legacy) {
        enum Outcome {
            SUCCESS,
            RETRY,
            DROP
        }

        static CheckResult success(String textureId, boolean legacy) {
            return new CheckResult(textureId, Outcome.SUCCESS, legacy);
        }

        static CheckResult drop(String textureId) {
            return new CheckResult(textureId, Outcome.DROP, false);
        }

        static CheckResult retry(String textureId) {
            return new CheckResult(textureId, Outcome.RETRY, false);
        }
    }
}
