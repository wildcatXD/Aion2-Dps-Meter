export type GuildEvent = {
  scheduleId: number;
  title: string;
  scheduleDate: string | null;
  status: "open" | "pending" | "approved";
  isSanctuary: boolean;
  characterName: string | null;
  memberId: number | null;
};

export type GuildNotice = {
  id: number;
  title: string;
  createdAt: string;
  isPinned: boolean;
};

export type GuildAgro = {
  id: number;
  starts_at: string;
  notice: string | null;
  myResponse: "going" | "skip" | null;
  goingCount?: number;
  skipCount?: number;
} | null;

export type GuildInbox = {
  linked: boolean;
  member: { id: number; name: string; className: string | null } | null;
  badgeCount: number;
  notices: GuildNotice[];
  myEvents: GuildEvent[];
  openEvents: GuildEvent[];
  agro: GuildAgro;
};

export function normalizeGuildUrl(raw: string): string {
  return raw.trim().replace(/\/+$/, "");
}

export async function guildFetch<T>(
  url: string,
  token: string,
  path: string,
  init?: RequestInit,
): Promise<T> {
  const base = normalizeGuildUrl(url);
  const res = await fetch(`${base}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(init?.headers || {}),
    },
  });
  const json = (await res.json()) as T & { success?: boolean; message?: string; error?: string };
  if (!res.ok || json.success === false) {
    throw new Error(json.message || json.error || `요청 실패 (${res.status})`);
  }
  return json;
}
