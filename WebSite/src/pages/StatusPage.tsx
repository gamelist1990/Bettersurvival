import { useEffect, useMemo, useRef, useState } from 'react';
import { SectionHeader } from '../components/common/SectionHeader';
import { MOCK_MODE } from '../features/webservice/api';

type StatusData = {
  updatedAt: number;
  intervalSeconds: number;
  resetAt: number;
  cpu: { process: number; system: number; processAvailable: boolean; systemAvailable: boolean; cores: number };
  memory: { usedBytes: number; allocatedBytes: number; committedBytes: number; usedPercent: number };
  power: { watts: number; energyWh: number; estimate: boolean };
  stability: { tps: number; mspt: number; percent: number; avgTps: number; minTps: number };
  uptime: { millis: number; text: string };
  traffic: { bytesOut: number; bytesIn: number; bytesTotal: number; requests: number; sinceReset: number };
  server: { name: string; version: string; playersOnline: number; playersMax: number };
};

const MOCK_STATUS: StatusData = {
  updatedAt: Date.now(),
  intervalSeconds: 5,
  resetAt: Date.now() - 3_600_000 * 6,
  cpu: { process: 0.23, system: 0.41, processAvailable: true, systemAvailable: true, cores: 8 },
  memory: { usedBytes: 3_650_000_000, allocatedBytes: 8_000_000_000, committedBytes: 5_100_000_000, usedPercent: 45.6 },
  power: { watts: 38.4, energyWh: 214.7, estimate: true },
  stability: { tps: 19.87, mspt: 12.4, percent: 99.35, avgTps: 19.72, minTps: 17.9 },
  uptime: { millis: 3_600_000 * 27 + 60_000 * 14, text: '1d 3h 14m 0s' },
  traffic: { bytesOut: 482_300_000, bytesIn: 12_400_000, bytesTotal: 494_700_000, requests: 18_204, sinceReset: Date.now() - 3_600_000 * 6 },
  server: { name: 'PEX Survival', version: 'git-Paper-1.21', playersOnline: 7, playersMax: 40 },
};

