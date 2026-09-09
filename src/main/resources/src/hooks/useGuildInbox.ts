import { useEffect } from "react";
import { useSettingsStore } from "@/stores/useSettingsStore";
import { useGuildStore } from "@/stores/useGuildStore";

const POLL_MS = 30_000;

export function useGuildInbox() {
  const url = useSettingsStore((s) => s.guildWebUrl);
  const token = useSettingsStore((s) => s.guildDeviceToken);
  const refresh = useGuildStore((s) => s.refresh);

  useEffect(() => {
    if (!url || !token) return;
    refresh(url, token);
    const id = setInterval(() => refresh(url, token), POLL_MS);
    return () => clearInterval(id);
  }, [url, token, refresh]);
}
