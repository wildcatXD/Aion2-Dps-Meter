import { useEffect, useState } from "react";
import { useSettingsStore } from "@/stores/useSettingsStore";

type TrackerStatus = {
  odeEnergy: number | null;
  shugoKeys: number | null;
};

const emptyStatus: TrackerStatus = { odeEnergy: null, shugoKeys: null };

export function parseTrackerResources(raw: unknown): TrackerStatus {
  if (typeof raw !== "string" || !raw) return emptyStatus;
  try {
    const parsed = JSON.parse(raw) as Partial<TrackerStatus>;
    return {
      odeEnergy: typeof parsed.odeEnergy === "number" ? parsed.odeEnergy : null,
      shugoKeys: typeof parsed.shugoKeys === "number" ? parsed.shugoKeys : null,
    };
  } catch {
    return emptyStatus;
  }
}

function Chip({ label, value }: { label: string; value: number | null }) {
  return (
    <div className="flex min-w-[44px] flex-col items-center rounded-md border border-amber-500/20 bg-black/40 px-1.5 py-0.5">
      <span className="text-[8px] font-bold tracking-wide text-amber-300/80">{label}</span>
      <span className="text-[11px] font-bold tabular-nums text-slate-100">
        {value == null ? "—" : value.toLocaleString()}
      </span>
    </div>
  );
}

export function ResourceChips({ compact = false }: { compact?: boolean }) {
  const showEmptyResourceChips = useSettingsStore((s) => s.showEmptyResourceChips);
  const [status, setStatus] = useState<TrackerStatus>(emptyStatus);

  useEffect(() => {
    const tick = () => {
      setStatus(parseTrackerResources(window.javaBridge?.getTrackerStatus?.()));
    };
    tick();
    const id = setInterval(tick, 400);
    return () => clearInterval(id);
  }, []);

  const hasValue = status.odeEnergy != null || status.shugoKeys != null;
  if (!showEmptyResourceChips && !hasValue) return null;

  return (
    <div className={`flex items-end gap-1 ${compact ? "" : ""}`}>
      <Chip label="오드" value={status.odeEnergy} />
      <Chip label="열쇠" value={status.shugoKeys} />
    </div>
  );
}
