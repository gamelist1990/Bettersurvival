import { useEffect, useMemo, useRef, useState } from 'react';
import { MOCK_MODE } from '../features/webservice/api';

type StatusData = {
  updatedAt: number;
  intervalSeconds: number;
  resetAt: number;
  cpu: { process: number; system: number; processAvailable: boolean; systemAvailable: boolean; cores: number };
  memory: { usedBytes: number; allocatedBytes: number; committedBytes: number; usedPercent: number };
  power: { watts: number; energyWh: number; estimate: boolean; real?: boolean; source?: string };
  stability: { tps: number; mspt: number; percent: number; avgTps: number; minTps: number };
  uptime: { millis: number; text: string };
  traffic: { bytesOut: number; bytesIn: number; bytesTotal: number; requests: number; sinceReset: number };
  server: { name: string; version: string; playersOnline: number; playersMax: number };
};

const HISTORY_CAP = 48;

const MOCK_STATUS: StatusData = {
  updatedAt: Date.now(),
  intervalSeconds: 5,
  resetAt: Date.now() - 3_600_000 * 6,
  cpu: { process: 0.23, system: 0.41, processAvailable: true, systemAvailable: true, cores: 8 },
  memory: { usedBytes: 3_650_000_000, allocatedBytes: 8_000_000_000, committedBytes: 5_100_000_000, usedPercent: 45.6 },
  power: { watts: 38.4, energyWh: 214.7, estimate: false, real: true, source: 'rapl' },
  stability: { tps: 19.87, mspt: 12.4, percent: 99.35, avgTps: 19.72, minTps: 17.9 },
  uptime: { millis: 3_600_000 * 27 + 60_000 * 14, text: '1d 3h 14m 0s' },
  traffic: { bytesOut: 482_300_000, bytesIn: 12_400_000, bytesTotal: 494_700_000, requests: 18_204, sinceReset: Date.now() - 3_600_000 * 6 },
  server: { name: 'PEX Survival', version: 'git-Paper-1.21', playersOnline: 7, playersMax: 40 },
};

const MOCK_HISTORY = Array.from({ length: HISTORY_CAP }, (_, i) => {
  const dip = i === 30 ? 17.9 : i === 31 ? 18.6 : i > 26 && i < 34 ? 19.1 : 19.9;
  return Math.min(20, dip + Math.sin(i * 0.7) * 0.12);
});

