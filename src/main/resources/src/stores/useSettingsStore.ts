import { create } from "zustand";
import type { Hotkey, ContributionMode } from "@/types";
import { parseHotkeyString } from "@/utils/hotKey";
import { DEFAULT_VISIBLE_SKILL_CODES } from "@/constants/codes";

export type DisplayMode =
  | "dps_percent"
  | "amount_dps_percent"
  | "amount_percent"
  | "amount_full_dps_percent"
  | "amount_full_percent";
export type DpsMetric = "rdps" | "ndps";
export type TargetInfoDisplayMode =
  | "hp_full_percent"
  | "hp_percent"
  | "remain_full_percent"
  | "remain_percent"
  | "percent";
export type NameDisplay = "all" | "me_only" | "hidden";
export type HeaderPosition = "top" | "bottom";
export type FontFamily =
  | "Malgun Gothic"
  | "Spoqa Han Sans Neo"
  | "Freesentation"
  | "Tmoney Round Wind"
  | "Pretendard"
  | "NEXON Lv2 Gothic";

export interface ThemeColors {
  userBar: [string, string];
  normalBar: [string, string];
  warningBar: [string, string];
  errorBar: [string, string];
  bossBar: [string, string];
  serverAColor: string;
  serverBColor: string;
  serverDefaultColor: string;
  meterStatAmount: string;
  meterStatDps: string;
  meterStatPercent: string;
  bossRightValue: string;
  combatTimeColor: string;
}

export const DEFAULT_THEME: ThemeColors = {
  userBar: ["#fbbf24", "#d97706"],
  normalBar: ["#e0b45c", "#92400e"],
  warningBar: ["#fb923c", "#c2410c"],
  errorBar: ["#f87171", "#be123c"],
  bossBar: ["#1e1b4b", "#312e81"],
  serverAColor: "#93c5fd",
  serverBColor: "#c4b5fd",
  serverDefaultColor: "#e0e0e0",
  meterStatAmount: "#fde68a",
  meterStatDps: "#f8fafc",
  meterStatPercent: "#fbbf24",
  bossRightValue: "#fbbf24",
  combatTimeColor: "#cbd5e1",
};

const isLegacyDefaultTheme = (theme: ThemeColors) =>
  theme.userBar?.[0] === "#55c42a" && theme.normalBar?.[0] === "#ffc837";

interface SettingsState {
  // hotkey: Hotkey;
  displayMode: DisplayMode;
  setDisplayMode: (mode: DisplayMode) => void;
  dpsMetric: DpsMetric;
  setDpsMetric: (metric: DpsMetric) => void;
  targetInfoDisplayMode: TargetInfoDisplayMode;
  setTargetInfoDisplayMode: (mode: TargetInfoDisplayMode) => void;
  nameDisplay: NameDisplay;
  setNameDisplay: (mode: NameDisplay) => void;
  fontFamily: FontFamily;
  setFontFamily: (v: FontFamily) => void;
  meterWidth: number;
  setMeterWidth: (w: number) => void;
  rowHeight: number;
  setRowHeight: (h: number) => void;
  detailWidth: number;
  setDetailWidth: (w: number) => void;
  isLoaded: boolean;
  detailHeight: number;
  setDetailHeight: (h: number) => void;
  // setHotkey: (h: Hotkey) => void;
  isMinimal: boolean;
  showCombatTimerInMinimal: boolean;
  setShowCombatTimerInMinimal: (v: boolean) => void;
  showTargetInfoInMinimal: boolean;
  setShowTargetInfoInMinimal: (v: boolean) => void;

