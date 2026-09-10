import { useEffect, useRef, useState } from "react";
import { useGuildStore } from "@/stores/useGuildStore";
import { useSettingsStore } from "@/stores/useSettingsStore";
import { formatRemainShort, parseKstDateTime } from "@/utils/formatRemain";

type ToastItem = {
  key: string;
  title: string;
  body: string;
};

const DISMISS_MS = 12_000;

export function OverlayToasts() {
  const inbox = useGuildStore((s) => s.inbox);
  const enabled = useSettingsStore((s) => s.guildAlertsEnabled);
  const token = useSettingsStore((s) => s.guildDeviceToken);
  const seen = useRef(new Set<string>());
  const [toasts, setToasts] = useState<ToastItem[]>([]);

  useEffect(() => {
    if (!enabled || !token || !inbox) return;
    const now = Date.now();
    const next: ToastItem[] = [];

    if (inbox.agro?.starts_at) {
      const at = parseKstDateTime(inbox.agro.starts_at);
      if (at != null) {
        const remain = at - now;
        if (remain <= 10 * 60_000 && remain > 0) {
          next.push({
            key: `agro-${inbox.agro.id}-10m`,
            title: "아그로",
            body: `${formatRemainShort(remain)} 후 · ${inbox.agro.notice || "참가 확인"}`,
          });
        } else if (remain <= 0 && remain > -2 * 60_000) {
          next.push({
            key: `agro-${inbox.agro.id}-now`,
            title: "아그로 시작",
            body: inbox.agro.notice || "지금",
          });
        }
      }
    }

    for (const boss of inbox.fieldBosses ?? []) {
      const remain = boss.targetAt - now;
      if (!boss.priority && remain > 5 * 60_000) continue;
      if (remain <= 5 * 60_000 && remain > 0) {
        next.push({
          key: `boss-${boss.bossCode}-${boss.targetAt}-5m`,
          title: boss.name,
          body: `${boss.regionName} · ${formatRemainShort(remain)}`,
        });
      } else if (remain <= 0 && remain > -60_000) {
        next.push({
          key: `boss-${boss.bossCode}-${boss.targetAt}-now`,
          title: boss.name,
          body: `${boss.regionName} · 지금`,
        });
      }
    }

    const fresh = next.filter((item) => !seen.current.has(item.key));
    if (fresh.length === 0) return;
    for (const item of fresh) seen.current.add(item.key);
    setToasts((prev) => [...fresh, ...prev].slice(0, 4));
    const timer = setTimeout(() => {
      setToasts((prev) => prev.filter((item) => !fresh.some((f) => f.key === item.key)));
    }, DISMISS_MS);
    return () => clearTimeout(timer);
  }, [enabled, token, inbox]);

  if (toasts.length === 0) return null;

  return (
    <div className="absolute left-0 bottom-full mb-2 w-full space-y-1 pointer-events-none z-50">
      {toasts.map((toast) => (
        <div
          key={toast.key}
          className="rounded-md border border-amber-500/30 bg-[#0b0d17]/95 px-3 py-2 shadow-lg">
          <p className="text-[11px] font-bold text-amber-300">{toast.title}</p>
          <p className="text-[11px] text-slate-200/90 truncate">{toast.body}</p>
        </div>
      ))}
    </div>
  );
}