export function StatusPage() {
  const [data, setData] = useState<StatusData | null>(MOCK_MODE ? MOCK_STATUS : null);
  const [error, setError] = useState(false);
  const [tick, setTick] = useState(0);
  const intervalRef = useRef<number>(5);

  useEffect(() => {
    if (MOCK_MODE) return;
    let active = true;
    let timer: number | undefined;

    const load = async () => {
      try {
        const response = await fetch('/api/v1/serverstatus', { credentials: 'include', headers: { Accept: 'application/json' } });
        if (!response.ok) throw new Error(`status ${response.status}`);
        const payload = (await response.json()) as StatusData;
        if (!active) return;
        setData(payload);
        setError(false);
        intervalRef.current = Math.max(2, payload.intervalSeconds || 5);
      } catch {
        if (!active) return;
        setError(true);
      } finally {
        if (active) {
          timer = window.setTimeout(load, intervalRef.current * 1000);
        }
      }
    };

    void load();
    return () => {
      active = false;
      if (timer) window.clearTimeout(timer);
    };
  }, []);

  // "n秒前" 表示を1秒ごとに更新するための再描画トリガー。
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
      <div className="section-block status-page">
        <SectionHeader eyebrow="Status" title="サーバーステータス" text="サーバーの稼働状況をリアルタイムに確認できます。" />
        {error ? (
          <div className="status-empty">ステータスを取得できませんでした。サーバーが起動しているか確認してください。</div>
        ) : (
          <div className="status-empty status-empty--loading">ステータスを読み込んでいます…</div>
        )}
      </div>
    );
  }

  const cpuPct = data.cpu.process * 100;
  const sysPct = data.cpu.system * 100;
  const memPct = data.memory.usedPercent;
  const stabilityPct = data.stability.percent;

  return (
    <div className="section-block status-page">
      <SectionHeader
        eyebrow="Status"
        title="サーバーステータス"
        text={`${data.server.name} の稼働状況。数値は約${data.intervalSeconds}秒ごとにキャッシュ更新され、Webにはキャッシュから配信されます。`}
      />

      <div className="status-meta">
        <span className={`status-dot ${error ? 'is-stale' : 'is-live'}`} />
        <span className="status-meta__label">{error ? '更新停止中' : 'ライブ'}</span>
        <span className="status-meta__sep">·</span>
        <span>更新 {freshness}</span>
        <span className="status-meta__sep">·</span>
        <span>{data.server.playersOnline} / {data.server.playersMax} 人オンライン</span>
        <span className="status-meta__spacer" />
        <code className="status-reset-hint">/status reset でリセット</code>
      </div>

      <div className="status-grid">
        <StatusCard title="CPU 使用率" eyebrow="Processor" accent={pickColor(cpuPct, false)}>
          <div className="status-gauge-row">
            <Gauge value={cpuPct} label="プロセス" color={pickColor(cpuPct, false)} />
            <div className="status-substats">
              <Stat label="システム全体" value={`${sysPct.toFixed(1)}%`} />
              <MiniBar value={sysPct} color={pickColor(sysPct, false)} />
              <Stat label="コア数" value={`${data.cpu.cores} スレッド`} />
              {!data.cpu.processAvailable ? <p className="status-note">※ プロセス負荷はこの環境では取得できません</p> : null}
            </div>
          </div>
        </StatusCard>

        <StatusCard title="メモリ" eyebrow="Heap memory" accent={pickColor(memPct, false)}>
          <div className="status-gauge-row">
            <Gauge value={memPct} label="使用率" color={pickColor(memPct, false)} />
            <div className="status-substats">
              <Stat label="使用中" value={formatBytes(data.memory.usedBytes)} />
              <Stat label="割り当て (最大)" value={formatBytes(data.memory.allocatedBytes)} />
              <Stat label="確保済み" value={formatBytes(data.memory.committedBytes)} />
            </div>
          </div>
        </StatusCard>

        <StatusCard title="安定性" eyebrow="Performance" accent={pickColor(stabilityPct, true)}>
          <div className="status-headline">
            <span className="status-headline__value" style={{ color: pickColor(stabilityPct, true) }}>{data.stability.tps.toFixed(2)}</span>
            <span className="status-headline__unit">TPS</span>
          </div>
          <Bar value={stabilityPct} color={pickColor(stabilityPct, true)} />
          <div className="status-inline-stats">
            <Stat label="MSPT" value={`${data.stability.mspt.toFixed(2)} ms`} compact />
            <Stat label="平均TPS" value={data.stability.avgTps.toFixed(2)} compact />
            <Stat label="最低TPS" value={data.stability.minTps.toFixed(2)} compact />
          </div>
        </StatusCard>

        <StatusCard title="電力消費" eyebrow="Power (概算)" accent="var(--accent)">
          <div className="status-headline">
            <span className="status-headline__value" style={{ color: 'var(--accent)' }}>{data.power.watts.toFixed(1)}</span>
            <span className="status-headline__unit">W</span>
          </div>
          <div className="status-inline-stats">
            <Stat label="積算エネルギー" value={`${formatEnergy(data.power.energyWh)}`} compact />
            <Stat label="推定コア電力" value={`${data.cpu.cores} コア`} compact />
          </div>
          {data.power.estimate ? <p className="status-note">※ CPU負荷から推定した概算値です</p> : null}
        </StatusCard>

        <StatusCard title="稼働時間" eyebrow="Uptime" accent="var(--green)">
          <div className="status-uptime">{data.uptime.text}</div>
          <div className="status-inline-stats">
            <Stat label="オンライン" value={`${data.server.playersOnline} / ${data.server.playersMax}`} compact />
            <Stat label="バージョン" value={shortVersion(data.server.version)} compact />
          </div>
        </StatusCard>

        <StatusCard title="総通信量" eyebrow="Traffic" accent="var(--blue)">
          <div className="status-headline">
            <span className="status-headline__value" style={{ color: 'var(--blue)' }}>{formatBytes(data.traffic.bytesTotal)}</span>
          </div>
          <div className="status-inline-stats">
            <Stat label="送信 ↑" value={formatBytes(data.traffic.bytesOut)} compact />
            <Stat label="受信 ↓" value={formatBytes(data.traffic.bytesIn)} compact />
            <Stat label="リクエスト" value={data.traffic.requests.toLocaleString()} compact />
          </div>
          <p className="status-note">リセットから {sinceText(data.traffic.sinceReset)} 計測</p>
        </StatusCard>
      </div>
    </div>
  );
}

