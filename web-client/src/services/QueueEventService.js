export class QueueEventService {
  connect(queueUrl, handlers) {
    const eventSource = new EventSource(queueUrl, { withCredentials: true });

    eventSource.addEventListener("waiting", (event) => {
      handlers.onWaiting?.(this.safeParse(event.data));
    });

    eventSource.addEventListener("allowed", (event) => {
      handlers.onAllowed?.(this.safeParse(event.data));
    });

    eventSource.addEventListener("expired", (event) => {
      handlers.onExpired?.(this.safeParse(event.data));
    });

    eventSource.onerror = (error) => {
      handlers.onError?.(error);
    };

    return () => eventSource.close();
  }

  safeParse(jsonText) {
    try {
      return JSON.parse(jsonText);
    } catch (error) {
      return null;
    }
  }
}

export const queueEventService = new QueueEventService();
