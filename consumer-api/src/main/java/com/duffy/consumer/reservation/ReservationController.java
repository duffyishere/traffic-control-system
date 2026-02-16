package com.duffy.consumer.reservation;

import com.duffy.consumer.seat.SeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reservation")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping
    public ResponseEntity<String> reserve(@RequestBody ReservationRequest request) {
        Long reservationId = reservationService.reserve(request);
        return ResponseEntity.ok().body(reservationId.toString());
    }
}
