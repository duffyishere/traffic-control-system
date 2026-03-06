package io.github.duffyishere.consumer.global;

import io.github.duffyishere.consumer.seat.Seat;
import io.github.duffyishere.consumer.seat.SeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private static final int TOTAL_SEAT_COUNT = 1_000;
    private static final String SEAT_INIT_LOCK_NAME = "consumer-api-seat-init-lock";
    private static final int LOCK_TIMEOUT_SECONDS = 60;

    private final SeatRepository seatRepository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        if (!acquireInitializationLock()) {
            throw new IllegalStateException("Failed to acquire seat initialization lock");
        }

        try {
            if (seatRepository.count() > 0) {
                log.info("[Initialization] Seats already exist. Skipping seed.");
                return;
            }

            log.info("[Initialization] Starting dummy seat generation...");
            List<Seat> seats = new ArrayList<>(TOTAL_SEAT_COUNT);
            for (int seatNumber = 1; seatNumber <= TOTAL_SEAT_COUNT; seatNumber++) {
                seats.add(Seat.builder()
                        .seatNo(String.format("S-%04d", seatNumber))
                        .build());
            }
            seatRepository.saveAll(seats);
            log.info("[Initialization] {} seats have been generated", seats.size());
        } finally {
            releaseInitializationLock();
        }
    }

    private boolean acquireInitializationLock() {
        Integer lockStatus = jdbcTemplate.queryForObject(
                "SELECT GET_LOCK(?, ?)",
                Integer.class,
                SEAT_INIT_LOCK_NAME,
                LOCK_TIMEOUT_SECONDS
        );
        return lockStatus != null && lockStatus == 1;
    }

    private void releaseInitializationLock() {
        jdbcTemplate.queryForObject(
                "SELECT RELEASE_LOCK(?)",
                Integer.class,
                SEAT_INIT_LOCK_NAME
        );
    }
}
