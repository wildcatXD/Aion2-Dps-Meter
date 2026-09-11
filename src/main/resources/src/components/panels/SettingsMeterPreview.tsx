import { CombatTimer } from "@/components/CombatTimer";
import { Header } from "@/components/Header";
import { MeterList } from "@/components/MeterList";
import { TargetInfo } from "@/components/TargetInfo";
import { useSettingsStore } from "@/stores/useSettingsStore";
import type { Player } from "@/types";
import { useShallow } from "zustand/react/shallow";

/** 설정값을 바꾸는 즉시 미터에 어떻게 보이는지 보여 줍니다. */
const PREVIEW_PLAYERS: Player[] = [
  {
    id: 1,
    name: "나",
    job: "검성",
    dps: 99564,
    nDps: 90200,
    amount: 4185000,
    nAmount: 3800000,
    damageContribution: 35.5,
    entireContribution: 12.4,
    isUser: true,
    server: 1001,
  },
  {
    id: 2,
    name: "딜러A",
    job: "마도성",
    dps: 72423,
    nDps: 68100,
    amount: 2790000,
    nAmount: 2500000,
    damageContribution: 20.1,
    entireContribution: 8.2,
    isUser: false,
    server: 2002,
  },
  {
    id: 3,
    name: "딜러B",
    job: "정령성",
    dps: 18954,
    nDps: 17200,
    amount: 1395000,
    nAmount: 1200000,
    damageContribution: 4.2,
    entireContribution: 1.8,
    isUser: false,
    server: 1003,
  },
  {
    id: 4,
    name: "서폿A",
    job: "치유성",
    dps: 3000,
    nDps: 3000,
    amount: 279000,
    nAmount: 279000,
    damageContribution: 2.1,
    entireContribution: 0.4,
    isUser: false,
    server: 1004,
  },
];

export function SettingsMeterPreview() {
  const {
    rowHeight,
    meterListOpacity,
    meterOpacity,
    isMinimal,
    showCombatTimerInMinimal,
    showTargetInfoInMinimal,
    headerPosition,
  } = useSettingsStore(
    useShallow((s) => ({
      rowHeight: s.rowHeight,
      meterListOpacity: s.meterListOpacity,
      meterOpacity: s.meterOpacity,
      isMinimal: s.isMinimal,
      showCombatTimerInMinimal: s.showCombatTimerInMinimal,
      showTargetInfoInMinimal: s.showTargetInfoInMinimal,
      headerPosition: s.headerPosition,
    })),
  );

  const showTarget = !isMinimal || showTargetInfoInMinimal;
  const showTimer = !isMinimal || showCombatTimerInMinimal;
  const showHeader = !isMinimal;

  return (
    <div className="flex h-full min-h-0 w-[360px] shrink-0 flex-col border-l border-amber-500/15 bg-black/30 px-3 py-3">
      <div className="mb-2 text-[10px] font-semibold tracking-wide text-amber-200/55">
        실시간 미리보기
      </div>
      <div className="min-h-0 flex-1 overflow-auto">
        <div
          className="pointer-events-none w-full"
          style={
            {
              "--meter-bg": `rgba(11,13,23,${meterOpacity})`,
            } as React.CSSProperties
          }>
          <div
            className="rounded-lg border border-amber-500/20 px-2 py-2"
            style={{ background: "var(--meter-bg)" }}>
            {headerPosition === "top" && showHeader && (
              <div className="mb-2">
                <Header
                  className=""
                  setSettings={() => {}}
                />
              </div>
            )}
            <div style={{ opacity: meterListOpacity }}>
              {showTarget && (
                <TargetInfo
                  targetName="계약파괴자 가르투아"
                  rowHeight={rowHeight}
                  remainHp={4368556}
                  maxHp={7560000}
                />
              )}
              <MeterList
                players={PREVIEW_PLAYERS}
                rowHeight={rowHeight}
                onSelect={() => {}}
              />
              {showTimer && (
                <CombatTimer
                  isInCombat
                  combatTime="01:32"
                />
              )}
            </div>
            {headerPosition === "bottom" && showHeader && (
              <div className="mt-2">
                <Header
                  className=""
                  setSettings={() => {}}
                />
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
