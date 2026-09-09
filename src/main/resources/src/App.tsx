import { useState, useCallback, useEffect, useRef } from "react";
import { useMeter } from "./hooks/useMeter";
import type { Player, PanelType } from "@/types";
import { MeterList } from "./components/MeterList";
import { useDragUi } from "@/hooks/drag/useDragUi";
import { Header } from "@/components/Header.tsx";
import { TargetInfo } from "@/components/TargetInfo";
import { SidePanel } from "@/components/panels/SidePanel.tsx";
import { CombatTimer } from "@/components/CombatTimer.tsx";
import { useVersionCheck } from "@/hooks/useVersionCheck";
import { useResizable } from "@/hooks/resize/useResizable";
import { useSettingsStore } from "@/stores/useSettingsStore";
import { useShallow } from "zustand/react/shallow";
// import { TooltipProvider } from "@/components/ui/tooltip";
import { useJoinRequestStore } from "@/stores/useJoinRequestStore";
import { JoinRequestPanel } from "@/components/joinPanel/JoinRequestPanel";
import { cn } from "@/lib/utils";
import { DebugConsole } from "./components/DebugConsole";
import { useGuildInbox } from "@/hooks/useGuildInbox";
import lock from "@/assets/lock.png";
export default function App() {
  const {
    players,
    targetName,
    // isCollapse,
    isInCombat,
    remainHp,
    maxHp,
    // reset,
    // toggleCollapse,
    battleTime,
    formatBattleTime,
    setHistoryData,
  } = useMeter();

  const activePanelRef = useRef<PanelType>(null);
  const selectedRef = useRef<Player | null>(null);
  const {
    updateInfo,
    currentVersion,
    openReleasePage,
    downloadState,
    retryDownload,
    startUpdate,
    checkUpdate,
    checkStatus,
  } = useVersionCheck();
  const addRequest = useJoinRequestStore((s) => s.addRequest);
  const removeRequest = useJoinRequestStore((s) => s.removeRequest);
  const clearAll = useJoinRequestStore((s) => s.clearAll);
  const refuseRequest = useJoinRequestStore((s) => s.refuseRequest);

  useGuildInbox();
  const [activePanel, setActivePanel] = useState<PanelType>(null);
  const { meterWidth, onMouseDown, isDragging } = useResizable();
  const {
    headerPosition,
    windowX,
    windowY,
    isLoaded,
    rowHeight,
    isMinimal,
    showCombatTimerInMinimal,
    showTargetInfoInMinimal,
    meterOpacity,
    panelOpacity,
    joinPanelOpacity,
    meterListOpacity,
    isClickThrough,
    uiX,
    uiY,
  } = useSettingsStore(
    useShallow((s) => ({
      headerPosition: s.headerPosition,
      windowX: s.windowX,
      windowY: s.windowY,
      isLoaded: s.isLoaded,
      rowHeight: s.rowHeight,
      isMinimal: s.isMinimal,
      showCombatTimerInMinimal: s.showCombatTimerInMinimal,
      showTargetInfoInMinimal: s.showTargetInfoInMinimal,
      meterOpacity: s.meterOpacity,
      panelOpacity: s.panelOpacity,
      joinPanelOpacity: s.joinPanelOpacity,
      meterListOpacity: s.meterListOpacity,
      isClickThrough: s.isClickThrough,
      uiX: s.uiX,
      uiY: s.uiY,
    })),
  );

  const [selectedHistoryIdx, setSelectedHistoryIdx] = useState<number | undefined>(undefined);

  const handlePanelToggle = useCallback((panel: PanelType) => {
    setActivePanel((prev) => (prev === panel ? null : panel));
  }, []);
  const [selected, setSelected] = useState<Player | null>(null);
  const { wasDraggingRef } = useDragUi();
  // const handleToggleCollapse = useCallback(() => {
  //   toggleCollapse();
  //   setActivePanel(null);
  //   setSelected(null);
  // }, [toggleCollapse]);

  // const handleReset = useCallback(() => {
  //   reset();
  //   setSelectedHistoryIdx(undefined);
  //   setActivePanel(null);
  //   setSelected(null);
  // }, [reset]);
  const playersRef = useRef<Player[]>([]);
  useEffect(() => {
    playersRef.current = players;
  }, [players]);

  const handleSelect = useCallback((id: number) => {
    if (wasDraggingRef.current) return;
    const player = playersRef.current.find((p) => p.id === id);
    if (!player) return;
    if (activePanelRef.current === "details" && selectedRef.current?.id === player.id) {
      setActivePanel(null);
      return;
    }
    setSelected(player);
    setActivePanel("details");
  }, []);
  const handleClose = useCallback(() => {
    setActivePanel(null);
  }, []);
  const handleCheckUpdate = useCallback(() => {
    checkUpdate();
    handlePanelToggle("update");
  }, [checkUpdate, handlePanelToggle]);

  useEffect(() => {
    activePanelRef.current = activePanel;
  }, [activePanel]);
  useEffect(() => {
    selectedRef.current = selected;
  }, [selected]);

  useEffect(() => {
    if (!isLoaded) return;
    (window as any).javaBridge?.moveWindow(windowX, windowY);
  }, [isLoaded]);

  useEffect(() => {
    if (updateInfo) setActivePanel("update");
  }, [updateInfo]);

  useEffect(() => {
    const handleKeydown = (e: KeyboardEvent) => {
      if (e.key === "Escape") handleClose();
    };
    window.addEventListener("keydown", handleKeydown);
    return () => window.removeEventListener("keydown", handleKeydown);
  }, [handleClose]);
  useEffect(() => {
    (window as any).onClickThroughChanged = (v: boolean) => {
      useSettingsStore.setState({ isClickThrough: v });
    };
  }, []);
  // useEffect(() => {
  //   (window as any).strongReset = () => {
  //     reset();
  //     setActivePanel(null);
  //     setSelected(null);
  //   };
  // }, [reset]);

  useEffect(() => {
    if (isInCombat) {
      setSelectedHistoryIdx(undefined);
    }
  }, [isInCombat]);

  useEffect(() => {
    (window as any).onJoinRequest = (data: any) => {
      addRequest(data);
    };
    (window as any).onJoinRequestRemove = (id: number) => {
      removeRequest(id);
    };
    (window as any).onExitPartyUI = () => {
      clearAll();
    };
    (window as any).onRefuseJoinRequest = () => {
      refuseRequest();
    };
  }, []);
  const meterClass = cn(
    "rounded-lg relative transition-color duration-300 text-[#e0e0e0] py-2 px-3 border border-amber-500/15",
    isMinimal ? "bg-transparent group-hover/app:bg-(--meter-bg)" : "bg-(--meter-bg)",
  );

  const headerClass = cn(
    "transition-opacity duration-300",
    isMinimal && "opacity-0 group-hover/app:opacity-100",
  );

  const rootClass = cn(
    "drag-area cursor-move select-none relative group/app pt-2",
    isDragging && "pointer-events-none",
  );
  const handleSelectHistory = useCallback(
    (idx: number, report: any) => {
      setHistoryData(report);
      setSelectedHistoryIdx(idx);
    },
    [setHistoryData],
  );
  return (
    // <TooltipProvider>
    <div
      style={
        {
          position: "fixed",
          left: uiX,
          top: uiY,

          width: "fit-content",
          "--meter-bg": `rgba(11,13,23,${meterOpacity})`,
          "--panel-bg": `rgba(18,18,28,${panelOpacity})`,
          "--join-panel-bg": `rgba(18,18,28,${joinPanelOpacity})`,
          visibility: isLoaded ? "visible" : "hidden",
        } as React.CSSProperties
      }
      className={rootClass}>
      <div
        data-meter-root-anchor
        className={meterClass}
        style={{ width: meterWidth }}>
        {headerPosition === "top" && (
          <div className=" mb-2">
            <Header
              className={headerClass}
              // reset={handleReset}
              setSettings={handlePanelToggle}

              // isCollapse={isCollapse}
              // toggleCollapse={handleToggleCollapse}
            />
          </div>
        )}
        <div style={{ opacity: meterListOpacity }}>
          {players.length > 0 && (!isMinimal || showTargetInfoInMinimal) && (
            <TargetInfo
              targetName={targetName}
              rowHeight={rowHeight}
              remainHp={remainHp}
              maxHp={maxHp}
            />
          )}
          <MeterList
            players={players}
            selectedId={selected?.id}
            onSelect={handleSelect}
            rowHeight={rowHeight}
          />

          {battleTime && (!isMinimal || showCombatTimerInMinimal) && (
            <CombatTimer
              isInCombat={isInCombat}
              combatTime={formatBattleTime(battleTime)}
            />
          )}
        </div>
        {headerPosition === "bottom" && (
          <div className="mt-2">
            <Header
              className={headerClass}
              // reset={handleReset}
              setSettings={handlePanelToggle}
              // isCollapse={isCollapse}
              // toggleCollapse={handleToggleCollapse}
            />
          </div>
        )}
        {!isMinimal && (
          <div
            onMouseDown={onMouseDown}
            className="resizeHandle absolute top-1/2 -translate-y-1/2 -right-3 w-1 h-16 cursor-e-resize flex items-center justify-center opacity-60 hover:opacity-100 transition-opacity group">
            <div className="w-1 h-10 rounded-full bg-amber-400/70  transition-colors" />
          </div>
        )}
        {isClickThrough && (
          <div
            className="absolute -top-2  z-50 pointer-events-none"
            style={{ right: "-7.5px" }}>
            <img
              src={lock}
              className="w-4 h-4"></img>
            {/* <LockKeyhole className="size-4 opacity-60 text-white " /> */}
          </div>
        )}
      </div>

      <DebugConsole></DebugConsole>
      <div>
        <JoinRequestPanel />
      </div>
      <div>
        <SidePanel
          type={activePanel}
          player={selected}
          players={players}
          onClose={handleClose}
          combatTime={formatBattleTime(battleTime)}
          updateInfo={updateInfo}
          onUpdate={startUpdate}
          checkStatus={checkStatus}
          downloadState={downloadState}
          onRetryDownload={retryDownload}
          formatBattleTime={formatBattleTime}
          historyIdx={selectedHistoryIdx}
          onOpenReleasePage={openReleasePage}
          onSelectHistory={handleSelectHistory}
          currentVersion={currentVersion ?? undefined}
          onCheckUpdate={handleCheckUpdate}
        />
      </div>
    </div>
    // </TooltipProvider>
  );
}
