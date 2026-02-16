package com.duffy.consumer.global;

import com.duffy.consumer.seat.Seat;
import com.duffy.consumer.seat.SeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final SeatRepository seatRepository;

    @Override
    public void run(String... args) throws Exception {
        if (seatRepository.count() == 0) {
            System.out.println("[Initialization] Starting dummy seat generation...");
            List<Seat> seats = new ArrayList<>();
            for (char row = 'A'; row <= 'Z'; row++) {
                for (int num = 1; num <= 10; num++) {
                    seats.add(Seat.builder()
                            .seatNo(row + "-" + num)
                            .build());
                }
            }
            seatRepository.saveAll(seats);
            System.out.println("[Initialization] " + seats.size() + " seats have been generated");
        }
    }
}
