import { useEffect, useRef } from "react";
import { useSettingsStore } from "@/stores/useSettingsStore";
import { useGuildStore } from "@/stores/useGuildStore";

const POLL_MS = 30_000;
const FAST_POLL_MS = 5_000;

function nextIntervalMs(): number {
  const inbox = useGuildStore.getState().inbox;
  const now = Date.now();
  const soon = inbox?.fieldBosses?.some((boss) => {
    const remain = boss.targetAt - now;
    return remain <= 120_000 && remain > -30_000;
  });
  return soon ? FAST_POLL_MS : POLL_MS;
}

export function useGuildInbox() {
  const url = useSettingsStore((s) => s.guildWebUrl);
  const token = useSettingsStore((s) => s.guildDeviceToken);
  const refresh = useGuildStore((s) => s.refresh);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    if (!url || !token) return;
    let cancelled = false;

    const tick = async () => {
      await refresh(url, token);
      if (cancelled) return;
      timerRef.current = setTimeout(tick, nextIntervalMs());
    };

    tick();
    return () => {
      cancelled = true;
      if (timerRef.current) clearTimeout(timerRef.current);
    };
  }, [url, token, refresh]);
}
