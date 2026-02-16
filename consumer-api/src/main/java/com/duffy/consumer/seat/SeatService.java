package com.duffy.consumer.seat;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SeatService {

    private final SeatRepository seatRepository;

    public List<SeatResponse> getAllSeats() {
        return seatRepository.findAll(Sort.by(Sort.Direction.ASC, "id"))
                .stream()
                .map(SeatResponse::from)
                .collect(Collectors.toList());
    }
}