function StatusCard({ title, eyebrow, accent, children }: { title: string; eyebrow: string; accent: string; children: React.ReactNode }) {
  return (
    <article className="status-card" style={{ ['--card-accent' as string]: accent }}>
      <header className="status-card__head">
        <p className="status-card__eyebrow">{eyebrow}</p>
        <h3 className="status-card__title">{title}</h3>
      </header>
      <div className="status-card__body">{children}</div>
    </article>
  );
}

function Gauge({ value, label, color }: { value: number; label: string; color: string }) {
  const clamped = Math.max(0, Math.min(100, value));
  const radius = 52;
  const circumference = 2 * Math.PI * radius;
  const offset = circumference * (1 - clamped / 100);
  return (
    <div className="status-gauge">
      <svg viewBox="0 0 130 130" className="status-gauge__svg" role="img" aria-label={`${label} ${clamped.toFixed(0)}%`}>
        <circle cx="65" cy="65" r={radius} className="status-gauge__track" />
        <circle
          cx="65"
          cy="65"
          r={radius}
          className="status-gauge__value"
          style={{ stroke: color, strokeDasharray: circumference, strokeDashoffset: offset }}
        />
      </svg>
      <div className="status-gauge__center">
        <span className="status-gauge__number" style={{ color }}>{clamped.toFixed(clamped < 10 ? 1 : 0)}</span>
        <span className="status-gauge__percent">%</span>
        <span className="status-gauge__label">{label}</span>
      </div>
    </div>
  );
}

function Bar({ value, color }: { value: number; color: string }) {
  const clamped = Math.max(0, Math.min(100, value));
  return (
    <div className="status-bar" role="progressbar" aria-valuenow={Math.round(clamped)} aria-valuemin={0} aria-valuemax={100}>
      <span className="status-bar__fill" style={{ width: `${clamped}%`, background: color }} />
    </div>
  );
}

function MiniBar({ value, color }: { value: number; color: string }) {
  const clamped = Math.max(0, Math.min(100, value));
  return (
    <div className="status-minibar">
      <span className="status-minibar__fill" style={{ width: `${clamped}%`, background: color }} />
    </div>
  );
}

function Stat({ label, value, compact }: { label: string; value: string; compact?: boolean }) {
  return (
    <div className={`status-stat ${compact ? 'is-compact' : ''}`}>
      <span className="status-stat__label">{label}</span>
      <span className="status-stat__value">{value}</span>
    </div>
  );
}

// 使用率が高いほど危険 (invertGood=false)、または高いほど良い (invertGood=true) 場合で色を切り替える。
function pickColor(percent: number, higherIsBetter: boolean): string {
  const good = 'var(--green)';
  const warn = 'var(--accent)';
  const danger = '#c0392b';
  if (higherIsBetter) {
    if (percent >= 90) return good;
    if (percent >= 70) return warn;
    return danger;
  }
  if (percent >= 85) return danger;
  if (percent >= 60) return warn;
  return good;
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
  return match ? match[0] : version.slice(0, 16);
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
