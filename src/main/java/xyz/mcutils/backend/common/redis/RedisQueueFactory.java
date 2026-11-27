package xyz.mcutils.backend.common.redis;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import xyz.mcutils.backend.config.RedisConfig;

/**
 * Creates {@link RedisQueue} instances backed by the shared producer and consumer Redis templates.
 */
@Component
public class RedisQueueFactory {

    private final RedisTemplate<String, String> producer;
    private final RedisTemplate<String, String> consumer;

    public RedisQueueFactory(@Qualifier(RedisConfig.QUEUE_PRODUCER_TEMPLATE) RedisTemplate<String, String> producer,
                             @Qualifier(RedisConfig.QUEUE_CONSUMER_TEMPLATE) RedisTemplate<String, String> consumer) {
        this.producer = producer;
        this.consumer = consumer;
    }

    public RedisQueue getQueue(String queueName) {
        return new RedisQueue(producer, consumer, queueName);
    }
}
