import { useEffect, useState } from "react";
import { SkillIcon } from "@/components/SkillIcon";
import { useSettingsStore } from "@/stores/useSettingsStore";
import { getSkillName } from "@/constants/codes";
import { formatRemainShort } from "@/utils/formatRemain";

type TrackedBuff = {
  skillCode: number;
  name?: string | null;
  remainingMs: number;
  durationMs: number;
};

type TrackerStatus = {
  buffs: TrackedBuff[];
};

const emptyStatus: TrackerStatus = { buffs: [] };

function parseStatus(raw: unknown): TrackerStatus {
  if (typeof raw !== "string" || !raw) return emptyStatus;
  try {
    const parsed = JSON.parse(raw) as Partial<TrackerStatus>;
    return {
      buffs: Array.isArray(parsed.buffs) ? parsed.buffs : [],
    };
  } catch {
    return emptyStatus;
  }
}

export function TrackerHud() {
  const codes = useSettingsStore((s) => s.trackedBuffCodes);
  const [status, setStatus] = useState<TrackerStatus>(emptyStatus);

  useEffect(() => {
    const tick = () => {
      const raw = window.javaBridge?.getTrackerStatus?.();
      setStatus(parseStatus(raw));
    };
    tick();
    const id = setInterval(tick, 400);
    return () => clearInterval(id);
  }, []);

  const byCode = new Map(status.buffs.map((buff) => [buff.skillCode, buff]));

  return (
    <div className="flex flex-wrap items-end gap-1.5 rounded-lg border border-amber-500/20 bg-[#0b0d17]/80 px-2 py-1.5">
      {codes.map((code) => {
        const live = byCode.get(code);
        const remaining = live?.remainingMs ?? 0;
        const duration = live?.durationMs || 1;
        const ratio = remaining > 0 ? Math.min(1, remaining / duration) : 0;
        const active = remaining > 0;
        return (
          <div
            key={code}
            className={`relative flex h-10 w-10 items-center justify-center rounded-md border ${
              active ? "border-amber-400/50 bg-black/50" : "border-white/10 bg-black/30 opacity-50"
            }`}
            title={`${live?.name || getSkillName(code) || code} · ${active ? formatRemainShort(remaining) : "준비"}`}>
            <SkillIcon code={code} size={28} />
            {active && (
              <span className="absolute inset-x-0 bottom-0 text-center text-[8px] font-bold text-amber-200 bg-black/60">
                {Math.ceil(remaining / 1000)}
              </span>
            )}
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
