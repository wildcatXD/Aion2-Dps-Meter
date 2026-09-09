import { useState } from "react";
import { useSettingsStore } from "@/stores/useSettingsStore";
import { SettingsItem } from "./SettingsItem";
import { SettingsRow } from "./SettingsRow";
import { SettingsControlInput } from "./SettingsControlInput";
import { Button } from "@/components/ui/button";
import { guildFetch, normalizeGuildUrl } from "@/lib/guildApi";
import { useGuildStore } from "@/stores/useGuildStore";

export function GuildPairSettings() {
  const guildWebUrl = useSettingsStore((s) => s.guildWebUrl);
  const guildDeviceToken = useSettingsStore((s) => s.guildDeviceToken);
  const guildLinkedName = useSettingsStore((s) => s.guildLinkedName);
  const setGuildWebUrl = useSettingsStore((s) => s.setGuildWebUrl);
  const setGuildDeviceToken = useSettingsStore((s) => s.setGuildDeviceToken);
  const setGuildLinkedName = useSettingsStore((s) => s.setGuildLinkedName);
  const refresh = useGuildStore((s) => s.refresh);
  const setInbox = useGuildStore((s) => s.setInbox);
  const [code, setCode] = useState("");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");

  const pair = async () => {
    setBusy(true);
    setMessage("");
    try {
      const base = normalizeGuildUrl(guildWebUrl);
      if (!base) throw new Error("길드 웹 주소를 먼저 넣으세요.");
      const json = await guildFetch<{ token: string; member: { name: string } | null }>(
        base,
        "",
        "/api/meter/pair/complete",
        { method: "POST", body: JSON.stringify({ code, label: "빛 DPS Overlay" }) },
      );
      setGuildWebUrl(base);
      setGuildDeviceToken(json.token);
      setGuildLinkedName(json.member?.name || "연동됨");
      setCode("");
      setMessage("연동했습니다. 헤더 종 아이콘에서 일정·공지를 볼 수 있습니다.");
      await refresh(base, json.token);
    } catch (e) {
      setMessage(e instanceof Error ? e.message : "연동 실패");
    } finally {
      setBusy(false);
    }
  };

  const unlink = () => {
    setGuildDeviceToken("");
    setGuildLinkedName("");
    setInbox(null);
    setMessage("이 미터기 연동을 해제했습니다.");
  };

  return (
    <SettingsItem
      title="길드 웹 연동"
      description="웹의 미터기 연동 페이지에서 코드를 만든 뒤 여기에 입력합니다. 공지·일정 참여·아그로 알림만 오고, 전투기록 업로드는 아직 없습니다.">
      <SettingsRow
        title="웹 주소"
        align="start"
        rightClassName="w-44">
        <SettingsControlInput
          value={guildWebUrl}
          placeholder="https://길드웹주소"
          onChange={(e) => setGuildWebUrl(e.target.value)}
        />
      </SettingsRow>
      {guildDeviceToken ? (
        <SettingsRow
          title="상태"
          description={guildLinkedName || "연동됨"}>
          <Button
            variant="ghost"
            size="sm"
            onClick={unlink}
            className="text-xs opacity-70">
            해제
          </Button>
        </SettingsRow>
      ) : (
        <SettingsRow
          title="연동 코드"
          align="start"
          rightClassName="w-44">
          <div className="flex flex-col gap-2 w-44">
            <SettingsControlInput
              value={code}
              placeholder="ABC123"
              onChange={(e) => setCode(e.target.value.toUpperCase())}
            />
            <Button
              size="sm"
              disabled={busy}
              onClick={pair}
              className="bg-amber-500 hover:bg-amber-400 text-[#0b0d17]">
              {busy ? "연동 중" : "연동"}
            </Button>
          </div>
        </SettingsRow>
      )}
      {message ? <p className="text-[11px] text-amber-300/80">{message}</p> : null}
    </SettingsItem>
  );
}
