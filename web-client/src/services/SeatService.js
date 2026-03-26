const TURNSTILE_EVENT_PATH = "/turnstile/queue/events";
const QUEUE_PAGE_PATH = "/queue";
const SEATS_PATH = "/api/v1/concerts/seats";
const CURRENT_PAGE_URI_HEADER = "X-Current-Page-Uri";

export class SeatService {
  constructor(baseUrl) {
    this.baseUrl = SeatService.normalizeBaseUrl(baseUrl);
  }

  getSeatsRequestUrl() {
    return `${this.baseUrl}${SEATS_PATH}`;
  }

  async requestSeats(accessToken) {
    const headers = {
      Accept: "application/json",
      [CURRENT_PAGE_URI_HEADER]: this.getCurrentPageUri(),
    };
    if (accessToken) {
      headers.Authorization = `Bearer ${accessToken}`;
    }

    const response = await fetch(this.getSeatsRequestUrl(), {
      method: "GET",
      headers,
      credentials: "include",
    });

    const contentType = response.headers.get("content-type") || "";
    const redirectedUrl = new URL(response.url, window.location.origin);
    const isQueueJsonResponse = response.status === 202 && contentType.includes("application/json");
    const queuePayload = isQueueJsonResponse ? await response.clone().json().catch(() => null) : null;

    const isQueuePageRedirect =
      response.redirected &&
      redirectedUrl.origin === window.location.origin &&
      redirectedUrl.pathname === QUEUE_PAGE_PATH;

    const isQueueSseRedirect =
      contentType.includes("text/event-stream") || response.url.includes(TURNSTILE_EVENT_PATH);

    return {
      response,
      isQueueRedirect: isQueueJsonResponse || isQueuePageRedirect || isQueueSseRedirect,
      queuePagePath:
        queuePayload?.queuePagePath || (isQueuePageRedirect ? `${redirectedUrl.pathname}${redirectedUrl.search}` : null),
      queueUrl:
        (queuePayload?.queueSsePath
          ? new URL(queuePayload.queueSsePath, window.location.origin).toString()
          : null) || (isQueueSseRedirect ? response.url : null),
    };
  }

  static normalizeBaseUrl(value) {
    const trimmed = String(value || "").trim();
    if (!trimmed) {
      return window.location.origin;
    }
    return trimmed.endsWith("/") ? trimmed.slice(0, -1) : trimmed;
  }

  getCurrentPageUri() {
    return `${window.location.pathname}${window.location.search}`;
  }
}
