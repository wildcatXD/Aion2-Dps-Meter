import { useEffect, useState } from "react";
import { SkillIcon } from "@/components/SkillIcon";
import { useSettingsStore } from "@/stores/useSettingsStore";
import { getSkillName } from "@/constants/codes";
import { parseTrackerResources } from "@/components/ResourceChips";

type TrackedBuff = {
  skillCode: number;
  name?: string | null;
  remainingMs: number;
  durationMs: number;
};

type LiveStatus = {
  odeEnergy: number | null;
  shugoKeys: number | null;
  buffs: TrackedBuff[];
};

const emptyStatus: LiveStatus = { odeEnergy: null, shugoKeys: null, buffs: [] };

function parseLiveStatus(raw: unknown): LiveStatus {
  const resources = parseTrackerResources(raw);
  if (typeof raw !== "string" || !raw) return { ...resources, buffs: [] };
  try {
    const parsed = JSON.parse(raw) as { buffs?: TrackedBuff[] };
    return {
      ...resources,
      buffs: Array.isArray(parsed.buffs) ? parsed.buffs : [],
    };
  } catch {
    return { ...resources, buffs: [] };
  }
}

function Chip({ label, value }: { label: string; value: number | null }) {
  return (
    <div className="flex min-w-[48px] flex-col items-center rounded-md border border-amber-500/25 bg-black/45 px-1.5 py-0.5">
      <span className="text-[8px] font-bold tracking-wide text-amber-300/85">{label}</span>
      <span className="text-[12px] font-bold tabular-nums text-slate-100">
        {value == null ? "—" : value.toLocaleString()}
      </span>
    </div>
  );
}

/** 미터기 창에 고정된 오드·열쇠·본인 버프. 별도 추적 창이나 캐릭터 아래 HUD가 아닙니다. */
export function MeterStatusBar() {
  const showEmptyResourceChips = useSettingsStore((s) => s.showEmptyResourceChips);
  const [status, setStatus] = useState<LiveStatus>(emptyStatus);

  useEffect(() => {
    const tick = () => setStatus(parseLiveStatus(window.javaBridge?.getTrackerStatus?.()));
    tick();
    const id = setInterval(tick, 400);
    return () => clearInterval(id);
  }, []);

  const activeBuffs = status.buffs
    .filter((buff) => (buff.remainingMs ?? 0) > 0)
    .sort((a, b) => b.remainingMs - a.remainingMs)
    .slice(0, 10);
  const hasResources = status.odeEnergy != null || status.shugoKeys != null;
  if (!showEmptyResourceChips && !hasResources && activeBuffs.length === 0) return null;

  return (
    <div className="mb-2 flex flex-wrap items-end gap-1.5">
      <Chip label="오드" value={status.odeEnergy} />
      <Chip label="열쇠" value={status.shugoKeys} />
      {activeBuffs.map((buff) => {
        const remaining = buff.remainingMs;
        const duration = buff.durationMs || 1;
        const ratio = Math.min(1, remaining / duration);
        return (
          <div
            key={buff.skillCode}
            className="relative flex h-9 w-9 items-center justify-center rounded-md border border-amber-400/45 bg-black/50"
            title={`${buff.name || getSkillName(buff.skillCode) || buff.skillCode} · ${Math.ceil(remaining / 1000)}초`}>
            <SkillIcon code={buff.skillCode} size={26} />
            <span className="absolute inset-x-0 bottom-0 text-center text-[8px] font-bold text-amber-100 bg-black/65">
              {Math.ceil(remaining / 1000)}
            </span>
            <span
              className="absolute inset-x-0 bottom-0 h-0.5 bg-amber-400/80"
              style={{ width: `${ratio * 100}%` }}
            />
          </div>
        );
      })}
    </div>
  );
}
