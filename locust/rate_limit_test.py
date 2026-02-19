import random
from locust import HttpUser, task, between

HOST="http://gateway:8080"

class WebsiteUser(HttpUser):
    host = HOST

    @task
    def test(self):
        with self.client.get("/api/v1/concerts/seats", catch_response=True) as response:
            if response.status_code != 200:
                response.failure(f"좌석 조회 실패: {response.status_code}")
                return

            try:
                seats = response.json()
            except Exception as e:
                response.failure(f"JSON 파싱 에러: {e}")
                return

            if not seats or len(seats) == 0:
                response.failure("예약 가능한 좌석이 없습니다.")
                return

        target_seat = random.choice(seats)
        seat_id = target_seat.get('id')
        user_id = random.randint(1, 1000000)
        payload = {
            "seatId": seat_id,
            "userId": user_id
        }

        with self.client.post("/api/v1/reservation", json=payload, catch_response=True) as post_response:
            if post_response.status_code == 200:
                post_response.success()
            elif post_response.status_code == 409:
                post_response.failure("이미 예약된 좌석 (409)") 
            else:
                post_response.failure(f"예약 요청 실패: {post_response.status_code}")