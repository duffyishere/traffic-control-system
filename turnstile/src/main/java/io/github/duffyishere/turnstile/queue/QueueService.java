package io.github.duffyishere.turnstile.queue;

import com.nimbusds.jose.JOSEException;
import io.github.duffyishere.turnstile.common.TokenBucketResolver;
import io.github.duffyishere.turnstile.common.TokenProvider;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.function.Function;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueueService {

    private static final String QUEUE_NAME = "queue";
    private static final Duration DISPATCH_INTERVAL = Duration.ofMillis(200);
    private static final Duration STATUS_POLL_INTERVAL = Duration.ofSeconds(1);
    private static final Duration GRANT_TTL = Duration.ofSeconds(20);

    private final RedisQueueRepository queueRepository;
    private final TokenBucketResolver tokenBucketResolver;
    private final TokenProvider tokenProvider;

    private Disposable dispatcherSubscription;

    @PostConstruct
    public void startDispatcher() {
        this.dispatcherSubscription = Flux.interval(Duration.ZERO, DISPATCH_INTERVAL)
                .concatMap(tick -> dispatchOnce()
                        .onErrorResume(e -> {
                            log.error("Dispatcher loop failed", e);
                            return Mono.empty();
                        }))
                .subscribe();
    }

    @PreDestroy
    public void stopDispatcher() {
        if (dispatcherSubscription != null && !dispatcherSubscription.isDisposed()) {
            dispatcherSubscription.dispose();
        }
    }

    private Mono<Void> dispatchOnce() {
        return queueRepository.peekHead(QUEUE_NAME)
                .flatMap(headRequestId -> tokenBucketResolver.checkAccess()
                        .filter(Boolean::booleanValue)
                        .flatMap(ignored -> queueRepository.remove(QUEUE_NAME, headRequestId))
                        .filter(removedCount -> removedCount > 0)
                        .flatMap(ignored -> issueGrant(headRequestId)))
                .then();
    }

    private Mono<Void> issueGrant(String requestId) {
        try {
            String token = tokenProvider.generateToken(requestId);
            return queueRepository.saveGrant(requestId, token, GRANT_TTL);
        } catch (JOSEException e) {
            log.error("Failed to generate token", e);
            return Mono.empty();
        }
    }

    public Mono<QueueResponse> currentStatus(String requestId) {
        return queueRepository.getGrant(requestId)
                .map(token -> new QueueResponse("ALLOWED", 0L, token))
                .switchIfEmpty(
                        queueRepository.getRank(QUEUE_NAME, requestId)
                                .map(rank -> new QueueResponse("WAITING", rank + 1, null))
                                .defaultIfEmpty(new QueueResponse("EXPIRED", -1L, null))
                );
    }

    public Flux<QueueResponse> subscribeQueue(String requestId) {
        Mono<String> resourceSupplier = queueRepository.register(QUEUE_NAME, requestId)
                .thenReturn(requestId);
        Function<String, Publisher<?>> asyncCleanup = id -> queueRepository.remove(QUEUE_NAME, id);

        return Flux.usingWhen(
                resourceSupplier,
                id -> Flux.interval(Duration.ZERO, STATUS_POLL_INTERVAL)
                        .concatMap(tick -> currentStatus(id))
                        .takeUntil(response ->
                                "ALLOWED".equals(response.status()) || "EXPIRED".equals(response.status())
                        ),
                asyncCleanup
        );
    }
}
