import { useState, useCallback } from "react";

interface HistoryContributor {
  id: number;
  nickname: string;
  server?: number;
  job?: string;
}

interface HistoryInformation {
  amount: number;
  dps: number;
  contribution: number;
}

export interface HistoryItem {
  idx: number;
  mobName: string;
  isBoss: boolean;
  totalAmount: number;
  contributors: string[];
  battleTime: number;
  battleStart: number;
  raw: any;
}
// import { useDebugStore } from "../stores/debugStore";

export const useHistory = () => {
  const [historyList, setHistoryList] = useState<HistoryItem[]>([]);
  const [loading, setLoading] = useState(false);
  // const addLog = useDebugStore.getState().addLog;

  const fetchHistory = useCallback(async (opts?: { silent?: boolean }) => {
    if (!opts?.silent) setLoading(true);
    try {
      const raw = window.javaBridge?.getBattleList?.();
      // addLog(`히스토리 ${raw}`);

      if (!raw) return;

      const parsed: { first: number; second: any }[] = JSON.parse(raw);
      const items: HistoryItem[] = parsed
        .map(({ first: idx, second: report }) => {
          const contributors: HistoryContributor[] = Array.from(report.contributors ?? []);
          const information: Record<string, HistoryInformation> = report.information ?? {};

          const totalAmount = Object.values(information).reduce(
            (sum, info) => sum + (info.amount ?? 0),
            0,
          );

          const contributorNames = contributors.map((c) => c.nickname || String(c.id));
          const battleTime = (report.battleEnd ?? 0) - (report.battleStart ?? 0);
          const mobName = report.target?.mob?.name ?? "알 수 없음";
          const isBoss = report.target?.mob?.boss ?? false;
          return {
            idx,
            mobName,
            isBoss,
            totalAmount,
            contributors: contributorNames,
            battleTime,
            battleStart: report.battleStart ?? 0,
            raw: report,
          };
        })
        .filter((item) => item.totalAmount > 0)
        .reverse();

      setHistoryList((prev) => {
        if (
          prev.length === items.length &&
          prev.every(
            (item, i) =>
              item.idx === items[i].idx &&
              item.battleStart === items[i].battleStart &&
              item.totalAmount === items[i].totalAmount &&
              item.mobName === items[i].mobName,
          )
        ) {
          return prev;
        }
        return items;
      });
    } catch (e) {
      console.error("history fetch error", e);
    } finally {
      if (!opts?.silent) setLoading(false);
    }
  }, []);

  return { historyList, loading, fetchHistory };
};
