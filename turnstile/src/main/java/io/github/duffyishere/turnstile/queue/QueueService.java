package io.github.duffyishere.turnstile.queue;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueueService {

    private String bucketName = "ratelimit";
    private String queueName = "queue";
    private final RedisQueueRepository queueRepository;

    public Flux<QueueResponse> subscribeQueue(String requestId) {
        return queueRepository.register(queueName, requestId)
                .doOnError(e -> log.error("Redis 등록 실패", e))
                .thenMany(
                        Flux.interval(Duration.ofSeconds(1))
                                .concatMap(tick -> {
                                    Mono<Long> rankMono = queueRepository.getRank(queueName, requestId).defaultIfEmpty(-1L);
                                    Mono<Long> longMono = queueRepository.getAllowedLimit(bucketName).defaultIfEmpty(100L);

                                    return Mono.zip(rankMono, longMono)
                                            .flatMap(tuple -> {
                                                Long rank = tuple.getT1();
                                                Long allowedLimit = tuple.getT2();

                                                if (rank < 0) {
                                                    return Mono.just(new QueueResponse("EXPIRED", -1L, null));
                                                }
                                                if (rank < allowedLimit) {
                                                    return queueRepository.remove(queueName, requestId)
                                                            .map(removed -> {
                                                                // TODO: Implement token generation
                                                                String token = "123";
                                                                return new QueueResponse("ALLOWED", 0L, token);
                                                            });
                                                } else {
                                                    return Mono.just(new QueueResponse("WAITING", rank, ""));
                                                }
                                            });

                                })
                );
    }
}
