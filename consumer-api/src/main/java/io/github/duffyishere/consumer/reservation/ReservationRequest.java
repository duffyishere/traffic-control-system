package io.github.duffyishere.consumer.reservation;

public record ReservationRequest(
    Long userId,
    Long seatId
) {}
