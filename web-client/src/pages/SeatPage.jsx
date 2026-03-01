import { useEffect, useMemo, useState } from "react";
import { SeatService } from "../services/SeatService";

export default function SeatPage({ search, routerService, sessionStore }) {
  const query = useMemo(() => routerService.readQuery(search), [routerService, search]);
  const queryBaseUrl = query.get("baseUrl");
  const baseUrl = useMemo(
    () => SeatService.normalizeBaseUrl(queryBaseUrl || sessionStore.getBaseUrl() || window.location.origin),
    [queryBaseUrl, sessionStore],
  );
  const [seats, setSeats] = useState([]);
  const [accessToken, setAccessToken] = useState(sessionStore.getAccessToken() || "");
  const [message, setMessage] = useState("좌석 조회 버튼을 눌러주세요.");
  const [loading, setLoading] = useState(false);
  const seatService = useMemo(() => new SeatService(baseUrl), [baseUrl]);
  const seatRequestUrl = useMemo(() => seatService.getSeatsRequestUrl(), [seatService]);

  useEffect(() => {
    sessionStore.setBaseUrl(baseUrl);
  }, [baseUrl, sessionStore]);

  useEffect(() => {
    if (query.get("autoLoad") !== "1") {
      return;
    }
    loadSeats();
    routerService.replace(`/?baseUrl=${encodeURIComponent(baseUrl)}`);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const resetToken = () => {
    sessionStore.clearAccessToken();
    setAccessToken("");
    setMessage("저장된 입장 토큰을 초기화했습니다.");
  };

  const loadSeats = async () => {
    setLoading(true);
    setMessage("좌석 목록 요청 중...");

    try {
      const accessToken = sessionStore.getAccessToken();
      const { response, isQueueRedirect, queueUrl, queuePagePath } = await seatService.requestSeats(accessToken);

      if (isQueueRedirect && queueUrl) {
        await response.body?.cancel().catch(() => {});
        const queuePageUrl =
          `/queue?queueUrl=${encodeURIComponent(queueUrl)}` +
          `&baseUrl=${encodeURIComponent(baseUrl)}`;
        routerService.navigate(queuePageUrl);
        return;
      }

      if (isQueueRedirect && queuePagePath) {
        routerService.navigate(queuePagePath);
        return;
      }

      if (!response.ok) {
        setSeats([]);
        setMessage(`요청 실패 (HTTP ${response.status})`);
        return;
      }

      const data = await response.json();
      const list = Array.isArray(data) ? data : [];
      setSeats(list);
      setAccessToken(sessionStore.getAccessToken() || "");
      setMessage(`조회 완료: ${list.length}개`);
    } catch (error) {
      setSeats([]);
      const reason = error instanceof Error ? error.message : "알 수 없는 오류";
      setMessage(`요청 실패: ${reason}`);
    } finally {
      setLoading(false);
    }
  };

  return (
    <main className="container">
      <header className="header">
        <p className="eyebrow">Web Client</p>
        <h1>콘서트 좌석 조회</h1>
      </header>

      <section className="panel">
        <label className="label" htmlFor="requestUrl">
          실제 요청 URL
        </label>
        <input
          id="requestUrl"
          className="input"
          value={seatRequestUrl}
          readOnly
        />
        <div className="actions">
          <button className="button" onClick={loadSeats} disabled={loading}>
            {loading ? "조회 중..." : "좌석 조회"}
          </button>
          <button className="button secondary" onClick={resetToken} type="button">
            토큰 초기화
          </button>
        </div>
        <p className="message">{message}</p>
      </section>

      <section className="panel">
        <h2>현재 토큰</h2>
        <textarea
          className="tokenBox"
          readOnly
          value={accessToken || "토큰이 없습니다. 대기열 통과 후 발급됩니다."}
        />
      </section>

      <section className="panel">
        <h2>좌석 목록</h2>
        <div className="grid">
          {seats.map((seat) => (
            <article className="card" key={seat.id}>
              <p className="seatNo">{seat.seatNo}</p>
              <p className="seatId">ID: {seat.id}</p>
              <span className={`status ${String(seat.status || "").toLowerCase()}`}>
                {seat.status}
              </span>
            </article>
          ))}
          {seats.length === 0 && <p className="empty">조회된 좌석이 없습니다.</p>}
        </div>
      </section>
    </main>
  );
}