  hideHotkey: Hotkey;
  setHideHotkey: (h: Hotkey) => void;
  isDebugMode: boolean;
  headerPosition: HeaderPosition;
  setHeaderPosition: (v: HeaderPosition) => void;
  setIsMinimal: (v: boolean) => void;
  toggleMinimal: () => void;
  theme: ThemeColors;
  setTheme: (theme: ThemeColors) => void;
  setThemeColor: <K extends keyof ThemeColors>(key: K, value: ThemeColors[K]) => void;
  resetTheme: () => void;
  windowX: number;
  windowY: number;
  setWindowPosition: (x: number, y: number) => void;
  visibleSkillCodes: number[];
  setVisibleSkillCodes: (codes: number[]) => void;
  // showPower: boolean;
  // setShowPower: (v: boolean) => void;
  meterOpacity: number;
  setMeterOpacity: (v: number) => void;
  panelOpacity: number;
  setPanelOpacity: (v: number) => void;
  joinPanelOpacity: number;
  setJoinPanelOpacity: (v: number) => void;
  meterListOpacity: number;
  setMeterListOpacity: (v: number) => void;
  contributionMode: ContributionMode;
  setContributionMode: (v: ContributionMode) => void;
  clickThroughHotkey: Hotkey;
  setClickThroughHotkey: (h: Hotkey) => void;
  isClickThrough: boolean;
  isAutoHide: boolean;
  toggleAutoHide: () => void;
  joinPanelWidth: number;
  setJoinPanelWidth: (w: number) => void;
  joinPanelHeight: number;
  setJoinPanelHeight: (h: number) => void;
  joinPanelX: number;
  joinPanelY: number;
  joinPanelPositioned: boolean;
  setJoinPanelPosition: (x: number, y: number) => void;
  resetJoinPanelPosition: () => void;
  sidePanelX: number;
  sidePanelY: number;
  sidePanelPositioned: boolean;
  setSidePanelPosition: (x: number, y: number) => void;
  resetSidePanelPosition: () => void;
  settingsPanelWidth: number;
  settingsPanelHeight: number;
  setSettingsPanelWidth: (w: number) => void;
  setSettingsPanelHeight: (h: number) => void;
  historyPanelWidth: number;
  historyPanelHeight: number;
  setHistoryPanelWidth: (w: number) => void;
  setHistoryPanelHeight: (h: number) => void;
  updatePanelWidth: number;
  updatePanelHeight: number;
  setUpdatePanelWidth: (w: number) => void;
  setUpdatePanelHeight: (h: number) => void;
  uiX: number;
  uiY: number;
  resetMeterPosition: () => void;
  setUiPosition: (x: number, y: number) => void;
  guildWebUrl: string;
  setGuildWebUrl: (v: string) => void;
  guildDeviceToken: string;
  setGuildDeviceToken: (v: string) => void;
  guildLinkedName: string;
  setGuildLinkedName: (v: string) => void;
  guildAlertsEnabled: boolean;
  setGuildAlertsEnabled: (v: boolean) => void;
  trackerOverlayEnabled: boolean;
  setTrackerOverlayEnabled: (v: boolean) => void;
  showEmptyResourceChips: boolean;
  setShowEmptyResourceChips: (v: boolean) => void;
  trackedBuffCodes: number[];
  setTrackedBuffCodes: (codes: number[]) => void;
  trackerX: number;
  trackerY: number;
  setTrackerPosition: (x: number, y: number) => void;
}

const jb = () => (window as any).javaBridge;
const MAX_INIT_ATTEMPTS = 200;

