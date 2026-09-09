interface Props {
  isInCombat: boolean;
  combatTime: string;
}
import { useSettingsStore } from "@/stores/useSettingsStore";

export const CombatTimer = ({ isInCombat, combatTime }: Props) => {
  const combatTimeColor = useSettingsStore((s) => s.theme.combatTimeColor);
  return (
    <div className="flex items-center gap-2 px-1 pt-2">
      <div
        className="w-2 h-2 rounded-full transition-colors duration-300"
        style={{
          background: isInCombat ? "#fbbf24" : combatTimeColor,
          boxShadow: isInCombat ? "0 0 6px #fbbf24" : "none",
        }}
      />
      <span
        className="text-xs font-bold"
        style={{ color: isInCombat ? "#fbbf24" : combatTimeColor }}>
        {isInCombat ? "전투 중" : "대기 중"}
      </span>
      <span
        className="ml-auto text-xs font-bold"
        style={{ color: combatTimeColor }}>
        {combatTime}
      </span>
    </div>
  );
};