export function StatusPage() {
  const [data, setData] = useState<StatusData | null>(MOCK_MODE ? MOCK_STATUS : null);
  const [history, setHistory] = useState<number[]>(MOCK_MODE ? MOCK_HISTORY : []);
  const [error, setError] = useState(false);
  const [tick, setTick] = useState(0);
  const intervalRef = useRef(5);

  useEffect(() => {
    if (MOCK_MODE) return;
    let active = true;
    let timer: number | undefined;
    let seeded = false;

    const load = async () => {
      try {
        const response = await fetch('/api/v1/serverstatus', { credentials: 'include', headers: { Accept: 'application/json' } });
        if (!response.ok) throw new Error(`status ${response.status}`);
        const payload = (await response.json()) as StatusData;
        if (!active) return;
        setData(payload);
        setError(false);
        intervalRef.current = Math.max(2, payload.intervalSeconds || 5);
        const tps = payload.stability.tps;
        setHistory((prev) => {
          // 初回だけ現在値で薄く埋めて、以降は実サンプルを右へ流していく。
          const base = prev.length === 0 && !seeded ? Array.from({ length: 8 }, () => tps) : prev;
          seeded = true;
          return [...base, tps].slice(-HISTORY_CAP);
        });
      } catch {
        if (!active) return;
        setError(true);
      } finally {
        if (active) timer = window.setTimeout(load, intervalRef.current * 1000);
      }
    };

    void load();
    return () => {
      active = false;
      if (timer) window.clearTimeout(timer);
    };
  }, []);

  useEffect(() => {
    const id = window.setInterval(() => setTick((value) => value + 1), 1000);
    return () => window.clearInterval(id);
  }, []);

  const freshness = useMemo(() => {
    if (!data) return '';
    void tick;
    const seconds = Math.max(0, Math.round((Date.now() - data.updatedAt) / 1000));
    if (seconds <= 1) return 'たった今';
    return `${seconds}秒前`;
  }, [data, tick]);

  if (!data) {
    return (
      <div className="vt-shell">
        <div className="vt-console vt-console--empty">
          <span className="vt-eyebrow">SERVER VITALS</span>
          <p className={error ? 'vt-empty' : 'vt-empty vt-empty--loading'}>
            {error ? 'ステータスを取得できませんでした。サーバーが起動しているか確認してください。' : '稼働状況を読み込んでいます…'}
          </p>
        </div>
      </div>
    );
  }

  const tps = data.stability.tps;
  const health = tpsHealth(tps);
  const cpuProc = data.cpu.process * 100;
  const memPct = data.memory.usedPercent;

  return (
    <div className="vt-shell" style={{ ['--vt-health' as string]: health.color }}>
      <div className="vt-console">
        {/* masthead — instrument header */}
        <header className="vt-masthead">
          <div className="vt-ident">
            <span className="vt-eyebrow">SERVER VITALS</span>
            <h1 className="vt-name">{data.server.name}</h1>
          </div>
          <div className="vt-live">
            <span className={`vt-live__row ${error ? 'is-stale' : ''}`}>
              <span className="vt-live__dot" />
              {error ? '更新停止中' : '稼働中'}
              <span className="vt-live__muted">· {freshness}更新</span>
            </span>
            <span className="vt-live__players">
              <b>{data.server.playersOnline}</b> / {data.server.playersMax} 人接続中
            </span>
          </div>
        </header>

        {/* signature — live tick-rate scope (the server's pulse) */}
        <section className="vt-pulse" aria-label={`直近のTPS ${tps.toFixed(2)}`}>
          <div className="vt-pulse__reading">
            <div className="vt-pulse__now">
              <span className="vt-pulse__value">{tps.toFixed(2)}</span>
              <span className="vt-pulse__unit">TPS</span>
              <span className="vt-pulse__tag" style={{ color: health.color }}>{health.label}</span>
            </div>
            <dl className="vt-pulse__meta">
              <div><dt>MSPT</dt><dd>{data.stability.mspt.toFixed(1)}<small>ms</small></dd></div>
              <div><dt>平均</dt><dd>{data.stability.avgTps.toFixed(2)}</dd></div>
              <div><dt>最低</dt><dd>{data.stability.minTps.toFixed(2)}</dd></div>
            </dl>
          </div>
          <Scope samples={history} color={health.color} />
        </section>

        {/* load — linear meters */}
        <section className="vt-load">
          <Meter label="CPU" latin sub={`${data.cpu.system >= 0 ? `system ${(data.cpu.system * 100).toFixed(0)}% · ` : ''}${data.cpu.cores} threads`} pct={cpuProc} warnAt={70} dangerAt={88} note={!data.cpu.processAvailable ? 'この環境ではプロセス負荷を取得できません' : undefined} />
          <Meter label="RAM" latin sub={`${formatBytes(data.memory.usedBytes)} / ${formatBytes(data.memory.allocatedBytes)} 割り当て`} pct={memPct} warnAt={70} dangerAt={88} />
        </section>

        {/* readout — honest definition grid */}
        <section className="vt-readout">
          <Readout k="稼働時間" v={data.uptime.text} />
          {(() => {
            const real = data.power.real ?? !data.power.estimate;
            return (
              <Readout
                k="消費電力"
                v={`${real ? '' : '≈ '}${data.power.watts.toFixed(1)} W`}
                sub={`積算 ${formatEnergy(data.power.energyWh)} · ${real ? '実測 RAPL' : '概算'}`}
              />
            );
          })()}
          <Readout
            k="総通信量"
            v={formatBytes(data.traffic.bytesTotal)}
            sub={`↑${formatBytes(data.traffic.bytesOut)} ↓${formatBytes(data.traffic.bytesIn)} · ${data.traffic.requests.toLocaleString()} req · ${sinceText(data.traffic.sinceReset)}計測`}
          />
          <Readout k="バージョン" v={shortVersion(data.server.version)} sub={`更新間隔 ${data.intervalSeconds}s · キャッシュ配信`} />
        </section>
      </div>
    </div>
  );
}

// TPS スコープは 12〜20 の帯で描く。健全時は上端付近に張り付き、低下 (dip) がはっきり見える。
const SCOPE_FLOOR = 12;
const SCOPE_RANGE = 20 - SCOPE_FLOOR;

