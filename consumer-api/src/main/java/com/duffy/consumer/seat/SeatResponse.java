package com.duffy.consumer.seat;

public record SeatResponse(
        Long id,
        String seatNo,
        String status
) {
    public static SeatResponse from(Seat seat) {
        return new SeatResponse(
                seat.getId(),
                seat.getSeatNo(),
                seat.getStatus().name()
        );
    }
}
