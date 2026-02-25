package io.github.duffyishere.turnstile.queue;

public record QueueResponse (
        String status,
        Long rank,
        String token
) {}
