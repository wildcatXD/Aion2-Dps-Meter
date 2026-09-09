import { memo, useMemo } from "react";
import { getJobIconSrc } from "@/utils/icons";
import { formatAmount } from "@/utils/format";
import { useSettingsStore } from "@/stores/useSettingsStore";
import { useShallow } from "zustand/react/shallow";

interface Props {
  id: number;
  name: string;
  job?: string;
  dps: number;
  nDps: number;
  amount: number;
  contribution: number;
  entireContribution: number;
  isUser: boolean;
  isSelected: boolean;
  onSelect: (id: number) => void;
  topDps: number;
  rowHeight: number;
  server: number;
  metric: "rdps" | "ndps";
}

const makeGradient = (from: string, to: string) => `linear-gradient(to right, ${from}, ${to})`;

export const MeterRow = memo(
  ({
    id,
    name,
    job,
    dps,
    nDps,
    server,
    contribution,
    entireContribution,
    isUser,
    onSelect,
    topDps,
    amount,
    rowHeight,
    metric,
  }: Props) => {
    const { displayMode, nameDisplay, theme, contributionMode } = useSettingsStore(
      useShallow((s) => ({
        displayMode: s.displayMode,
        nameDisplay: s.nameDisplay,
        theme: s.theme,
        contributionMode: s.contributionMode,
      })),
    );
    // const showPower = useSettingsStore((s) => s.showPower);

    const gradients = useMemo(
      () => ({
        user: makeGradient(...theme.userBar),
        normal: makeGradient(...theme.normalBar),
        warning: makeGradient(...theme.warningBar),
        error: makeGradient(...theme.errorBar),
      }),
      [theme.errorBar, theme.normalBar, theme.userBar, theme.warningBar],
    );
    const nameColor = !server
      ? theme.serverDefaultColor
      : server >= 1001 && server <= 1021
        ? theme.serverAColor
        : server >= 2001 && server <= 2021
          ? theme.serverBColor
          : theme.serverDefaultColor;

    const maskedName = (name: string) => `${name[0]}***`;
    const iconSize = Math.round(rowHeight * 0.7);
    const fontSize = `${Math.max(10, Math.floor(rowHeight * 0.4))}px`;

    const shownDps = metric === "ndps" ? nDps || dps : dps;
    const dpsSuffix = metric === "ndps" ? " n/초" : "/초";
    const ratio = Math.max(0, Math.min(1, shownDps / topDps));
    const iconSrc = getJobIconSrc(job);
    const fillGradient = isUser
      ? gradients.user
      : Number(contribution) < 3
        ? gradients.error
        : Number(contribution) < 5
          ? gradients.warning
          : gradients.normal;

    const statItems = useMemo(() => {
      const amountColor = theme.meterStatAmount;
      const dpsColor = theme.meterStatDps;
      const percentColor = theme.meterStatPercent;
      const pct = contributionMode === "entireContribution" ? entireContribution : contribution;
      const compactAmount = formatAmount(amount);
      const fullAmount = amount.toLocaleString();
      const dpsText = `${shownDps.toLocaleString()}${dpsSuffix}`;
      const pctText = `${pct.toFixed(1)}%`;

      switch (displayMode) {
        case "amount_dps_percent":
          return [
            { key: "amount", color: amountColor, value: compactAmount },
            { key: "dps", color: dpsColor, value: dpsText },
            { key: "percent", color: percentColor, value: pctText },
          ];
        case "amount_percent":
          return [
            { key: "amount", color: amountColor, value: compactAmount },
            { key: "percent", color: percentColor, value: pctText },
          ];
        case "amount_full_dps_percent":
          return [
            { key: "amount", color: amountColor, value: fullAmount },
            { key: "dps", color: dpsColor, value: dpsText },
            { key: "percent", color: percentColor, value: pctText },
          ];
        case "amount_full_percent":
          return [
            { key: "amount", color: amountColor, value: fullAmount },
            { key: "percent", color: percentColor, value: pctText },
          ];
        case "dps_percent":
        default:
          return [
            { key: "dps", color: dpsColor, value: dpsText },
            { key: "percent", color: percentColor, value: pctText },
          ];
      }
    }, [
      amount,
      contribution,
      contributionMode,
      displayMode,
      metric,
      shownDps,
      entireContribution,
      theme.meterStatAmount,
      theme.meterStatDps,
      theme.meterStatPercent,
    ]);

    const displayName = useMemo(() => {
      switch (nameDisplay) {
        case "all":
          return name;
        case "me_only":
          return isUser ? name : maskedName(name);
        case "hidden":
          return maskedName(name);
      }
    }, [isUser, name, nameDisplay]);

    return (
      <div
        onClick={() => onSelect(id)}
        style={{ height: rowHeight }}
        className={`w-full  relative px-2 rounded-sm overflow-hidden bg-black/40 cursor-pointer`}>
        <div
          className="absolute inset-0 origin-left transition-transform duration-150 ease-out"
          style={{
            background: fillGradient,
            transform: `scaleX(${ratio})`,
          }}
        />
        <div className="relative h-full flex items-center gap-3  overflow-hidden">
          <div
            style={{ width: iconSize, height: iconSize }}
            className="flex items-center justify-center ">
            {iconSrc && (
              <img
                src={iconSrc}
                draggable={false}
                className="w-full h-full object-contain"
                style={{ filter: "drop-shadow(0 0 3px rgba(20,20,20,0.6))" }}
              />
            )}
          </div>
          <span
            className="font-bold text-shadow-meter truncate flex-1 "
            style={{ color: nameColor, fontSize }}>
            {displayName}
          </span>
          {/* <div className="flex gap-1.5 flex-1 items-center "> */}
          {/* {showPower && power > 0 && (
              <div
                className={`bg-black/30 px-2     text-shadow-meter flex items-center rounded-xl `}>
                <span
                  className="text-[#10f1e2] font-semibold  py-1 leading-none"
                  style={{
                    fontSize: `${parseInt(fontSize) - 2}px`,
                  }}>{`${(power / 1000).toFixed(1)}k`}</span>
              </div>
            )} */}
          {/* </div> */}
          <div className="flex items-center gap-2 font-bold text-shadow-meter shrink-0">
            {statItems.map((item) => (
              <span
                key={item.key}
                className="text-end whitespace-nowrap "
                style={{ color: item.color, fontSize }}>
                {item.value}
              </span>
            ))}
          </div>
        </div>
      </div>
    );
  },
  (prev, next) => {
    return (
      prev.dps === next.dps &&
      prev.nDps === next.nDps &&
      prev.amount === next.amount &&
      prev.contribution === next.contribution &&
      prev.entireContribution === next.entireContribution &&
      prev.server === next.server &&
      prev.isUser === next.isUser &&
      prev.isSelected === next.isSelected &&
      prev.topDps === next.topDps &&
      prev.name === next.name &&
      prev.job === next.job &&
      prev.rowHeight === next.rowHeight &&
      prev.metric === next.metric
    );
  },
);
