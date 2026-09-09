import { memo } from "react";
import bossIcon from "@/assets/bossIcon.png";
import { useSettingsStore } from "@/stores/useSettingsStore";
import { formatAmount } from "@/utils/format";
import { useShallow } from "zustand/react/shallow";

interface Props {
  targetName: string;
  rowHeight: number;
  remainHp: number;
  maxHp: number;
}

export const TargetInfo = memo(({ targetName, rowHeight, remainHp, maxHp }: Props) => {
  const { theme, targetInfoDisplayMode } = useSettingsStore(
    useShallow((s) => ({
      theme: s.theme,
      targetInfoDisplayMode: s.targetInfoDisplayMode,
    })),
  );
  const displayName = targetName || "타겟 인식 실패";
  const isFailed = !targetName;

  const iconSize = Math.round(rowHeight * 0.7);
  const fontSize = `${Math.max(10, Math.round(rowHeight * 0.4))}px`;
  const percent = maxHp > 0 ? `${((remainHp / maxHp) * 100).toFixed(1)}%` : "0%";

  const renderHpValue = (value: string) => <span>{value}</span>;

  const renderStats = () => {
    switch (targetInfoDisplayMode) {
      case "hp_percent":
        return (
          <>
            <span>{renderHpValue(formatAmount(remainHp))}</span>
            {/* <span className="mx-0.5">{renderDivider()}</span> */}
            <span className="ml-0.5 ">({formatAmount(maxHp)})</span>{" "}
            <span className="ml-2">{renderHpValue(percent)}</span>
          </>
        );
      case "remain_full_percent":
        return (
          <>
            <span>{renderHpValue(remainHp.toLocaleString())}</span>
            <span className="ml-2">{renderHpValue(percent)}</span>
          </>
        );
      case "remain_percent":
        return (
          <>
            <span>{renderHpValue(formatAmount(remainHp))}</span>
            <span className="ml-2">{renderHpValue(percent)}</span>
          </>
        );
      case "percent":
        return <span className="ml-2">{renderHpValue(percent)}</span>;
      case "hp_full_percent":
      default:
        return (
          <>
            <span>{renderHpValue(remainHp.toLocaleString())}</span>
            {/* <span className="mx-0.5">{renderDivider()}</span> */}
            <span className="ml-0.5  ">({maxHp.toLocaleString()})</span>
            <span className="ml-2">{renderHpValue(percent)}</span>
          </>
        );
    }
  };

  return (
    <div
        className="relative w-full px-2 mb-2 rounded-sm overflow-hidden bg-black/40"
      style={{ height: rowHeight }}>
      <div
        className="absolute inset-0 origin-left"
        style={{
          background: `linear-gradient(to right, ${theme.bossBar[0]}, ${theme.bossBar[1]})`,
          opacity: isFailed ? 0.2 : 0.8,
        }}
      />
      <div className="relative h-full flex items-center gap-3">
        <div
          className="flex items-center justify-center shrink-0"
          style={{ width: iconSize, height: iconSize }}>
          <img
            src={bossIcon}
            draggable={false}
            className={`w-full h-full object-contain ${isFailed ? "opacity-40" : ""}`}
          />
        </div>
        <span
          className="font-bold text-shadow-meter truncate"
          style={{ color: isFailed ? "rgba(255,255,255,0.4)" : "#ffffff", fontSize }}>
          {displayName}
        </span>

        {!isFailed && (
          <div
            className="ml-auto font-bold text-shadow-meter shrink-0 flex items-center text-end whitespace-nowrap"
            style={{ color: theme.bossRightValue, fontSize }}>
            {renderStats()}
          </div>
        )}
      </div>
    </div>
  );
});
