package xyz.mcutils.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * @author Braydon
 */
@Configuration
@Slf4j
public class RedisConfig {

    public static final String QUEUE_CONSUMER_FACTORY = "queueConsumerConnectionFactory";
    public static final String QUEUE_CONSUMER_TEMPLATE = "queueConsumerRedisTemplate";
    public static final String QUEUE_PRODUCER_TEMPLATE = "queueProducerRedisTemplate";

    @Value("${mc-utils.redis.host}")
    private String host;

    @Value("${mc-utils.redis.port}")
    private int port;

    @Value("${mc-utils.redis.database}")
    private int database;

    @Value("${mc-utils.redis.auth}")
    private String auth;

    @Value("${mc-utils.redis.command-timeout-seconds:120}")
    private int commandTimeoutSeconds;

    /**
     * The connection for caches and queue producers. Lettuce multiplexes every command onto one connection, so
     * nothing that blocks may run here.
     */
    @Bean
    @Primary
    public LettuceConnectionFactory lettuceConnectionFactory() {
        return buildConnectionFactory(true);
    }

    /**
     * Connection factory for blocking queue consumers (BLPOP). Each call gets its own connection, so a consumer
     * waiting on an empty queue can neither stall producers nor another consumer.
     */
    @Bean(name = QUEUE_CONSUMER_FACTORY)
    public LettuceConnectionFactory queueConsumerConnectionFactory() {
        return buildConnectionFactory(false);
    }

    @Bean(name = "blobRedisTemplate")
    public RedisTemplate<String, byte[]> blobRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, byte[]> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(RedisSerializer.byteArray());
        return template;
    }

    @Bean(name = QUEUE_PRODUCER_TEMPLATE)
    public RedisTemplate<String, String> queueProducerRedisTemplate(RedisConnectionFactory connectionFactory) {
        return stringTemplate(connectionFactory);
    }

    @Bean(name = QUEUE_CONSUMER_TEMPLATE)
    public RedisTemplate<String, String> queueConsumerRedisTemplate(@Qualifier(QUEUE_CONSUMER_FACTORY) RedisConnectionFactory connectionFactory) {
        return stringTemplate(connectionFactory);
    }

    private static RedisTemplate<String, String> stringTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        return template;
    }

    private LettuceConnectionFactory buildConnectionFactory(boolean shareNativeConnection) {
        log.info("Connecting to Redis at {}:{}/{} with Lettuce (shared connection: {})", host, port, database, shareNativeConnection);

        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host, port);
        config.setDatabase(database);

        if (!auth.trim().isEmpty()) {
            config.setPassword(auth);
        }

        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofSeconds(commandTimeoutSeconds))
                .shutdownTimeout(Duration.ofMillis(100))
                .build();

        LettuceConnectionFactory factory = new LettuceConnectionFactory(config, clientConfig);
        factory.setShareNativeConnection(shareNativeConnection);
        return factory;
    }
}
