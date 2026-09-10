import { create } from "zustand";
import type { GuildInbox } from "@/lib/guildApi";
import { guildFetch } from "@/lib/guildApi";

type GuildState = {
  inbox: GuildInbox | null;
  error: string;
  loading: boolean;
  lastFetchedAt: number;
  setInbox: (inbox: GuildInbox | null) => void;
  refresh: (url: string, token: string) => Promise<void>;
  act: (
    url: string,
    token: string,
    body: Record<string, unknown>,
  ) => Promise<string>;
};

const empty = (): GuildInbox => ({
  linked: false,
  member: null,
  badgeCount: 0,
  notices: [],
  myEvents: [],
  openEvents: [],
  agro: null,
  fieldBosses: [],
});

export const useGuildStore = create<GuildState>((set) => ({
  inbox: null,
  error: "",
  loading: false,
  lastFetchedAt: 0,
  setInbox: (inbox) => set({ inbox }),
  refresh: async (url, token) => {
    if (!url || !token) {
      set({ inbox: null, error: "" });
      return;
    }
    set({ loading: true, error: "" });
    try {
      const json = await guildFetch<{ data: GuildInbox }>(url, token, "/api/meter/inbox");
      set({ inbox: json.data ?? empty(), loading: false, lastFetchedAt: Date.now() });
    } catch (e) {
      set({
        loading: false,
        error: e instanceof Error ? e.message : "길드 웹에 연결하지 못했습니다.",
      });
    }
  },
  act: async (url, token, body) => {
    const json = await guildFetch<{ message?: string; data: GuildInbox }>(url, token, "/api/meter/actions", {
      method: "POST",
      body: JSON.stringify(body),
    });
    set({ inbox: json.data ?? empty(), lastFetchedAt: Date.now(), error: "" });
    return json.message || "완료";
  },
}));
