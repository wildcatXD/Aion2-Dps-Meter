import { useState } from "react";
import { useGuildStore } from "@/stores/useGuildStore";
import { useSettingsStore } from "@/stores/useSettingsStore";

const formatWhen = (value: string | null) => {
  if (!value) return "시간 미정";
  return value.replace("T", " ").slice(0, 16);
};

export const GuildPanel = () => {
  const url = useSettingsStore((s) => s.guildWebUrl);
  const token = useSettingsStore((s) => s.guildDeviceToken);
  const linkedName = useSettingsStore((s) => s.guildLinkedName);
  const { inbox, error, loading, refresh, act } = useGuildStore();
  const [busy, setBusy] = useState<string | null>(null);
  const [flash, setFlash] = useState("");

  const run = async (key: string, body: Record<string, unknown>) => {
    setBusy(key);
    setFlash("");
    try {
      const msg = await act(url, token, body);
      setFlash(msg);
    } catch (e) {
      setFlash(e instanceof Error ? e.message : "실패");
    } finally {
      setBusy(null);
    }
  };

  if (!url || !token) {
    return (
      <div className="flex h-full items-center justify-center px-4 text-center text-sm opacity-60">
        설정에서 길드 웹 주소와 연동 코드를 입력하세요.
      </div>
    );
  }

  return (
    <div className="flex h-full min-h-0 w-full flex-col overflow-hidden">
      <div className="flex items-center justify-between px-1 pb-2 text-xs">
        <span className="text-amber-200/90 truncate">{linkedName || inbox?.member?.name || "연동됨"}</span>
        <button
          type="button"
          className="opacity-50 hover:opacity-100"
          onClick={() => refresh(url, token)}
        >
          {loading ? "불러오는 중" : "새로고침"}
        </button>
      </div>
      {error && <p className="text-xs text-rose-300 px-1 mb-2">{error}</p>}
      {flash && <p className="text-xs text-amber-300 px-1 mb-2">{flash}</p>}
      <div className="flex min-h-0 flex-1 flex-col gap-3 overflow-y-auto pr-1">
        {inbox?.agro && (
          <section className="rounded-lg border border-amber-500/20 bg-black/30 p-3">
            <p className="text-[10px] tracking-widest text-amber-400 font-bold mb-1">아그로</p>
            <p className="text-sm">{formatWhen(inbox.agro.starts_at)}</p>
            {inbox.agro.notice && <p className="text-xs opacity-50 mt-1">{inbox.agro.notice}</p>}
            <div className="flex gap-2 mt-2">
              <button
                type="button"
                disabled={busy !== null}
                onClick={() => run("agro-go", { action: "agro", roundId: inbox.agro?.id, status: "going" })}
                className="flex-1 text-xs py-1.5 rounded-md bg-amber-500 text-[#0b0d17] font-bold disabled:opacity-40"
              >
                참가
              </button>
              <button
                type="button"
                disabled={busy !== null}
                onClick={() => run("agro-skip", { action: "agro", roundId: inbox.agro?.id, status: "skip" })}
                className="flex-1 text-xs py-1.5 rounded-md bg-white/10 disabled:opacity-40"
              >
                불참
              </button>
            </div>
            {inbox.agro.myResponse && (
              <p className="text-[11px] opacity-50 mt-1">
                내 응답: {inbox.agro.myResponse === "going" ? "참가" : "불참"}
              </p>
            )}
          </section>
        )}

        {inbox?.notices && inbox.notices.length > 0 && (
          <section>
            <p className="text-[10px] tracking-widest text-amber-400 font-bold mb-1 px-1">안 읽은 공지</p>
            <div className="space-y-1">
              {inbox.notices.map((n) => (
                <button
                  key={n.id}
                  type="button"
                  disabled={busy !== null}
                  onClick={() => run(`notice-${n.id}`, { action: "notice-read", noticeId: n.id })}
                  className="w-full text-left rounded-lg bg-black/30 px-3 py-2 hover:bg-black/50"
                >
                  <p className="text-sm truncate">{n.title}</p>
                  <p className="text-[11px] opacity-40">탭하면 읽음</p>
                </button>
              ))}
            </div>
          </section>
        )}

        <section>
          <p className="text-[10px] tracking-widest text-amber-400 font-bold mb-1 px-1">내 일정</p>
          {!inbox?.myEvents?.length ? (
            <p className="text-xs opacity-40 px-1">신청한 일정이 없습니다</p>
          ) : (
            <div className="space-y-1">
              {inbox.myEvents.map((ev) => (
                <div key={`${ev.scheduleId}-${ev.memberId}`} className="rounded-lg bg-black/30 px-3 py-2">
                  <p className="text-sm truncate">{ev.title}</p>
                  <p className="text-[11px] opacity-50">
                    {formatWhen(ev.scheduleDate)} · {ev.status === "pending" ? "승인 대기" : "참가 확정"}
                  </p>
                  <button
                    type="button"
                    disabled={busy !== null}
                    onClick={() =>
                      run(`cancel-${ev.scheduleId}`, {
                        action: "cancel",
                        scheduleId: ev.scheduleId,
                        memberId: ev.memberId,
                      })
                    }
                    className="text-[11px] text-rose-300/80 mt-1"
                  >
                    취소
                  </button>
                </div>
              ))}
            </div>
          )}
        </section>

        <section>
          <p className="text-[10px] tracking-widest text-amber-400 font-bold mb-1 px-1">신청 가능한 일정</p>
          {!inbox?.openEvents?.length ? (
            <p className="text-xs opacity-40 px-1">열린 일정이 없습니다</p>
          ) : (
            <div className="space-y-1">
              {inbox.openEvents.map((ev) => (
                <div key={ev.scheduleId} className="rounded-lg bg-black/30 px-3 py-2">
                  <p className="text-sm truncate">{ev.title}</p>
                  <p className="text-[11px] opacity-50">{formatWhen(ev.scheduleDate)}</p>
                  <button
                    type="button"
                    disabled={busy !== null || !inbox.linked}
                    onClick={() =>
                      run(`apply-${ev.scheduleId}`, {
                        action: "apply",
                        scheduleId: ev.scheduleId,
                        memberId: ev.memberId,
                      })
                    }
                    className="mt-1 text-xs font-bold text-amber-300 disabled:opacity-40"
                  >
                    {inbox.linked ? "참가" : "캐릭터 연동 필요"}
                  </button>
                </div>
              ))}
            </div>
          )}
        </section>
      </div>
    </div>
  );
};
