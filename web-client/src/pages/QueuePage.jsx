import { useEffect, useMemo, useState } from "react";
import { queueEventService } from "../services/QueueEventService";

export default function QueuePage({ search, routerService, sessionStore }) {
  const query = useMemo(() => routerService.readQuery(search), [routerService, search]);
  const requestId = query.get("requestId");
  const requestedUri = query.get("requestedUri");
  const queueUrlParam = query.get("queueUrl");
  const baseUrl = query.get("baseUrl") || sessionStore.getBaseUrl() || window.location.origin;
  const queueUrl = useMemo(() => {
    if (queueUrlParam) {
      return queueUrlParam;
    }
    if (!requestId) {
      return null;
    }
    const sseUrl = new URL("/turnstile/queue/events", window.location.origin);
    sseUrl.searchParams.set("requestId", requestId);
    if (requestedUri) {
      sseUrl.searchParams.set("requestedUri", requestedUri);
    }
    return sseUrl.toString();
  }, [queueUrlParam, requestId, requestedUri]);

  const [statusText, setStatusText] = useState("대기열 연결 준비 중...");
  const [rank, setRank] = useState(null);
  const [errorText, setErrorText] = useState("");

  useEffect(() => {
    if (!queueUrl) {
      setErrorText("대기열 URL이 없습니다. 좌석 페이지에서 다시 시도해주세요.");
      return;
    }

    const close = queueEventService.connect(queueUrl, {
      onWaiting: (payload) => {
        const nextRank = payload?.rank ?? null;
        setRank(nextRank);
        setStatusText(nextRank ? `대기 순번: ${nextRank}` : "대기열에서 순번을 확인 중입니다.");
      },
      onAllowed: (payload) => {
        if (payload?.token) {
          sessionStore.setAccessToken(payload.token);
        }
        const redirectTarget =
          routerService.resolveSameOriginPath(payload?.requestedUri) ||
          routerService.resolveSameOriginPath(requestedUri) ||
          `/?baseUrl=${encodeURIComponent(baseUrl)}&autoLoad=1`;

        setStatusText(`입장 허용됨. ${redirectTarget} 로 이동합니다.`);
        routerService.redirect(redirectTarget);
      },
      onExpired: () => {
        setStatusText("요청이 만료되었습니다.");
        setErrorText("요청 만료: 좌석 페이지에서 다시 조회해주세요.");
      },
      onError: () => {
        setStatusText("대기열 연결이 종료되었습니다.");
        setErrorText("연결 오류: 잠시 후 다시 시도해주세요.");
      },
    });

    return () => close();
  }, [baseUrl, queueUrl, requestedUri, routerService, sessionStore]);

  const goBack = () => {
    routerService.navigate(`/?baseUrl=${encodeURIComponent(baseUrl)}`);
  };

  return (
    <main className="container">
      <header className="header">
        <p className="eyebrow">Turnstile Queue</p>
        <h1>대기열 입장 페이지</h1>
      </header>

      <section className="panel queue-panel">
        <h2>현재 상태</h2>
        <p className="queue-status">{statusText}</p>
        {rank ? <p className="queue-rank">순번 {rank}</p> : null}
        {errorText ? <p className="queue-error">{errorText}</p> : null}
        <div className="actions">
          <button className="button secondary" type="button" onClick={goBack}>
            좌석 페이지로 돌아가기
          </button>
        </div>
      </section>
    </main>
  );
}
