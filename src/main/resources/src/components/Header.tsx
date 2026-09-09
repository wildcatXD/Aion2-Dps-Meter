import { Button } from "@/components/ui/button";
import type { PanelType } from "@/types";
import { memo, useRef } from "react";
import {
  Settings,
  Power,
  ClipboardClock,
  Bug,
  UserRoundPlus,
  Grip,
  Bell,
} from "lucide-react";
import { useJoinRequestStore } from "@/stores/useJoinRequestStore";
import { useGuildStore } from "@/stores/useGuildStore";

interface Props {
  setSettings: (value: PanelType) => void;
  className: string;
}
import { useSettingsStore } from "@/stores/useSettingsStore";
import { useMoveWindow } from "@/hooks/drag/useMoveWindow";

export const Header = memo(
  ({
    className,
    setSettings,
  }: Props) => {
    const isDebugMode = useSettingsStore((s) => s.isDebugMode);
    const dpsMetric = useSettingsStore((s) => s.dpsMetric);
    const requestCount = useJoinRequestStore((s) => s.requests.length);
    const guildBadge = useGuildStore((s) => s.inbox?.badgeCount ?? 0);
    const isOpen = useJoinRequestStore((s) => s.isOpen);
    const setOpen = useJoinRequestStore((s) => s.setOpen);
    const exitApp = () => {
      (window as any).javaBridge.exitApp();
    };
    const dragRef = useRef<HTMLDivElement>(null);
    useMoveWindow(dragRef);

    const toggleDebugConsole = () => {
      window.dispatchEvent(new CustomEvent("toggle-debug-console"));
    };

    return (
      <div className=" flex justify-between items-center">
        <div className={`flex gap-2 items-center ${className}`}>
          <div
            ref={dragRef}
            className="window-drag-handle cursor-grab active:cursor-grabbing opacity-70 hover:opacity-100 transition-opacity p-1">
            <Grip className="size-4 text-amber-200/80" />
          </div>
          <div className="flex items-baseline gap-1.5 pr-1 select-none">
            <span className="text-sm font-extrabold tracking-wide text-amber-400">빛</span>
            <span className="text-[11px] font-semibold text-slate-300/85">DPS</span>
            {dpsMetric === "ndps" && (
              <span className="text-[9px] font-bold tracking-wide text-amber-300/90 border border-amber-400/30 rounded px-1 py-px">
                nDPS
              </span>
            )}
          </div>
        </div>

        <div className={`${className} flex gap-2`}>
          <Button
            variant="ghost"
            onClick={exitApp}
            size="icon"
            className="rounded-full hover:bg-amber-500/15 hover:text-amber-200">
            <Power className="size-4.5" />
          </Button>
          <Button
            variant="ghost"
            onClick={() => setOpen(!isOpen)}
            size="icon"
            className="rounded-full relative hover:bg-amber-500/15 hover:text-amber-200">
            <UserRoundPlus className="size-4.5" />
            {requestCount > 0 && (
              <span
                className={`${className} absolute -top-1 -right-1 w-4 h-4 rounded-full bg-amber-500 text-[10px] text-[#0b0d17] flex items-center justify-center font-bold`}>
                {requestCount}
              </span>
            )}
          </Button>

          <Button
            size="icon"
            variant="ghost"
            onClick={() => setSettings("settings")}
            className="rounded-full hover:bg-amber-500/15 hover:text-amber-200">
            <Settings className="size-4.5" />
          </Button>

          <Button
            variant="ghost"
            size="icon"
            onClick={() => setSettings("guild")}
            className="rounded-full relative hover:bg-amber-500/15 hover:text-amber-200">
            <Bell className="size-4.5" />
            {guildBadge > 0 && (
              <span
                className={`${className} absolute -top-1 -right-1 w-4 h-4 rounded-full bg-amber-500 text-[10px] text-[#0b0d17] flex items-center justify-center font-bold`}>
                {guildBadge > 9 ? "9+" : guildBadge}
              </span>
            )}
          </Button>
          <Button
            variant="ghost"
            size="icon"
            onClick={() => setSettings("history")}
            className="rounded-full hover:bg-amber-500/15 hover:text-amber-200">
            <ClipboardClock className="size-4.5" />
          </Button>

          {isDebugMode && (
            <Button
              variant="ghost"
              size="icon"
              onClick={toggleDebugConsole}
              className="rounded-full hover:bg-amber-500/15 hover:text-amber-200">
              <Bug className="size-4.5" />
            </Button>
          )}
        </div>
      </div>
    );
  },
);
