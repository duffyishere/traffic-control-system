package com.duffy.consumer.reservation;

import com.duffy.consumer.seat.Seat;
import com.duffy.consumer.seat.SeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final SeatRepository seatRepository;

    @Transactional
    public Long reserve(ReservationRequest request) {
        Seat seat = seatRepository.findByIdWithLock(request.seatId())
                .orElseThrow(() -> new IllegalArgumentException("There is no seat available for this seat"));
        seat.reserve();
        Reservation reservation = reservationRepository.save(
                Reservation.builder()
                        .userId(request.userId())
                        .seat(seat)
                        .build()
        );

        // [Important] External Payment Integration Simulation (1-Second Delay)
        // This delay causes DB connections to remain open for extended periods, and when traffic spikes, the database crashes.
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        return reservation.getId();
    }
}
