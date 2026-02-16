package com.duffy.consumer.reservation;

public record ReservationRequest(
    Long userId,
    Long seatId
) {}
