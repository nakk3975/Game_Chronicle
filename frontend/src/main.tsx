import React, { useEffect, useState } from "react";
import { createRoot } from "react-dom/client";
import {
  BookOpen,
  LayoutDashboard,
  Clock3,
  Library,
  ChartNoAxesCombined,
  Settings,
  ArrowUpRight,
  ChevronRight,
  Search,
  Download,
  RefreshCw,
  Gamepad2,
  X,
  LogOut,
  ShieldCheck,
  Pause,
  Play,
  Sun,
  Moon,
} from "lucide-react";
import "./style.css";
type Segment = { start: string; end: string };
type Session = {
  id: string;
  gameId: string;
  name: string;
  startedAt: string;
  endedAt: string | null;
  lastSeen: string;
  segments: Segment[];
  quality: string;
  lifecycle: string;
  excluded: boolean;
  version: number;
  closeReason?: string;
};
type Game = {
  gameId: string;
  name: string;
  reportedMinutes: number;
  baselineMinutes: number;
  trackedSeconds: number;
  syncedAt: string;
};
type Summary = {
  seconds: number;
  days: Record<string, number>;
  games: Record<string, number>;
  hours: Record<string, number>;
  weekdays: Record<string, number>;
};
type Me = {
  displayName: string;
  timezone: string;
  trackingEnabled: boolean;
  version: number;
  libraryStatus: string;
  collectorEnabled: boolean;
  tracking: {
    status: string;
    lastSuccess: string | null;
    active: Session | null;
  };
};
const paths = [
  "/dashboard",
  "/timeline",
  "/library",
  "/analytics",
  "/settings",
];
const labels = ["대시보드", "타임라인", "라이브러리", "통계", "설정"];
const icons = [LayoutDashboard, Clock3, Library, ChartNoAxesCombined, Settings];
const duration = (s: number) =>
  `${Math.floor(s / 3600)}시간 ${Math.floor((s % 3600) / 60)}분`;
const seconds = (s: Session) =>
  s.segments.reduce(
    (a, g) => a + Math.max(0, (Date.parse(g.end) - Date.parse(g.start)) / 1000),
    0,
  );
const today = () =>
  new Date().toLocaleDateString("sv-SE", { timeZone: "Asia/Seoul" });
const plusDay = (d: string) =>
  new Date(Date.parse(d + "T00:00:00Z") + 86400000).toISOString().slice(0, 10);
