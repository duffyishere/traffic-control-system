package io.github.duffyishere.gateway.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class RedisStartupChecker implements ApplicationRunner {

    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("Checking if redis client is ready");
        try {
            String pong = redisTemplate.getConnectionFactory().getConnection().ping();
            if (pong.equals("PONG")) {
                log.info("Redis client is ready");
            } else  {
                log.error("Redis client is not ready");
            }
        } catch (Exception e) {
            log.error("Redis Connection Failed.", e);
        }
    }
}
