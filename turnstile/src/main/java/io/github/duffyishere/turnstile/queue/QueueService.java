package io.github.duffyishere.turnstile.queue;

import io.github.duffyishere.turnstile.common.TokenBucketResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.function.Function;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueueService {

    private final RedisQueueRepository queueRepository;
    private final TokenBucketResolver tokenBucketResolver;

    private String queueName = "queue";

    public Flux<QueueResponse> subscribeQueue(String requestId) {

        Mono<String> resourceSupplier = queueRepository.register(queueName, requestId)
                .thenReturn(requestId);
        Function<String, Publisher<?>> asyncCleanup = id -> queueRepository.remove(queueName, id);

        return Flux.usingWhen(
                resourceSupplier,
                id -> Flux.interval(Duration.ofSeconds(1))
                        .concatMap(tick -> Mono.zip(
                                queueRepository.getRank(queueName, id).defaultIfEmpty(-1L),
                                tokenBucketResolver.checkAccess()
                        ).flatMap(tuple -> {
                            long rank = tuple.getT1();
                            boolean canAccess = tuple.getT2();

                            if (rank < 0) {
                                return Mono.just(new QueueResponse("EXPIRED", -1L, null));
                            }

                            if (canAccess) {
                                // TODO: Genenrate jwt token
                                String token = "generated_jwt_token";
                                return Mono.just(new QueueResponse("ALLOWED", 0L, token));
                            }

                            return Mono.just(new QueueResponse("WAITING", rank + 1, null));
                        }))
                        .takeUntil(response ->
                                "ALLOWED".equals(response.status()) || "EXPIRED".equals(response.status())
                        ),
                asyncCleanup
        );
    }
}