const defaultSettings = {
  hotkey: { modifiers: 2, vkCode: 0x52 },
  hideHotkey: { modifiers: 2, vkCode: 0x48 },
  meterWidth: 400,
  rowHeight: 36,
  isDebugMode: false,
  detailHeight: 600,
  detailWidth: 800,
  windowX: 0,
  windowY: 0,
  isLoaded: false,
  displayMode: "dps_percent" as DisplayMode,
  dpsMetric: "rdps" as DpsMetric,
  targetInfoDisplayMode: "hp_full_percent" as TargetInfoDisplayMode,
  nameDisplay: "all" as NameDisplay,
  fontFamily: "NEXON Lv2 Gothic" as FontFamily,
  headerPosition: "top" as HeaderPosition,
  isMinimal: false,
  showCombatTimerInMinimal: true,
  showTargetInfoInMinimal: true,
  theme: DEFAULT_THEME,
  visibleSkillCodes: DEFAULT_VISIBLE_SKILL_CODES,
  // showPower: true,
  meterOpacity: 0.4,
  panelOpacity: 0.8,
  joinPanelOpacity: 0.8,
  meterListOpacity: 1,
  contributionMode: "contribution" as ContributionMode,
  clickThroughHotkey: { modifiers: 2, vkCode: 0x54 },
  isClickThrough: false,
  isAutoHide: true,
  joinPanelWidth: 400,
  joinPanelHeight: 330,
  joinPanelX: 0,
  joinPanelY: 0,
  joinPanelPositioned: false,
  sidePanelX: 0,
  sidePanelY: 0,
  sidePanelPositioned: false,
  settingsPanelWidth: 960,
  settingsPanelHeight: 680,
  historyPanelWidth: 380,
  historyPanelHeight: 520,
  updatePanelWidth: 300,
  updatePanelHeight: 160,
  uiX: 0,
  uiY: 0,
  guildWebUrl: "",
  guildDeviceToken: "",
  guildLinkedName: "",
  guildAlertsEnabled: true,
  trackerOverlayEnabled: false,
  showEmptyResourceChips: false,
  trackedBuffCodes: [] as number[],
  trackerX: 80,
  trackerY: 120,
};

