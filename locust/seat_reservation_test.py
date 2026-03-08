import os
import random

from locust import HttpUser, between, task

HOST = os.getenv("LOCUST_HOST", "http://gateway:8080")
SEATS_PATH = "/api/v1/concerts/seats"
RESERVATION_PATH = "/api/v1/reservation"


class WebsiteUser(HttpUser):
    host = HOST
    wait_time = between(0.2, 1.0)

    @task
    def reserve_random_seat(self):
        seats = self.fetch_seats()
        if not seats:
            return

        candidate_seats = [
            seat for seat in seats
            if str(seat.get("status", "")).upper() == "AVAILABLE"
        ]
        target_pool = candidate_seats if candidate_seats else seats
        target_seat = random.choice(target_pool)
        seat_id = target_seat.get("id")
        if seat_id is None:
            return

        self.reserve_seat(int(seat_id))

    def fetch_seats(self) -> list[dict] | None:
        with self.client.get(
            SEATS_PATH,
            headers={"Accept": "application/json"},
            catch_response=True,
            name="GET /api/v1/concerts/seats",
        ) as response:
            if response.status_code != 200:
                response.failure(f"좌석 조회 실패: HTTP {response.status_code}")
                return None

            try:
                seats = response.json()
            except Exception as error:
                response.failure(f"좌석 조회 JSON 파싱 실패: {error}")
                return None

            if not isinstance(seats, list):
                response.failure("좌석 조회 응답이 배열이 아닙니다.")
                return None

            response.success()
            return seats

    def reserve_seat(self, seat_id: int):
        payload = {
            "seatId": seat_id,
            "userId": random.randint(1, 1_000_000),
        }

        with self.client.post(
            RESERVATION_PATH,
            json=payload,
            headers={"Accept": "application/json"},
            catch_response=True,
            name="POST /api/v1/reservation",
        ) as response:
            if response.status_code == 200:
                response.success()
                return

            if response.status_code == 400:
                response.success()
                return

            response.failure(f"예약 요청 실패: HTTP {response.status_code}")
