package io.github.duffyishere.consumer.reservation;

import io.github.duffyishere.consumer.seat.Seat;
import io.github.duffyishere.consumer.seat.SeatRepository;
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

        return reservation.getId();
    }
}
