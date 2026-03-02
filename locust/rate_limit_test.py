import json
import random
import time
from dataclasses import dataclass
from urllib.parse import parse_qs, urlparse

from locust import HttpUser, between, task

HOST = "http://gateway:8080"
SEATS_PATH = "/api/v1/concerts/seats"
RESERVATION_PATH = "/api/v1/reservation"
QUEUE_EVENTS_PATH = "/turnstile/queue/events"
CURRENT_PAGE_URI_HEADER = "X-Current-Page-Uri"
REDIRECT_STATUS = {301, 302, 303, 307, 308}


@dataclass
class QueueRedirect:
    request_id: str
    requested_uri: str | None


class TurnstileQueueClient:
    def __init__(self, client, current_page_uri: str):
        self.client = client
        self.current_page_uri = current_page_uri

    def build_headers(self, token: str | None = None) -> dict[str, str]:
        headers = {
            "Accept": "application/json",
            CURRENT_PAGE_URI_HEADER: self.current_page_uri,
        }
        if token:
            headers["Authorization"] = f"Bearer {token}"
        return headers

    def parse_redirect(self, location: str | None) -> QueueRedirect | None:
        if not location:
            return None

        parsed = urlparse(location)
        query = parse_qs(parsed.query)
        request_id = self._first_value(query.get("requestId"))
        requested_uri = self._first_value(query.get("requestedUri"))

        if not request_id:
            return None
        return QueueRedirect(request_id=request_id, requested_uri=requested_uri)

    def wait_for_allowed_token(self, redirect: QueueRedirect, timeout_seconds: int = 60) -> str | None:
        params = {"requestId": redirect.request_id}
        if redirect.requested_uri:
            params["requestedUri"] = redirect.requested_uri

        with self.client.get(
            QUEUE_EVENTS_PATH,
            params=params,
            headers={"Accept": "text/event-stream"},
            stream=True,
            catch_response=True,
            name="GET /turnstile/queue/events",
        ) as response:
            if response.status_code != 200:
                response.failure(f"SSE 연결 실패: HTTP {response.status_code}")
                return None

            start = time.time()
            for raw_line in response.iter_lines(decode_unicode=True):
                if time.time() - start > timeout_seconds:
                    response.failure(f"SSE 대기열 타임아웃: {timeout_seconds}s")
                    return None

                line = (raw_line or "").strip()
                if not line.startswith("data:"):
                    continue

                payload = self._parse_payload(line[5:].strip())
                if payload is None:
                    continue

                status = str(payload.get("status", "")).upper()
                if status == "ALLOWED":
                    token = payload.get("token")
                    if token:
                        response.success()
                        return token
                    response.failure("ALLOWED 응답에 토큰이 없습니다.")
                    return None

                if status == "EXPIRED":
                    response.failure("대기열 요청이 만료되었습니다.")
                    return None

            response.failure("SSE 스트림이 ALLOWED 없이 종료되었습니다.")
            return None

    @staticmethod
    def _parse_payload(data: str) -> dict | None:
        try:
            parsed = json.loads(data)
        except json.JSONDecodeError:
            return None
        return parsed if isinstance(parsed, dict) else None

    @staticmethod
    def _first_value(values: list[str] | None) -> str | None:
        if not values:
            return None
        value = values[0]
        return value if value else None


class WebsiteUser(HttpUser):
    host = HOST
    wait_time = between(0.2, 1.0)

    def on_start(self):
        self.access_token = None
        self.queue_client = TurnstileQueueClient(self.client, current_page_uri="/locust")

    @task
    def reserve_random_seat(self):
        seats = self.fetch_seats_with_queue()
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

        self.reserve_seat_with_token(int(seat_id))

    def fetch_seats_with_queue(self) -> list[dict] | None:
        token = self.access_token

        for _ in range(3):
            with self.client.get(
                SEATS_PATH,
                headers=self.queue_client.build_headers(token),
                allow_redirects=False,
                catch_response=True,
                name="GET /api/v1/concerts/seats",
            ) as response:
                if response.status_code == 200:
                    try:
                        seats = response.json()
                    except Exception as error:
                        response.failure(f"좌석 조회 JSON 파싱 실패: {error}")
                        return None

                    if not isinstance(seats, list):
                        response.failure("좌석 조회 응답이 배열이 아닙니다.")
                        return None

                    response.success()
                    self.access_token = token
                    return seats

                if response.status_code in REDIRECT_STATUS:
                    redirect = self.queue_client.parse_redirect(response.headers.get("Location"))
                    if redirect is None:
                        response.failure("대기열 리다이렉트 Location 파싱 실패")
                        return None

                    response.success()
                    issued_token = self.queue_client.wait_for_allowed_token(redirect)
                    if issued_token is None:
                        return None

                    token = issued_token
                    continue

                response.failure(f"좌석 조회 실패: HTTP {response.status_code}")
                return None

        return None

    def reserve_seat_with_token(self, seat_id: int):
        user_id = random.randint(1, 1_000_000)
        payload = {"seatId": seat_id, "userId": user_id}
        token = self.access_token

        for _ in range(2):
            with self.client.post(
                RESERVATION_PATH,
                json=payload,
                headers=self.queue_client.build_headers(token),
                allow_redirects=False,
                catch_response=True,
                name="POST /api/v1/reservation",
            ) as response:
                if response.status_code == 200:
                    response.success()
                    self.access_token = token
                    return

                if response.status_code == 400:
                    # 비즈니스 실패(중복 예약)로 보고 정상 응답 처리
                    response.success()
                    return

                if response.status_code in REDIRECT_STATUS:
                    redirect = self.queue_client.parse_redirect(response.headers.get("Location"))
                    if redirect is None:
                        response.failure("예약 리다이렉트 Location 파싱 실패")
                        return

                    response.success()
                    issued_token = self.queue_client.wait_for_allowed_token(redirect)
                    if issued_token is None:
                        return

                    token = issued_token
                    continue

                response.failure(f"예약 요청 실패: HTTP {response.status_code}")
                return