const month = () => today().slice(0, 7) + "-01";
const empty: Summary = {
  seconds: 0,
  days: {},
  games: {},
  hours: {},
  weekdays: {},
};
let csrf: { token: string; headerName: string } | null = null;
async function api<T>(url: string, method = "GET", body?: unknown): Promise<T> {
  if (method !== "GET" && !csrf)
    csrf = await fetch("/api/v1/csrf").then((r) => r.json());
  const r = await fetch("/api/v1" + url, {
    method,
    headers: {
      "Content-Type": "application/json",
      ...(method !== "GET" && csrf ? { [csrf.headerName]: csrf.token } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!r.ok) {
    let message = "요청을 처리하지 못했습니다.";
    try {
      message = (await r.json()).error?.message || message;
    } catch {}
    throw Object.assign(new Error(message), { status: r.status });
  }
  return r.json();
}
const demoGames: Game[] = [
  {
    gameId: "1245620",
    name: "ELDEN RING",
    reportedMinutes: 8540,
    baselineMinutes: 7800,
    trackedSeconds: 26400,
    syncedAt: new Date().toISOString(),
  },
  {
    gameId: "1623730",
    name: "Palworld",
    reportedMinutes: 2180,
    baselineMinutes: 1760,
    trackedSeconds: 13200,
    syncedAt: new Date().toISOString(),
  },
  {
    gameId: "570",
    name: "Dota 2",
    reportedMinutes: 7200,
    baselineMinutes: 6800,
    trackedSeconds: 7200,
    syncedAt: new Date().toISOString(),
  },
];
function makeDemo(): Session[] {
  return Array.from({ length: 12 }, (_, i) => {
    const d = new Date();
    d.setDate(d.getDate() - Math.floor(i / 2));
    d.setHours(17 + (i % 2) * 3, 10, 0, 0);
    const e = new Date(+d + (65 + i * 7) * 60000),
      g = demoGames[i % 3];
    return {
      id: "sample-" + i,
      gameId: g.gameId,
      name: g.name,
      startedAt: d.toISOString(),
      endedAt: e.toISOString(),
      lastSeen: e.toISOString(),
      segments: [{ start: d.toISOString(), end: e.toISOString() }],
      quality: i === 3 ? "PARTIAL" : "ESTIMATED",
      lifecycle: "FINALIZED",
      excluded: false,
      version: 0,
    };
  });
}
function App() {
  const [route, setRoute] = useState(
    paths.includes(location.pathname) ? location.pathname : "/dashboard",
  );
  const [demo, setDemo] = useState(false),
    [me, setMe] = useState<Me | null>(null),
    [loading, setLoading] = useState(true),
    [error, setError] = useState(""),
    [notice, setNotice] = useState("");
  const [sessions, setSessions] = useState<Session[]>([]),
    [games, setGames] = useState<Game[]>([]),
    [summary, setSummary] = useState<Summary>(empty),
    [heat, setHeat] = useState<Summary>(empty);
  const [from, setFrom] = useState(month()),
    [to, setTo] = useState(plusDay(today())),
    [q, setQ] = useState(""),
    [sort, setSort] = useState("reported"),
    [selected, setSelected] = useState<Session | null>(null),
    [selectedGame, setSelectedGame] = useState<Game | null>(null);
  const [consent, setConsent] = useState(false),
    [busy, setBusy] = useState(false),
    [cursor, setCursor] = useState(""),
    [gaps, setGaps] = useState<
      { startAt: string; endAt: string; reason: string }[]
    >([]),
    [libraryMore, setLibraryMore] = useState(false);
  const [name, setName] = useState(""),
    [zone, setZone] = useState("Asia/Seoul"),
    [theme, setTheme] = useState(localStorage.getItem("gc-theme") || "dark");
  function nav(p: string) {
    history.pushState({}, "", p);
    setRoute(p);
    setSelectedGame(null);
    setQ("");
  }
  useEffect(() => {
    const f = () =>
      setRoute(
        paths.includes(location.pathname) ? location.pathname : "/dashboard",
      );
    window.addEventListener("popstate", f);
    return () => window.removeEventListener("popstate", f);
  }, []);
  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    localStorage.setItem("gc-theme", theme);
  }, [theme]);
  useEffect(() => {
    if (!selected && !selectedGame) return;
    const previous = document.activeElement as HTMLElement;
    const handler = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        setSelected(null);
        setSelectedGame(null);
      }
      if (e.key === "Tab") {
        const els = Array.from(
          document.querySelectorAll<HTMLElement>(
            ".modal button:not([disabled]),.modal a[href],.modal input",
          ),
        );
        if (!els.length) return;
        const first = els[0],
          last = els[els.length - 1];
        if (e.shiftKey && document.activeElement === first) {
          e.preventDefault();
          last.focus();
        } else if (!e.shiftKey && document.activeElement === last) {
          e.preventDefault();
          first.focus();
        }
      }
    };
    document.addEventListener("keydown", handler);
    return () => {
      document.removeEventListener("keydown", handler);
      previous?.focus();
    };
  }, [selected, selectedGame]);
  useEffect(() => {
    if (notice) {
      const t = setTimeout(() => setNotice(""), 5000);
      return () => clearTimeout(t);
    }
  }, [notice]);
  async function reload() {
    if (demo) return;
    setError("");
    try {
      const m = await api<Me>("/me");
      setMe(m);
      setName(m.displayName);
      setZone(m.timezone);
      const query = `from=${from}&to=${to}`;
      const [s, g, a, h] = await Promise.all([
        api<{ data: Session[]; nextCursor: string; gaps: typeof gaps }>(
          "/sessions?" + query,
        ),
        api<Game[]>("/library"),
        api<Summary>("/analytics/summary?" + query),
        api<Summary>("/analytics/heatmap?year=" + new Date().getFullYear()),
      ]);
      setSessions(s.data);
      setCursor(s.nextCursor);
      setGaps(s.gaps);
      setGames(g);
      setLibraryMore(g.length === 100);
      setSummary(a);
      setHeat(h);
    } catch (e) {
      if ((e as { status: number }).status === 401) setMe(null);
      else setError((e as Error).message);
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => {
    void reload();
  }, [from, to, demo]);
  useEffect(() => {
    const t = setInterval(() => {
      if (!document.hidden && !busy && !selected && route !== "/settings")
        void reload();
    }, 60000);
    return () => clearInterval(t);
  }, [demo, from, to, busy, selected, route]);
  useEffect(() => {
    if (!demo) return;
    const d = sessions.filter((s) => !s.excluded);
    const a: Summary = {
      seconds: 0,
      days: {},
      games: {},
      hours: {},
      weekdays: {},
    };
    d.forEach((s) => {
      const n = seconds(s),
        day = new Date(s.startedAt).toLocaleDateString("sv-SE", {
          timeZone: "Asia/Seoul",
        });
      a.seconds += n;
      a.days[day] = (a.days[day] || 0) + n;
      a.games[s.gameId] = (a.games[s.gameId] || 0) + n;
    });
    setSummary(a);
    setHeat(a);
  }, [demo, sessions]);
  function sample() {
    setDemo(true);
    setLoading(false);
    setMe({
      displayName: "플레이어",
      timezone: "Asia/Seoul",
      trackingEnabled: false,
      version: 0,
      libraryStatus: "AVAILABLE",
      collectorEnabled: false,
      tracking: { status: "PAUSED", lastSuccess: null, active: null },
    });
    setSessions(makeDemo());
    setGames(demoGames);
    setName("플레이어");
    setCursor("");
    setError("");
  }
  async function action(
    fn: () => Promise<unknown>,
    message: string,
    refresh = true,
  ) {
    setBusy(true);
    setError("");
    try {
      await fn();
      setNotice(message);
      if (refresh) await reload();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  function time(s: string) {
    return new Date(s).toLocaleTimeString("ko-KR", {
      timeZone: me?.timezone || "Asia/Seoul",
      hour: "2-digit",
      minute: "2-digit",
      hour12: false,
    });
  }
  function date(s: string) {
    return new Date(s).toLocaleDateString("ko-KR", {
      timeZone: me?.timezone || "Asia/Seoul",
      month: "long",
      day: "numeric",
      weekday: "short",
    });
  }
  const picture = (g: string) =>
    `https://cdn.akamai.steamstatic.com/steam/apps/${g}/header.jpg`;
  const filteredGames = games
    .filter((g) => g.name.toLowerCase().includes(q.toLowerCase()))
    .sort((a, b) =>
      sort === "name"
        ? a.name.localeCompare(b.name)
        : sort === "tracked"
          ? b.trackedSeconds - a.trackedSeconds
          : b.reportedMinutes - a.reportedMinutes,
    );
  const state = !me?.trackingEnabled
    ? "일시중지"
    : !me.collectorEnabled
      ? "수집기 설정 대기"
      : !me.tracking.lastSuccess
        ? "첫 관측 대기"
        : Date.now() - Date.parse(me.tracking.lastSuccess) > 150000
          ? "수집 지연"
          : me.tracking.status === "PLAYING"
            ? "게임 관측 중"
            : me.tracking.status === "UNOBSERVABLE"
              ? "현재 조회 불가"
              : "다음 플레이를 기다리는 중";
  const validActive =
    me?.tracking.active && state === "게임 관측 중" ? me.tracking.active : null;
  const totalGames = Object.keys(summary.games).length;
  const dates = Array.from({ length: 365 }, (_, i) => {
    const d = new Date(new Date().getFullYear(), 0, 1 + i, 12);
    return d.toLocaleDateString("sv-SE");
  });
  function tile(s: Session) {
    return (
      <button
        key={s.id}
        className={"session " + (s.excluded ? "excluded" : "")}
        onClick={() => setSelected(s)}
      >
        <img
          src={picture(s.gameId)}
          alt=""
          onError={(e) => {
            e.currentTarget.style.visibility = "hidden";
          }}
        />
        <span className="session-title">
          <strong>{s.name}</strong>
          <small>
            {date(s.startedAt)} · {time(s.startedAt)}경 —{" "}
            {s.endedAt ? time(s.endedAt) + "경" : "관측 중"}
          </small>
        </span>
        <span className="session-time">
          약 {duration(seconds(s))}
          <small>
            {s.excluded
              ? "통계 제외"
              : s.quality === "PARTIAL"
                ? "일부 기록"
                : "관측 추정"}
          </small>
        </span>
        <ChevronRight size={18} />
      </button>
    );
  }
  if (loading)
    return (
      <div className="loading">
        <BookOpen /> 기록을 불러오는 중입니다.
      </div>
    );
  if (!me)
    return (
      <div className="landing">
        <div className="brand">
          <BookOpen />
          <b>GAME CHRONICLE</b>
        </div>
        <div className="intro">
          <span className="eyebrow">YOUR GAMES. YOUR STORY.</span>
          <h1>
            플레이는 끝나도,
            <br />
            이야기는 남으니까.
          </h1>
          <p>
            어제의 모험부터 오래 사랑한 게임까지.
            <br />
            Steam과 연결하고 나만의 게임 연대기를 만들어 보세요.
          </p>
          {(error || location.search.includes("auth=failed")) && (
            <div role="alert" className="error">
              {error || "Steam 인증에 실패했습니다. 다시 시도해 주세요."}
            </div>
          )}
          <a className="primary" href="/auth/steam/start">
            <Gamepad2 size={20} /> Steam으로 시작하기 <ArrowUpRight size={19} />
          </a>
          <button className="text-button" onClick={sample}>
            샘플 기록 둘러보기 <ChevronRight size={16} />
          </button>
          <p className="disclaimer">
            시작·종료 시각은 Steam 공개 상태를 관측한 추정값입니다.
            <br />
            비공개·오프라인 상태나 수집 장애로 일부 기록이 누락될 수 있습니다.
          </p>
        </div>
        <div className="intro-footer">
          <span>한 번의 연결, 차곡차곡 쌓이는 기록</span>
          <span>
            PRIVATE BY DEFAULT <ShieldCheck size={16} />
          </span>
        </div>
      </div>
    );
  return (
    <div className="shell">
      <aside>
        <a
          className="brand"
          href="/dashboard"
          onClick={(e) => {
            e.preventDefault();
            nav("/dashboard");
          }}
        >
          <span className="brand-icon">
            <BookOpen size={21} />
          </span>
          <b>
            GAME
            <br />
            CHRONICLE
          </b>
        </a>
        <span className="nav-caption">MY CHRONICLE</span>
        <nav>
          {paths.map((p, i) => {
            const Icon = icons[i];
            return (
              <button
                key={p}
                className={route === p ? "active" : ""}
                onClick={() => nav(p)}
              >
                <Icon size={19} />
                <span>{labels[i]}</span>
                {route === p && <span className="nav-dot" />}
              </button>
            );
          })}
        </nav>
        <div className="sidebar-bottom">
          <div className="private">
            <ShieldCheck size={18} />
            <div>
              나만 볼 수 있는 기록<small>모든 기록은 기본 비공개입니다.</small>
            </div>
          </div>
          <div className="profile">
            <span className="avatar">{me.displayName.slice(0, 1)}</span>
            <div>
              <b>{me.displayName}</b>
              <small>{demo ? "샘플 계정" : "Steam 연결됨"}</small>
            </div>
            <button
              title="테마 변경"
              onClick={() => setTheme(theme === "dark" ? "light" : "dark")}
            >
              {theme === "dark" ? <Sun size={18} /> : <Moon size={18} />}
            </button>
          </div>
        </div>
      </aside>
      <main>
        <header>
          <span>
            내 게임 공간 <ChevronRight size={13} />{" "}
            {labels[paths.indexOf(route)]}
          </span>
          <span className="header-right">
            {new Date().toLocaleDateString("ko-KR", {
              month: "long",
              day: "numeric",
              weekday: "long",
            })}
            <span className="avatar small">{me.displayName.slice(0, 1)}</span>
          </span>
        </header>
        <div className="content">
          {demo && (
            <div className="demo-banner">
              <span>
                샘플 모드 · 가상 데이터입니다. 실제 게임은 수집하지 않습니다.
              </span>
              <a href="/auth/steam/start">
                내 Steam 연결하기 <ArrowUpRight size={15} />
              </a>
            </div>
          )}
          {error && (
            <div role="alert" className="error">
              {error} <button onClick={() => void reload()}>다시 시도</button>
            </div>
          )}
          {notice && (
            <div role="status" className="toast">
              {notice}
            </div>
          )}
          <div className="page-heading">
            <div>
              <span className="eyebrow">
                {route === "/dashboard"
                  ? "CONTINUE YOUR STORY"
                  : "YOUR PERSONAL ARCHIVE"}
              </span>
              <h1>
                {route === "/dashboard"
                  ? `${me.displayName}님의 게임 기록`
                  : labels[paths.indexOf(route)]}
              </h1>
              <p>
                {route === "/dashboard"
                  ? "좋아하는 세계에서 보낸 시간, 여기에 모아두었어요."
                  : route === "/timeline"
                    ? "한 번의 플레이가 하나의 이야기가 됩니다."
                    : route === "/library"
                      ? "지금까지 함께한 게임들을 만나보세요."
                      : route === "/analytics"
                        ? "쌓인 기록으로 발견하는 나의 플레이 패턴."
                        : "기록하는 방식은 언제든 직접 선택할 수 있어요."}
              </p>
            </div>
            <button
              className="secondary"
              disabled={busy || demo}
              onClick={() => void reload()}
            >
              <RefreshCw size={16} /> 새로고침
            </button>
          </div>
          <div className="status-bar">
            <span className="status-icon">
              <Gamepad2 size={20} />
            </span>
            <div>
              <strong>{state}</strong>
              <span>
                {me.tracking.lastSuccess
                  ? `마지막 정상 확인 ${date(me.tracking.lastSuccess)} ${time(me.tracking.lastSuccess)}`
                  : "추적 동의 후 첫 기록을 기다립니다."}{" "}
                · 관측 주기 60초
              </span>
            </div>
            <button onClick={() => nav("/settings")}>
              수집 설정 <ChevronRight size={16} />
            </button>
          </div>
          {["/dashboard", "/timeline", "/analytics"].includes(route) && (
            <div className="range">
              <label>
                시작일{" "}
                <input
                  type="date"
                  value={from}
                  onChange={(e) => setFrom(e.target.value)}
                />
              </label>
              <span>—</span>
              <label>
                종료일 (미포함){" "}
                <input
                  type="date"
                  value={to}
                  onChange={(e) => setTo(e.target.value)}
                />
              </label>
              <small>
                {me.timezone} 기준{demo ? " · 샘플은 전체 예시 기간 표시" : ""}
              </small>
            </div>
          )}
          {route === "/dashboard" && (
            <>
              <div className="metrics">
                <article>
                  <span>선택 기간 관측 시간</span>
                  <h2>{duration(summary.seconds)}</h2>
                  <small>관측 공백을 제외한 추정 시간</small>
                  <Clock3 className="metric-icon" />
                </article>
                <article>
                  <span>플레이한 게임</span>
                  <h2>
                    {totalGames}
                    <em>개</em>
                  </h2>
                  <small>유효 관측 구간이 있는 게임</small>
                  <Gamepad2 className="metric-icon" />
                </article>
                <article>
                  <span>기록이 남은 날</span>
                  <h2>
                    {Object.values(summary.days).filter((n) => n > 0).length}
                    <em>일</em>
                  </h2>
                  <small>짧은 플레이도 소중한 기록</small>
                  <BookOpen className="metric-icon" />
                </article>
              </div>
              <div className="dashboard-grid">
                <section className="panel">
                  <div className="section-title">
                    <h2>최근 플레이</h2>
                    <button onClick={() => nav("/timeline")}>
                      전체 기록 <ArrowUpRight size={16} />
                    </button>
                  </div>
                  {validActive && (
                    <div className="active-game">
                      <Play size={18} />
                      {validActive.name} · 최근 관측된 게임
                    </div>
                  )}
                  {sessions.length ? (
                    sessions.slice(0, 5).map(tile)
                  ) : (
                    <div className="empty">
                      <Clock3 />
                      <h3>첫 모험을 기다리고 있어요</h3>
                      <p>
                        설정에서 추적을 활성화하고 게임을 실행해 보세요.
                        <br />
                        연결 전의 날짜별 기록은 복원하지 않습니다.
                      </p>
                      <button
                        className="secondary"
                        onClick={() => nav("/settings")}
                      >
                        추적 설정 열기
                      </button>
                    </div>
                  )}
                </section>
                <section className="panel">
                  <div className="section-title">
                    <h2>이번 기록의 주인공</h2>
                    <span className="muted">게임별</span>
                  </div>
                  {Object.entries(summary.games)
                    .sort((a, b) => b[1] - a[1])
                    .slice(0, 4)
                    .map(([id, n], i) => (
                      <div className="top-game" key={id}>
                        <span className="rank">0{i + 1}</span>
                        <div>
                          <strong>
                            {games.find((g) => g.gameId === id)?.name ||
                              sessions.find((s) => s.gameId === id)?.name ||
                              id}
                          </strong>
                          <div className="bar">
                            <i
                              style={{
                                width: `${summary.seconds ? (n / summary.seconds) * 100 : 0}%`,
                              }}
                            />
                          </div>
                          <small>약 {duration(n)}</small>
                        </div>
                      </div>
                    ))}
                  {!totalGames && (
                    <div className="empty compact">
                      <Gamepad2 />
                      <p>기록이 쌓이면 게임별 비중을 보여드려요.</p>
                    </div>
                  )}
                  <div className="note">
                    <ShieldCheck size={17} />
                    <span>
                      Steam 평생 누적시간은 관측 기록에 더하지 않습니다.
                    </span>
                  </div>
                </section>
              </div>
            </>
          )}
          {(route === "/dashboard" || route === "/analytics") && (
            <section className="panel heatmap-panel">
              <div className="section-title">
                <h2>{new Date().getFullYear()} 플레이 캘린더</h2>
                <span className="muted">관측 기록 기준</span>
              </div>
              <div className="heatmap-scroll">
                <div className="heatmap">
                  {dates.map((d) => {
                    const n = heat.days[d] || 0;
                    return (
                      <button
                        title={`${d} · ${n ? duration(n) : "관측 기록 없음"}`}
                        aria-label={`${d} ${duration(n)}`}
                        className={
                          "heat h" +
                          (n === 0
                            ? 0
                            : n < 3600
                              ? 1
                              : n < 7200
                                ? 2
                                : n < 14400
                                  ? 3
                                  : 4)
                        }
                        key={d}
                        onClick={() => {
                          setFrom(d);
                          setTo(plusDay(d));
                          nav("/timeline");
                        }}
                      />
                    );
                  })}
                </div>
              </div>
              <div className="heatmap-footer">
                <small>빈 칸은 게임을 하지 않았다는 의미가 아닙니다.</small>
                <span>
                  적음 <i className="heat h0" />
                  <i className="heat h1" />
                  <i className="heat h2" />
                  <i className="heat h3" />
                  <i className="heat h4" /> 많음
                </span>
              </div>
            </section>
          )}
          {route === "/timeline" && (
            <section className="panel">
              <div className="section-title">
                <h2>플레이 타임라인</h2>
                <span className="muted">시간은 추정값입니다</span>
              </div>
              {sessions.length ? (
                sessions.map(tile)
              ) : (
                <div className="empty">
                  <Clock3 />
                  <h3>이 기간에 관측된 기록이 없어요</h3>
                  <p>다른 날짜를 선택하거나 추적 상태를 확인해 주세요.</p>
                </div>
              )}
              {cursor && (
                <button
                  className="secondary"
                  onClick={() =>
                    void action(
                      async () => {
                        const s = await api<{
                          data: Session[];
                          nextCursor: string;
                        }>(`/sessions?from=${from}&to=${to}&cursor=${cursor}`);
                        setSessions((v) => [...v, ...s.data]);
                        setCursor(s.nextCursor);
                      },
                      "",
                      false,
                    )
                  }
                >
                  다음 기록
                </button>
              )}
              {gaps.length > 0 && (
                <div className="gap-list">
                  <h3>관측 공백</h3>
                  {gaps.map((g, i) => (
                    <p key={i}>
                      {date(g.startAt)} {time(g.startAt)} — {time(g.endAt)} ·
                      시간 합계에서 제외
                    </p>
                  ))}
                </div>
              )}
            </section>
          )}
          {route === "/library" && (
            <>
              <div className="library-controls">
                <label className="search">
                  <Search size={18} />
                  <input
                    placeholder="게임 이름으로 검색"
                    value={q}
                    onChange={(e) => setQ(e.target.value)}
                  />
                </label>
                <select
                  aria-label="정렬"
                  value={sort}
                  onChange={(e) => setSort(e.target.value)}
                >
                  <option value="reported">Steam 누적시간순</option>
                  <option value="tracked">관측시간순</option>
                  <option value="name">이름순</option>
                </select>
              </div>
              {me.libraryStatus !== "AVAILABLE" && !demo && (
                <p className="note">
                  {me.libraryStatus === "UNKNOWN"
                    ? "아직 게임 목록을 동기화하지 않았습니다."
                    : "현재 게임 목록을 조회할 수 없습니다. 기존 목록을 유지합니다."}
                </p>
              )}
              <div className="game-grid">
                {filteredGames.map((g) => (
                  <button
                    className="game-card"
                    key={g.gameId}
                    onClick={() => setSelectedGame(g)}
                  >
                    <img src={picture(g.gameId)} alt="" />
                    <div>
                      <h3>{g.name}</h3>
                      <div>
                        <span>Steam 누적</span>
                        <b>{duration(g.reportedMinutes * 60)}</b>
                      </div>
                      <div>
                        <span>관측 기록</span>
                        <strong>{duration(g.trackedSeconds)}</strong>
                      </div>
                    </div>
                  </button>
                ))}
              </div>
              {!filteredGames.length && (
                <div className="panel empty">
                  <Library />
                  <h3>
                    {q ? "검색한 게임이 없어요" : "아직 가져온 게임이 없어요"}
                  </h3>
                  <p>추적을 활성화하면 Steam 게임 목록을 동기화합니다.</p>
                </div>
              )}
              {libraryMore && (
                <button
                  className="secondary"
                  onClick={async () => {
                    try {
                      const more = await api<Game[]>(
                        "/library?offset=" + games.length,
                      );
                      setGames((v) => [...v, ...more]);
                      setLibraryMore(more.length === 100);
                    } catch (e) {
                      setError((e as Error).message);
                    }
                  }}
                >
                  게임 더 보기
                </button>
              )}
            </>
          )}
          {route === "/analytics" && (
            <div className="dashboard-grid">
              <section className="panel">
                <h2>시간대별 관측 시간</h2>
                <div className="hour-chart">
                  {Array.from({ length: 24 }, (_, h) => (
                    <div
                      key={h}
                      title={`${h}시 ${duration(summary.hours[h] || 0)}`}
                    >
                      <i
                        style={{
                          height:
                            Math.max(
                              2,
                              ((summary.hours[h] || 0) /
                                Math.max(1, ...Object.values(summary.hours))) *
                                160,
                            ) + "px",
                        }}
                      />
                      <small>{h % 4 === 0 ? h : ""}</small>
                    </div>
                  ))}
                </div>
                <p className="muted">
                  각 시간대에 겹친 유효 구간의 합계입니다.
                </p>
              </section>
              <section className="panel">
                <h2>요일별 관측 시간</h2>
                {[
                  "MONDAY",
                  "TUESDAY",
                  "WEDNESDAY",
                  "THURSDAY",
                  "FRIDAY",
                  "SATURDAY",
                  "SUNDAY",
                ].map((d, i) => (
                  <div className="weekday" key={d}>
                    <span>{"월화수목금토일"[i]}</span>
                    <div className="bar">
                      <i
                        style={{
                          width: `${((summary.weekdays[d] || 0) / Math.max(1, ...Object.values(summary.weekdays))) * 100}%`,
                        }}
                      />
                    </div>
                    <small>{duration(summary.weekdays[d] || 0)}</small>
                  </div>
                ))}
              </section>
            </div>
          )}
          {route === "/settings" && (
            <div className="settings-grid">
              <section className="panel">
                <h2>자동 기록</h2>
                <p>
                  SteamID, 공개 게임 목록·누적시간, 관측한 게임 상태와 시각을
                  저장합니다. 추적은 브라우저를 닫거나 로그아웃해도 유지됩니다.
                </p>
                <p className="muted">
                  연동 이전 상세 기록은 복원하지 않으며, 수집을 중지한 기간은
                  자동으로 채우지 않습니다. 기록은 Supabase 서울 리전에
                  저장합니다.
                </p>
                {!me.trackingEnabled && (
                  <label className="consent">
                    <input
                      type="checkbox"
                      checked={consent}
                      onChange={(e) => setConsent(e.target.checked)}
                    />{" "}
                    수집 범위와 한계를 확인했고 자동 기록에 동의합니다. (정책
                    1.0)
                  </label>
                )}
                <button
                  className={me.trackingEnabled ? "secondary" : "primary"}
                  disabled={busy || demo || (!me.trackingEnabled && !consent)}
                  onClick={() => {
                    if (
                      me.trackingEnabled &&
                      !confirm(
                        "자동 기록을 중지할까요? 진행 중 기록은 마지막 관측 시각까지만 보관합니다.",
                      )
                    )
                      return;
                    void action(
                      () =>
                        api("/me/tracking", "PATCH", {
                          enabled: !me.trackingEnabled,
                          policyVersion: "1.0",
                        }),
                      "추적 설정을 저장했습니다.",
                    );
                  }}
                >
                  {me.trackingEnabled ? (
                    <Pause size={18} />
                  ) : (
                    <Play size={18} />
                  )}{" "}
                  {me.trackingEnabled ? "추적 일시중지" : "자동 기록 시작"}
                </button>
                <hr />
                <h3>Steam 데이터 확인</h3>
                <p className="muted">
                  프로필 공개와 게임 세부 정보 공개 설정을 각각 확인해 주세요.
                  조회 실패만으로 비공개 여부를 단정할 수 없습니다.
                </p>
                <button
                  className="secondary"
                  disabled={busy || demo || !me.trackingEnabled}
                  onClick={() =>
                    void action(
                      () => api("/me/steam-sync", "POST"),
                      "동기화를 요청했습니다. 잠시 후 새로고침해 주세요.",
                    )
                  }
                >
                  <RefreshCw size={16} /> 게임 목록 동기화
                </button>
              </section>
              <section className="panel">
                <h2>프로필과 시간대</h2>
                <label className="field">
                  표시 이름
                  <input
                    value={name}
                    maxLength={100}
                    onChange={(e) => setName(e.target.value)}
                  />
                </label>
                <label className="field">
                  통계 시간대
                  <select
                    value={zone}
                    onChange={(e) => setZone(e.target.value)}
                  >
                    {[
                      "Asia/Seoul",
                      "Asia/Tokyo",
                      "UTC",
                      "America/New_York",
                      "Europe/London",
                    ].map((z) => (
                      <option key={z}>{z}</option>
                    ))}
                  </select>
                </label>
                <button
                  className="secondary"
                  disabled={busy || demo}
                  onClick={() =>
                    void action(
                      () =>
                        api("/me/settings", "PATCH", {
                          displayName: name,
                          timezone: zone,
                          expectedVersion: me.version,
                        }),
                      "설정을 저장했습니다.",
                    )
                  }
                >
                  변경 저장
                </button>
                <hr />
                <h3>내 기록 가져가기</h3>
                <p className="muted">
                  전체 세션을 내려받습니다. JSON에는 관측 구간도 포함됩니다.
                </p>
                <div className="button-row">
                  {["csv", "json"].map((f) => (
                    <a
                      className={"secondary " + (demo ? "disabled" : "")}
                      key={f}
                      href={
                        demo
                          ? undefined
                          : "/api/v1/exports/sessions?format=" + f
                      }
                    >
                      <Download size={16} />
                      {f.toUpperCase()}
                    </a>
                  ))}
                </div>
                <hr />
                <button
                  className="text-button"
                  onClick={() =>
                    demo
                      ? location.assign("/")
                      : void action(async () => {
                          await api("/auth/logout", "POST");
                          csrf = null;
                          setMe(null);
                          location.assign("/");
                        }, "")
                  }
                >
                  <LogOut size={16} />
                  {demo ? "샘플 종료" : "로그아웃 (자동 추적 유지)"}
                </button>
                <button
                  className="danger text-button"
                  disabled={busy || demo}
                  onClick={() => {
                    if (
                      prompt(
                        "계정과 모든 기록을 영구 삭제합니다. 삭제하려면 DELETE를 입력하세요.",
                      ) !== "DELETE"
                    )
                      return;
                    void action(async () => {
                      await api("/me", "DELETE", { confirmation: "DELETE" });
                      location.assign("/");
                    }, "");
                  }}
                >
                  계정과 기록 삭제
                </button>
                <p className="muted">
                  삭제는 최근 10분 내 Steam 인증이 필요합니다. 본 데이터는 즉시
                  삭제되며, 운영 백업이 있는 경우 백업 보관 정책에 따라 잔존할
                  수 있습니다.
                </p>
              </section>
            </div>
          )}
          <footer>
            GAME CHRONICLE <span>당신이 지나온 세계를 기록합니다.</span>
            <span>관측 기반 추정 · 비공개 기록</span>
          </footer>
        </div>
      </main>
      {(selected || selectedGame) && (
        <div
          className="modal-backdrop"
          onClick={() => {
            setSelected(null);
            setSelectedGame(null);
          }}
        >
          <section
            role="dialog"
            aria-modal="true"
            aria-label="기록 상세"
            className="modal"
            onClick={(e) => e.stopPropagation()}
          >
            <button
              className="close"
              autoFocus
              onClick={() => {
                setSelected(null);
                setSelectedGame(null);
              }}
            >
              <X />
            </button>
            {selected ? (
              <>
                <span className="eyebrow">PLAY SESSION</span>
                <h2>{selected.name}</h2>
                <p>
                  {date(selected.startedAt)} · {time(selected.startedAt)}경
                </p>
                <h1>약 {duration(seconds(selected))}</h1>
                <span className="badge">
                  {selected.quality === "PARTIAL" ? "일부 기록" : "관측 추정"}
                </span>
                <h3>유효 관측 구간</h3>
                {selected.segments.map((g, i) => (
                  <div className="segment" key={i}>
                    <span>
                      {time(g.start)} — {time(g.end)}
                    </span>
                    <b>
                      {duration(
                        (Date.parse(g.end) - Date.parse(g.start)) / 1000,
                      )}
                    </b>
                  </div>
                ))}
                <p className="muted">
                  관측이 끊긴 구간은 합계에서 제외됩니다. 실제 실행·종료 시각과
                  다를 수 있습니다.
                </p>
                <button
                  className="secondary"
                  disabled={busy}
                  onClick={() => {
                    if (demo) {
                      setSessions((v) =>
                        v.map((s) =>
                          s.id === selected.id
                            ? { ...s, excluded: !s.excluded }
                            : s,
                        ),
                      );
                      setSelected(null);
                    } else
                      void action(async () => {
                        await api(
                          "/sessions/" + selected.id + "/exclusion",
                          "PATCH",
                          {
                            excluded: !selected.excluded,
                            expectedVersion: selected.version,
                          },
                        );
                        setSelected(null);
                      }, "통계 반영 여부를 변경했습니다.");
                  }}
                >
                  {selected.excluded
                    ? "통계에 다시 포함"
                    : "이 기록을 통계에서 제외"}
                </button>
              </>
            ) : (
              selectedGame && (
                <>
                  <img
                    className="detail-cover"
                    src={picture(selectedGame.gameId)}
                    alt=""
                  />
                  <h2>{selectedGame.name}</h2>
                  <p>
                    Steam 누적: {duration(selectedGame.reportedMinutes * 60)}
                  </p>
                  <p>
                    연결 당시 기준값:{" "}
                    {duration(selectedGame.baselineMinutes * 60)}
                  </p>
                  <p>관측 기록: {duration(selectedGame.trackedSeconds)}</p>
                  <small>누적시간과 관측시간은 출처가 다른 값입니다.</small>
                </>
              )
            )}
          </section>
        </div>
      )}
    </div>
  );
}
createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