export const useSettingsStore = create<SettingsState>((set) => {
  let initAttempts = 0;
  const interval = setInterval(() => {
    const j = jb();
    if (!j || typeof j.loadProps !== "function") {
      initAttempts += 1;
      if (initAttempts >= MAX_INIT_ATTEMPTS) {
        set({ isLoaded: true });
        clearInterval(interval);
      }
      return;
    }

    // const raw = j.getHotkey?.();
    const rawHide = j.getHideHotkey?.();
    const rawClickThrough = j.getClickThroughHotkey?.();
    // const parsedHotkey = raw ? parseHotkeyString(raw) : null;
    const parsedHideHotkey = rawHide ? parseHotkeyString(rawHide) : null;
    const parsedClickThroughHotkey = rawClickThrough ? parseHotkeyString(rawClickThrough) : null;
    const savedIsMinimal = j.loadProps("isMinimal") === "true";

    const savedThemeRaw = j.loadProps?.("theme");
    let savedTheme: ThemeColors = DEFAULT_THEME;

    const savedTrackedRaw = j.loadProps?.("trackedBuffCodes");
    let savedTracked: number[] = defaultSettings.trackedBuffCodes;
    try {
      if (savedTrackedRaw) savedTracked = JSON.parse(savedTrackedRaw);
    } catch {}
    const savedSkillCodesRaw = j.loadProps?.("visibleSkillCodes");
    let savedSkillCodes = DEFAULT_VISIBLE_SKILL_CODES;
    try {
      if (savedSkillCodesRaw) savedSkillCodes = JSON.parse(savedSkillCodesRaw);
    } catch {}

    try {
      if (savedThemeRaw) savedTheme = { ...DEFAULT_THEME, ...JSON.parse(savedThemeRaw) };
      if (isLegacyDefaultTheme(savedTheme)) {
        savedTheme = DEFAULT_THEME;
        j.saveProps?.("theme", JSON.stringify(DEFAULT_THEME));
      }
    } catch {}

    const savedSidePanelXRaw = j.loadProps?.("sidePanelX");
    const savedSidePanelYRaw = j.loadProps?.("sidePanelY");
    const hasSavedSidePanelX = savedSidePanelXRaw != null && savedSidePanelXRaw !== "";
    const hasSavedSidePanelY = savedSidePanelYRaw != null && savedSidePanelYRaw !== "";
    const sidePanelPositioned = hasSavedSidePanelX || hasSavedSidePanelY;
    const savedJoinPanelXRaw = j.loadProps?.("joinPanelX");
    const savedJoinPanelYRaw = j.loadProps?.("joinPanelY");
    const hasSavedJoinPanelX = savedJoinPanelXRaw != null && savedJoinPanelXRaw !== "";
    const hasSavedJoinPanelY = savedJoinPanelYRaw != null && savedJoinPanelYRaw !== "";
    const joinPanelPositioned = hasSavedJoinPanelX || hasSavedJoinPanelY;
    const savedMeterOpacityRaw = j.loadProps?.("meterOpacity");
    const savedPanelOpacityRaw = j.loadProps?.("panelOpacity");
    const savedJoinPanelOpacityRaw = j.loadProps?.("joinPanelOpacity");
    const savedMeterListOpacityRaw = j.loadProps?.("meterListOpacity");

    set({
      // hotkey: parsedHotkey ?? defaultSettings.hotkey,
      hideHotkey: parsedHideHotkey ?? defaultSettings.hideHotkey,
      meterWidth: Number(j.loadProps?.("meterWidth")) || defaultSettings.meterWidth,
      rowHeight: Number(j.loadProps?.("rowHeight")) || defaultSettings.rowHeight,
      detailHeight: Number(j.loadProps?.("detailHeight")) || defaultSettings.detailHeight,
      detailWidth: Number(j.loadProps?.("detailWidth")) || defaultSettings.detailWidth,
      displayMode: j.loadProps?.("displayMode") ?? defaultSettings.displayMode,
      dpsMetric: (j.loadProps?.("dpsMetric") as DpsMetric) || defaultSettings.dpsMetric,
      targetInfoDisplayMode:
        j.loadProps?.("targetInfoDisplayMode") ?? defaultSettings.targetInfoDisplayMode,
      isDebugMode: j.isDebuggingMode?.() ?? false,
      nameDisplay: j.loadProps?.("nameDisplay") ?? defaultSettings.nameDisplay,
      fontFamily: (j.loadProps?.("fontFamily") as FontFamily) ?? defaultSettings.fontFamily,
      isMinimal: savedIsMinimal,
      showCombatTimerInMinimal: j.loadProps?.("showCombatTimerInMinimal") === "true",
      showTargetInfoInMinimal: j.loadProps?.("showTargetInfoInMinimal") === "true",
      headerPosition: j.loadProps?.("headerPosition") ?? defaultSettings.headerPosition,
      theme: savedTheme,
      visibleSkillCodes: savedSkillCodes,
      windowX: Number(j.loadProps?.("windowX")) || defaultSettings.windowX,
      windowY: Number(j.loadProps?.("windowY")) || defaultSettings.windowY,
      // showPower: j.loadProps?.("showPower") === "false" ? false : true,
      meterOpacity:
        savedMeterOpacityRaw != null && savedMeterOpacityRaw !== ""
          ? Number(savedMeterOpacityRaw)
          : defaultSettings.meterOpacity,
      panelOpacity:
        savedPanelOpacityRaw != null && savedPanelOpacityRaw !== ""
          ? Number(savedPanelOpacityRaw)
          : defaultSettings.panelOpacity,
      joinPanelOpacity:
        savedJoinPanelOpacityRaw != null && savedJoinPanelOpacityRaw !== ""
          ? Number(savedJoinPanelOpacityRaw)
          : defaultSettings.joinPanelOpacity,
      meterListOpacity:
        savedMeterListOpacityRaw != null && savedMeterListOpacityRaw !== ""
          ? Number(savedMeterListOpacityRaw)
          : defaultSettings.meterListOpacity,
      contributionMode:
        (j.loadProps?.("contributionMode") as ContributionMode) ?? defaultSettings.contributionMode,
      clickThroughHotkey: parsedClickThroughHotkey ?? defaultSettings.clickThroughHotkey,
      isClickThrough: j.isClickThrough?.() ?? false,
      isAutoHide: j.isAutoHide?.() ?? false,
      joinPanelWidth: Number(j.loadProps?.("joinPanelWidth")) || defaultSettings.joinPanelWidth,
      joinPanelHeight: Number(j.loadProps?.("joinPanelHeight")) || defaultSettings.joinPanelHeight,
      joinPanelX: hasSavedJoinPanelX ? Number(savedJoinPanelXRaw) : defaultSettings.joinPanelX,
      joinPanelY: hasSavedJoinPanelY ? Number(savedJoinPanelYRaw) : defaultSettings.joinPanelY,
      joinPanelPositioned,
      sidePanelX: hasSavedSidePanelX ? Number(savedSidePanelXRaw) : defaultSettings.sidePanelX,
      sidePanelY: hasSavedSidePanelY ? Number(savedSidePanelYRaw) : defaultSettings.sidePanelY,
      sidePanelPositioned,
      settingsPanelWidth: (() => {
        const n = Number(j.loadProps?.("settingsPanelWidth")) || defaultSettings.settingsPanelWidth;
        return n < 720 ? defaultSettings.settingsPanelWidth : n;
      })(),
      settingsPanelHeight:
        Number(j.loadProps?.("settingsPanelHeight")) || defaultSettings.settingsPanelHeight,
      historyPanelWidth:
        Number(j.loadProps?.("historyPanelWidth")) || defaultSettings.historyPanelWidth,
      historyPanelHeight:
        Number(j.loadProps?.("historyPanelHeight")) || defaultSettings.historyPanelHeight,
      updatePanelWidth:
        Number(j.loadProps?.("updatePanelWidth")) || defaultSettings.updatePanelWidth,
      updatePanelHeight:
        Number(j.loadProps?.("updatePanelHeight")) || defaultSettings.updatePanelHeight,
      uiX: Number(j.loadProps?.("uiX")) || defaultSettings.uiX,
      uiY: Number(j.loadProps?.("uiY")) || defaultSettings.uiY,
      guildWebUrl: j.loadProps?.("guildWebUrl") || defaultSettings.guildWebUrl,
      guildDeviceToken: j.loadProps?.("guildDeviceToken") || defaultSettings.guildDeviceToken,
      guildLinkedName: j.loadProps?.("guildLinkedName") || defaultSettings.guildLinkedName,
      guildAlertsEnabled: j.loadProps?.("guildAlertsEnabled") === "false" ? false : true,
      trackerOverlayEnabled: j.loadProps?.("trackerOverlayEnabled") === "true",
      showEmptyResourceChips: j.loadProps?.("showEmptyResourceChips") === "true",
      trackedBuffCodes: Array.isArray(savedTracked) ? savedTracked.slice(0, 8) : defaultSettings.trackedBuffCodes,
      trackerX: Number(j.loadProps?.("trackerX")) || defaultSettings.trackerX,
      trackerY: Number(j.loadProps?.("trackerY")) || defaultSettings.trackerY,

      isLoaded: true,
    });
    clearInterval(interval);
  }, 100);

  return {
    hotkey: defaultSettings.hotkey,
    hideHotkey: defaultSettings.hideHotkey,
    isMinimal: defaultSettings.isMinimal,
    showCombatTimerInMinimal: defaultSettings.showCombatTimerInMinimal,
    showTargetInfoInMinimal: defaultSettings.showTargetInfoInMinimal,

    meterWidth: defaultSettings.meterWidth,
    rowHeight: defaultSettings.rowHeight,
    detailHeight: defaultSettings.detailHeight,
    detailWidth: defaultSettings.detailWidth,
    visibleSkillCodes: defaultSettings.visibleSkillCodes,
    displayMode: defaultSettings.displayMode,
    dpsMetric: defaultSettings.dpsMetric,
    targetInfoDisplayMode: defaultSettings.targetInfoDisplayMode,
    nameDisplay: defaultSettings.nameDisplay,
    fontFamily: defaultSettings.fontFamily,
    isDebugMode: defaultSettings.isDebugMode,
    headerPosition: defaultSettings.headerPosition,
    theme: defaultSettings.theme,
    windowX: defaultSettings.windowX,
    windowY: defaultSettings.windowY,
    // showPower: defaultSettings.showPower,
    meterOpacity: defaultSettings.meterOpacity,
    panelOpacity: defaultSettings.panelOpacity,
    joinPanelOpacity: defaultSettings.joinPanelOpacity,
    meterListOpacity: defaultSettings.meterListOpacity,
    contributionMode: defaultSettings.contributionMode,
    clickThroughHotkey: defaultSettings.clickThroughHotkey,
    isClickThrough: defaultSettings.isClickThrough,
    isAutoHide: defaultSettings.isAutoHide,
    isLoaded: defaultSettings.isLoaded,

    joinPanelWidth: defaultSettings.joinPanelWidth,
    joinPanelHeight: defaultSettings.joinPanelHeight,
    joinPanelX: defaultSettings.joinPanelX,
    joinPanelY: defaultSettings.joinPanelY,
    joinPanelPositioned: defaultSettings.joinPanelPositioned,
    sidePanelX: defaultSettings.sidePanelX,
    sidePanelY: defaultSettings.sidePanelY,
    sidePanelPositioned: defaultSettings.sidePanelPositioned,
    settingsPanelWidth: defaultSettings.settingsPanelWidth,
    settingsPanelHeight: defaultSettings.settingsPanelHeight,
    historyPanelWidth: defaultSettings.historyPanelWidth,
    historyPanelHeight: defaultSettings.historyPanelHeight,
    updatePanelWidth: defaultSettings.updatePanelWidth,
    updatePanelHeight: defaultSettings.updatePanelHeight,
    uiX: defaultSettings.uiX,
    uiY: defaultSettings.uiY,
    guildWebUrl: defaultSettings.guildWebUrl,
    guildDeviceToken: defaultSettings.guildDeviceToken,
    guildLinkedName: defaultSettings.guildLinkedName,
    guildAlertsEnabled: defaultSettings.guildAlertsEnabled,
    trackerOverlayEnabled: defaultSettings.trackerOverlayEnabled,
    showEmptyResourceChips: defaultSettings.showEmptyResourceChips,
    trackedBuffCodes: defaultSettings.trackedBuffCodes,
    trackerX: defaultSettings.trackerX,
    trackerY: defaultSettings.trackerY,

    // setHotkey: (hotkey) => {
    //   set({ hotkey });
    //   jb()?.updateHotkey?.(hotkey.modifiers, hotkey.vkCode);
    // },
    setHideHotkey: (hideHotkey) => {
      set({ hideHotkey });
      jb()?.updateHideHotkey?.(hideHotkey.modifiers, hideHotkey.vkCode);
    },
    setIsMinimal: (isMinimal) => {
      set({ isMinimal });
      jb()?.saveProps?.("isMinimal", String(isMinimal));
    },
    setShowCombatTimerInMinimal: (v) => {
      set({ showCombatTimerInMinimal: v });
      jb()?.saveProps?.("showCombatTimerInMinimal", String(v));
    },
    setShowTargetInfoInMinimal: (v) => {
      set({ showTargetInfoInMinimal: v });
      jb()?.saveProps?.("showTargetInfoInMinimal", String(v));
    },
    toggleMinimal: () =>
      set((s) => {
        const next = !s.isMinimal;
        jb()?.saveProps?.("isMinimal", String(next));
        return { isMinimal: next };
      }),
    setDisplayMode: (displayMode) => {
      set({ displayMode });
      jb()?.saveProps?.("displayMode", displayMode);
    },
    setDpsMetric: (dpsMetric) => {
      set({ dpsMetric });
      jb()?.saveProps?.("dpsMetric", dpsMetric);
    },
    setTargetInfoDisplayMode: (targetInfoDisplayMode) => {
      set({ targetInfoDisplayMode });
      jb()?.saveProps?.("targetInfoDisplayMode", targetInfoDisplayMode);
    },
    setNameDisplay: (nameDisplay) => {
      set({ nameDisplay });
      jb()?.saveProps?.("nameDisplay", nameDisplay);
    },
    setFontFamily: (fontFamily) => {
      set({ fontFamily });
      jb()?.saveProps?.("fontFamily", fontFamily);
    },
    setMeterWidth: (meterWidth) => {
      set({ meterWidth });
      jb()?.saveProps?.("meterWidth", meterWidth);
    },
    setRowHeight: (rowHeight) => {
      set({ rowHeight });
      jb()?.saveProps?.("rowHeight", rowHeight);
    },
    setDetailHeight: (detailHeight) => {
      set({ detailHeight });
      jb()?.saveProps?.("detailHeight", detailHeight);
    },
    setDetailWidth: (detailWidth) => {
      set({ detailWidth });
      jb()?.saveProps?.("detailWidth", detailWidth);
    },
    setHeaderPosition: (headerPosition) => {
      set({ headerPosition });
      jb()?.saveProps?.("headerPosition", headerPosition);
    },
    setTheme: (theme) => {
      set({ theme });
      jb()?.saveProps?.("theme", JSON.stringify(theme));
    },
    setThemeColor: (key, value) =>
      set((s) => {
        const next = { ...s.theme, [key]: value };
        jb()?.saveProps?.("theme", JSON.stringify(next));
        return { theme: next };
      }),
    resetTheme: () => {
      set({ theme: DEFAULT_THEME });
      jb()?.saveProps?.("theme", JSON.stringify(DEFAULT_THEME));
    },
    setWindowPosition: (windowX, windowY) => {
      set({ windowX, windowY });
      jb()?.saveProps?.("windowX", String(windowX));
      jb()?.saveProps?.("windowY", String(windowY));
    },
    setVisibleSkillCodes: (visibleSkillCodes) => {
      set({ visibleSkillCodes });
      jb()?.saveProps?.("visibleSkillCodes", JSON.stringify(visibleSkillCodes));
    },
    setMeterOpacity: (meterOpacity) => {
      set({ meterOpacity });
      jb()?.saveProps?.("meterOpacity", String(meterOpacity));
    },
    setPanelOpacity: (panelOpacity) => {
      set({ panelOpacity });
      jb()?.saveProps?.("panelOpacity", String(panelOpacity));
    },
    setJoinPanelOpacity: (joinPanelOpacity) => {
      set({ joinPanelOpacity });
      jb()?.saveProps?.("joinPanelOpacity", String(joinPanelOpacity));
    },
    setMeterListOpacity: (meterListOpacity) => {
      set({ meterListOpacity });
      jb()?.saveProps?.("meterListOpacity", String(meterListOpacity));
    },
    setContributionMode: (contributionMode) => {
      set({ contributionMode });
      jb()?.saveProps?.("contributionMode", contributionMode);
    },
    setClickThroughHotkey: (clickThroughHotkey) => {
      set({ clickThroughHotkey });
      jb()?.updateClickThroughHotkey?.(clickThroughHotkey.modifiers, clickThroughHotkey.vkCode);
    },
    toggleAutoHide: () =>
      set((s) => {
        jb()?.toggleAutoHide?.();
        return { isAutoHide: !s.isAutoHide };
      }),
    // setShowPower: (showPower) => {
    //   set({ showPower });
    //   jb()?.saveProps?.("showPower", String(showPower));
    // },
    setJoinPanelWidth: (joinPanelWidth) => {
      set({ joinPanelWidth });
      jb()?.saveProps?.("joinPanelWidth", String(joinPanelWidth));
    },
    setJoinPanelHeight: (joinPanelHeight) => {
      set({ joinPanelHeight });
      jb()?.saveProps?.("joinPanelHeight", String(joinPanelHeight));
    },
    setJoinPanelPosition: (joinPanelX, joinPanelY) => {
      set({ joinPanelX, joinPanelY, joinPanelPositioned: true });
      jb()?.saveProps?.("joinPanelX", String(joinPanelX));
      jb()?.saveProps?.("joinPanelY", String(joinPanelY));
    },
    resetJoinPanelPosition: () => {
      const meterRoot = document.querySelector("[data-meter-root-anchor]");
      const rect = meterRoot?.getBoundingClientRect();
      const x = rect ? rect.left : 0;
      const y = rect ? rect.bottom + 8 : 8;

      set({
        joinPanelX: x,
        joinPanelY: y,
        joinPanelPositioned: true, 
      });
      jb()?.saveProps?.("joinPanelX", String(x));
      jb()?.saveProps?.("joinPanelY", String(y));
    },
    setSidePanelPosition: (sidePanelX, sidePanelY) => {
      set({ sidePanelX, sidePanelY, sidePanelPositioned: true });
      jb()?.saveProps?.("sidePanelX", String(sidePanelX));
      jb()?.saveProps?.("sidePanelY", String(sidePanelY));
    },
    resetSidePanelPosition: () => {
      const meterRoot = document.querySelector("[data-meter-root-anchor]");
      const rect = meterRoot?.getBoundingClientRect();
      const x = rect ? rect.right + 8 : 408;
      const y = rect ? rect.top : 8;

      set({
        sidePanelX: x,
        sidePanelY: y,
        sidePanelPositioned: true,
      });
      jb()?.saveProps?.("sidePanelX", String(x));
      jb()?.saveProps?.("sidePanelY", String(y));
    },
    resetMeterPosition: () => {
      set({
        uiX: defaultSettings.uiX,
        uiY: defaultSettings.uiY,
      });
      jb()?.saveProps?.("uiX", "0");
      jb()?.saveProps?.("uiY", "0");
    },
    setSettingsPanelWidth: (settingsPanelWidth) => {
      set({ settingsPanelWidth });
      jb()?.saveProps?.("settingsPanelWidth", String(settingsPanelWidth));
    },
    setSettingsPanelHeight: (settingsPanelHeight) => {
      set({ settingsPanelHeight });
      jb()?.saveProps?.("settingsPanelHeight", String(settingsPanelHeight));
    },
    setHistoryPanelWidth: (historyPanelWidth) => {
      set({ historyPanelWidth });
      jb()?.saveProps?.("historyPanelWidth", String(historyPanelWidth));
    },
    setHistoryPanelHeight: (historyPanelHeight) => {
      set({ historyPanelHeight });
      jb()?.saveProps?.("historyPanelHeight", String(historyPanelHeight));
    },
    setUpdatePanelWidth: (updatePanelWidth) => {
      set({ updatePanelWidth });
      jb()?.saveProps?.("updatePanelWidth", String(updatePanelWidth));
    },
    setUpdatePanelHeight: (updatePanelHeight) => {
      set({ updatePanelHeight });
      jb()?.saveProps?.("updatePanelHeight", String(updatePanelHeight));
    },
    setUiPosition: (uiX, uiY) => {
      set({ uiX, uiY });
      jb()?.saveProps?.("uiX", String(uiX));
      jb()?.saveProps?.("uiY", String(uiY));
    },
    setGuildWebUrl: (guildWebUrl) => {
      set({ guildWebUrl });
      jb()?.saveProps?.("guildWebUrl", guildWebUrl);
    },
    setGuildDeviceToken: (guildDeviceToken) => {
      set({ guildDeviceToken });
      jb()?.saveProps?.("guildDeviceToken", guildDeviceToken);
    },
    setGuildLinkedName: (guildLinkedName) => {
      set({ guildLinkedName });
      jb()?.saveProps?.("guildLinkedName", guildLinkedName);
    },
    setGuildAlertsEnabled: (guildAlertsEnabled) => {
      set({ guildAlertsEnabled });
      jb()?.saveProps?.("guildAlertsEnabled", String(guildAlertsEnabled));
    },
    setTrackerOverlayEnabled: (trackerOverlayEnabled) => {
      set({ trackerOverlayEnabled });
      jb()?.saveProps?.("trackerOverlayEnabled", String(trackerOverlayEnabled));
      jb()?.setTrackerOverlayEnabled?.(trackerOverlayEnabled);
    },
    setShowEmptyResourceChips: (showEmptyResourceChips) => {
      set({ showEmptyResourceChips });
      jb()?.saveProps?.("showEmptyResourceChips", String(showEmptyResourceChips));
    },
    setTrackerPosition: (trackerX, trackerY) => {
      set({ trackerX, trackerY });
      jb()?.saveProps?.("trackerX", String(trackerX));
      jb()?.saveProps?.("trackerY", String(trackerY));
    },
    setTrackedBuffCodes: (trackedBuffCodes) => {
      const next = trackedBuffCodes.slice(0, 8);
      set({ trackedBuffCodes: next });
      jb()?.saveProps?.("trackedBuffCodes", JSON.stringify(next));
    },
  };
});