function Scope({ samples, color }: { samples: number[]; color: string }) {
  const W = 320;
  const H = 72;
  const n = samples.length;
  const norm = (v: number) => (Math.max(SCOPE_FLOOR, Math.min(20, v)) - SCOPE_FLOOR) / SCOPE_RANGE;
  const geom = useMemo(() => {
    if (n === 0) return { line: '', area: '', dotY: 0 };
    const xAt = (i: number) => (n === 1 ? W : (i / (n - 1)) * W);
    const yAt = (v: number) => H - norm(v) * H;
    let line = '';
    samples.forEach((v, i) => {
      line += `${i === 0 ? 'M' : 'L'}${xAt(i).toFixed(1)} ${yAt(v).toFixed(2)} `;
    });
    const area = `M0 ${H} ${samples.map((v, i) => `L${xAt(i).toFixed(1)} ${yAt(v).toFixed(2)}`).join(' ')} L${W} ${H} Z`;
    return { line, area, dotY: (1 - norm(samples[n - 1])) * 100 };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [samples, n]);

  const y15 = H - ((15 - SCOPE_FLOOR) / SCOPE_RANGE) * H;

  return (
    <div className="vt-scope" role="img" aria-label="直近のティックレート推移">
      <span className="vt-scope__baseline">20 tps</span>
      <svg viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" className="vt-scope__svg">
        <line x1="0" y1="0.6" x2={W} y2="0.6" className="vt-scope__ref vt-scope__ref--ideal" />
        <line x1="0" y1={y15} x2={W} y2={y15} className="vt-scope__ref" />
        {geom.area ? <path d={geom.area} className="vt-scope__area" style={{ fill: color }} /> : null}
        {geom.line ? <path d={geom.line} className="vt-scope__line" style={{ stroke: color }} vectorEffect="non-scaling-stroke" /> : null}
      </svg>
      {n > 0 ? <span className="vt-scope__head" style={{ top: `${geom.dotY}%`, background: color }} /> : null}
    </div>
  );
}

function Meter({ label, sub, pct, warnAt, dangerAt, latin, note }: { label: string; sub: string; pct: number; warnAt: number; dangerAt: number; latin?: boolean; note?: string }) {
  const clamped = Math.max(0, Math.min(100, pct));
  const color = clamped >= dangerAt ? '#b23b2e' : clamped >= warnAt ? 'var(--accent)' : 'var(--green)';
  return (
    <div className="vt-meter">
      <div className="vt-meter__head">
        <span className={`vt-meter__label ${latin ? 'is-latin' : ''}`}>{label}</span>
        <span className="vt-meter__pct" style={{ color }}>{clamped.toFixed(clamped < 10 ? 1 : 0)}<small>%</small></span>
      </div>
      <div className="vt-meter__track" role="progressbar" aria-valuenow={Math.round(clamped)} aria-valuemin={0} aria-valuemax={100} aria-label={label}>
        <span className="vt-meter__fill" style={{ width: `${clamped}%`, background: color }} />
        <span className="vt-meter__ticks" />
      </div>
      <span className="vt-meter__sub">{note ?? sub}</span>
    </div>
  );
}

function Readout({ k, v, sub }: { k: string; v: string; sub?: string }) {
  return (
    <div className="vt-read">
      <span className="vt-read__k">{k}</span>
      <span className="vt-read__v">{v}</span>
      {sub ? <span className="vt-read__sub">{sub}</span> : null}
    </div>
  );
}

function tpsHealth(tps: number): { color: string; label: string } {
  if (tps >= 19.5) return { color: 'var(--green)', label: '安定' };
  if (tps >= 18) return { color: 'var(--accent)', label: 'やや低下' };
  return { color: '#b23b2e', label: '不安定' };
}

function formatBytes(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes < 1024) return `${Math.max(0, Math.round(bytes))} B`;
  const units = ['KB', 'MB', 'GB', 'TB'];
  let value = bytes / 1024;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit += 1;
  }
  return `${value.toFixed(value < 10 ? 2 : 1)} ${units[unit]}`;
}

function formatEnergy(wh: number): string {
  if (wh >= 1000) return `${(wh / 1000).toFixed(2)} kWh`;
  return `${wh.toFixed(1)} Wh`;
}

function shortVersion(version: string): string {
  if (!version) return '-';
  const match = version.match(/1\.\d+(\.\d+)?/);
  return match ? match[0] : version.slice(0, 18);
}

function sinceText(resetAt: number): string {
  const seconds = Math.max(0, Math.round((Date.now() - resetAt) / 1000));
  if (seconds < 60) return `${seconds}秒`;
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes}分`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}時間`;
  return `${Math.floor(hours / 24)}日`;
}
