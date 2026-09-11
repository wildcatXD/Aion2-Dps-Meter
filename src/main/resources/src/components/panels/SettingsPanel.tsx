import { useCallback, useEffect, useRef, useState } from "react";
import { useSettingsStore } from "@/stores/useSettingsStore";
import { useShallow } from "zustand/react/shallow";
import { useHotkeyCapture } from "@/hooks/useHotkeyCapture";
import { formatHotkey } from "@/utils/hotKey";
import { Button } from "@/components/ui/button";
import {
  Bell,
  Crosshair,
  Gauge,
  Keyboard,
  Palette,
  RotateCcw,
  SlidersHorizontal,
} from "lucide-react";
import type {
  DisplayMode,
  DpsMetric,
  FontFamily,
  HeaderPosition,
  NameDisplay,
  TargetInfoDisplayMode,
  ThemeColors,
} from "@/stores/useSettingsStore";
import type { ContributionMode } from "@/types";
import { Slider } from "@/components/ui/slider";
import { Switch } from "@/components/ui/switch";
import { SettingsItem } from "./SettingsItem";
import { SettingsRow } from "./SettingsRow";
import { SettingsControlInput } from "./SettingsControlInput";
import { ColorSwatch, GradientRow } from "@/components/colorpicker";
import { GuildPairSettings } from "./GuildPairSettings";
import { TrackerSkillPicker } from "./TrackerSkillPicker";
import { SettingsMeterPreview } from "./SettingsMeterPreview";
import type { UpdateInfo } from "@/types";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { cn } from "@/lib/utils";

interface Props {
  onClose: () => void;
  onReady?: () => void;
  currentVersion?: string;
  updateInfo?: UpdateInfo | null;
  onCheckUpdate?: () => void;
  registerHeaderClose?: (handler: (() => void) | null) => void;
}

type SettingsTab = "general" | "meter" | "theme" | "controls" | "tracker" | "guild";

const TABS: { id: SettingsTab; label: string; icon: typeof Gauge }[] = [
  { id: "general", label: "일반", icon: SlidersHorizontal },
  { id: "meter", label: "미터", icon: Gauge },
  { id: "theme", label: "테마", icon: Palette },
  { id: "controls", label: "조작", icon: Keyboard },
  { id: "tracker", label: "추적", icon: Crosshair },
  { id: "guild", label: "길드", icon: Bell },
];

const DPS_METRICS: { value: DpsMetric; label: string; description: string }[] = [
  { value: "rdps", label: "실딜 rDPS", description: "실제 피해 / 전투시간. nDPS가 아닙니다." },
  {
    value: "ndps",
    label: "개인 nDPS · 실험",
    description: "파티 질풍·격앙·노련한 반격·대지의 은총을 근사 제거",
  },
];

const DISPLAY_MODES: { value: DisplayMode; label: string; description: string }[] = [
  { value: "dps_percent", label: "DPS / 기여도", description: "45,000/초 (35.5%)" },
  {
    value: "amount_dps_percent",
    label: "누적(축약) / DPS / 기여도",
    description: "1.2M 45,000/초 (35.5%)",
  },
  { value: "amount_percent", label: "누적(축약) / 기여도", description: "1.2M (35.5%)" },
  {
    value: "amount_full_dps_percent",
    label: "누적(전체) / DPS / 기여도",
    description: "1,234,567 45,000/초 (35.5%)",
  },
  { value: "amount_full_percent", label: "누적(전체) / 기여도", description: "1,234,567 (35.5%)" },
];

const TARGET_INFO_DISPLAY_MODES: {
  value: TargetInfoDisplayMode;
  label: string;
  description: string;
}[] = [
  {
    value: "hp_full_percent",
    label: "남은/최대(전체) / 퍼센트",
    description: "1,234,567 / 9,876,543 12.5%",
  },
  {
    value: "hp_percent",
    label: "남은/최대(축약) / 퍼센트",
    description: "1.2M / 9.9M 12.5%",
  },
  {
    value: "remain_full_percent",
    label: "남은 체력(전체) / 퍼센트",
    description: "1,234,567 12.5%",
  },
  {
    value: "remain_percent",
    label: "남은 체력(축약) / 퍼센트",
    description: "1.2M 12.5%",
  },
  { value: "percent", label: "퍼센트만", description: "12.5%" },
];

const NAME_DISPLAY_MODES: { value: NameDisplay; label: string }[] = [
  { value: "all", label: "모두 표기" },
  { value: "me_only", label: "나만 표기" },
  { value: "hidden", label: "모두 숨김" },
];

const FONT_FAMILIES: { value: FontFamily; label: string }[] = [
  { value: "Malgun Gothic", label: "맑은 고딕 (윈도우 기본 폰트)" },
  { value: "NEXON Lv2 Gothic", label: "NEXON Lv2 Gothic" },
  { value: "Spoqa Han Sans Neo", label: "Spoqa Han Sans Neo" },
  { value: "Freesentation", label: "Freesentation" },
  { value: "Tmoney Round Wind", label: "Tmoney Round Wind" },
  { value: "Pretendard", label: "Pretendard" },
];

export const SettingsPanel = ({
  onClose,
  onReady,
  currentVersion,
  updateInfo,
  onCheckUpdate,
  registerHeaderClose,
}: Props) => {
  const [tab, setTab] = useState<SettingsTab>("general");
  const {
    hideHotkey,
    displayMode,
    dpsMetric,
    targetInfoDisplayMode,
    nameDisplay,
    fontFamily,
    rowHeight,
    isMinimal,
    headerPosition,
    theme,
    showCombatTimerInMinimal,
    showTargetInfoInMinimal,
    meterOpacity,
    meterListOpacity,
    contributionMode,
    clickThroughHotkey,
    isClickThrough,
    isAutoHide,
    guildAlertsEnabled,
    trackerOverlayEnabled,
    showEmptyResourceChips,
  } = useSettingsStore(
    useShallow((s) => ({
      hideHotkey: s.hideHotkey,
      displayMode: s.displayMode,
      dpsMetric: s.dpsMetric,
      targetInfoDisplayMode: s.targetInfoDisplayMode,
      nameDisplay: s.nameDisplay,
      fontFamily: s.fontFamily,
      rowHeight: s.rowHeight,
      isMinimal: s.isMinimal,
      headerPosition: s.headerPosition,
      theme: s.theme,
      showCombatTimerInMinimal: s.showCombatTimerInMinimal,
      showTargetInfoInMinimal: s.showTargetInfoInMinimal,
      meterOpacity: s.meterOpacity,
      meterListOpacity: s.meterListOpacity,
      contributionMode: s.contributionMode,
      clickThroughHotkey: s.clickThroughHotkey,
      isClickThrough: s.isClickThrough,
      isAutoHide: s.isAutoHide,
      guildAlertsEnabled: s.guildAlertsEnabled,
      trackerOverlayEnabled: s.trackerOverlayEnabled,
      showEmptyResourceChips: s.showEmptyResourceChips,
    })),
  );

  const {
    setHideHotkey,
    setDisplayMode,
    setDpsMetric,
    setTargetInfoDisplayMode,
    setNameDisplay,
    setFontFamily,
    setRowHeight,
    setIsMinimal,
    setHeaderPosition,
    setThemeColor,
    setTheme,
    resetTheme,
    setShowCombatTimerInMinimal,
    setShowTargetInfoInMinimal,
    setMeterOpacity,
    setMeterListOpacity,
    setContributionMode,
    setClickThroughHotkey,
    toggleAutoHide,
    resetJoinPanelPosition,
    resetSidePanelPosition,
    resetMeterPosition,
    setGuildAlertsEnabled,
    setTrackerOverlayEnabled,
    setShowEmptyResourceChips,
  } = useSettingsStore.getState();
  const {
    pending: pendingHide,
    start: startHide,
    stop: stopHide,
    reset: resetHide,
  } = useHotkeyCapture(hideHotkey);
  const {
    pending: pendingClickThrough,
    start: startClickThrough,
    stop: stopClickThrough,
    reset: resetClickThrough,
  } = useHotkeyCapture(clickThroughHotkey);

  const [snapshot] = useState(() => ({
    hideHotkey,
    displayMode,
    dpsMetric,
    targetInfoDisplayMode,
    headerPosition,
    nameDisplay,
    fontFamily,
    rowHeight,
    isMinimal,
    showCombatTimerInMinimal,
    showTargetInfoInMinimal,
    meterOpacity,
    meterListOpacity,
    contributionMode,
    clickThroughHotkey,
    guildAlertsEnabled,
    trackerOverlayEnabled,
    showEmptyResourceChips,
    theme: structuredClone(theme),
  }));

  useEffect(() => {
    onReady?.();
  }, []);

  const handleSave = useCallback(() => {
    setHideHotkey(pendingHide);
    setClickThroughHotkey(pendingClickThrough);
    onClose();
  }, [setHideHotkey, pendingHide, setClickThroughHotkey, pendingClickThrough, onClose]);

  const handleCancel = useCallback(() => {
    setDisplayMode(snapshot.displayMode);
    setDpsMetric(snapshot.dpsMetric);
    setTargetInfoDisplayMode(snapshot.targetInfoDisplayMode);
    setNameDisplay(snapshot.nameDisplay);
    setFontFamily(snapshot.fontFamily);
    setRowHeight(snapshot.rowHeight);
    setIsMinimal(snapshot.isMinimal);
    setShowCombatTimerInMinimal(snapshot.showCombatTimerInMinimal);
    setShowTargetInfoInMinimal(snapshot.showTargetInfoInMinimal);
    resetHide(snapshot.hideHotkey);
    setHeaderPosition(snapshot.headerPosition);
    setTheme(snapshot.theme as ThemeColors);
    setMeterOpacity(snapshot.meterOpacity);
    setMeterListOpacity(snapshot.meterListOpacity);
    setContributionMode(snapshot.contributionMode);
    resetClickThrough(snapshot.clickThroughHotkey);
    setGuildAlertsEnabled(snapshot.guildAlertsEnabled);
    setTrackerOverlayEnabled(snapshot.trackerOverlayEnabled);
    setShowEmptyResourceChips(snapshot.showEmptyResourceChips);
    onClose();
  }, [
    onClose,
    resetClickThrough,
    resetHide,
    setContributionMode,
    setDisplayMode,
    setDpsMetric,
    setFontFamily,
    setGuildAlertsEnabled,
    setHeaderPosition,
    setIsMinimal,
    setMeterListOpacity,
    setMeterOpacity,
    setNameDisplay,
    setRowHeight,
    setShowCombatTimerInMinimal,
    setShowEmptyResourceChips,
    setShowTargetInfoInMinimal,
    setTheme,
    setTargetInfoDisplayMode,
    setTrackerOverlayEnabled,
    snapshot,
  ]);

  const handleCancelRef = useRef(handleCancel);
  useEffect(() => {
    handleCancelRef.current = handleCancel;
  }, [handleCancel]);

  const stableHandleCancel = useCallback(() => {
    handleCancelRef.current();
  }, []);

  useEffect(() => {
    registerHeaderClose?.(stableHandleCancel);
    return () => registerHeaderClose?.(null);
  }, [registerHeaderClose, stableHandleCancel]);

  return (
    <div className="flex min-h-0 flex-1 flex-col overflow-hidden">
      <div className="flex min-h-0 flex-1 overflow-hidden">
        <nav className="flex w-[120px] shrink-0 flex-col gap-0.5 border-r border-amber-500/15 bg-black/25 px-1.5 py-2">
          {TABS.map(({ id, label, icon: Icon }) => (
            <button
              key={id}
              type="button"
              onClick={() => setTab(id)}
              className={cn(
                "flex items-center gap-2 rounded-md px-2 py-2 text-left text-xs transition-colors",
                tab === id
                  ? "bg-amber-500/20 text-amber-200"
                  : "text-slate-300/80 hover:bg-white/5 hover:text-slate-100",
              )}>
              <Icon className="size-3.5 shrink-0 opacity-80" />
              {label}
            </button>
          ))}
        </nav>

        <div className="flex min-h-0 min-w-0 flex-1 flex-col overflow-y-auto overflow-x-hidden px-4 py-2">
          {tab === "general" && (
            <>
              <SettingsItem>
                <SettingsRow
                  title="버전 정보"
                  description={currentVersion ? `v${currentVersion}` : "-"}
                  rightClassName="flex items-center">
                  <Button
                    onClick={onCheckUpdate}
                    variant="ghost"
                    size="lg"
                    className={
                      updateInfo
                        ? " py-3 transition-all text-green-400 border border-green-400/30 hover:bg-green-400/10"
                        : " py-3 transition-all opacity-60 hover:opacity-100"
                    }>
                    {updateInfo ? `v${updateInfo.latestVersion} 업데이트` : "업데이트 확인"}
                  </Button>
                </SettingsRow>
              </SettingsItem>
              <SettingsItem>
                <SettingsRow
                  title="폰트"
                  description="표시 글꼴을 선택합니다"
                  align="center"
                  rightClassName="w-44">
                  <Select
                    value={fontFamily}
                    onValueChange={(v) => setFontFamily(v as FontFamily)}>
                    <SelectTrigger className="w-44 bg-white/5 border-white/10 text-sm">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {FONT_FAMILIES.map(({ value, label }) => (
                        <SelectItem
                          key={value}
                          value={value}
                          className="px-4 py-2">
                          {label}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </SettingsRow>
              </SettingsItem>
              <SettingsItem>
                <SettingsRow
                  title="자동 숨김"
                  description="아이온2가 포커스가 아닐 때 미터기를 숨깁니다.">
                  <Switch
                    checked={isAutoHide}
                    onCheckedChange={toggleAutoHide}
                    className="data-[state=checked]:bg-amber-500"
                  />
                </SettingsRow>
              </SettingsItem>
            </>
          )}

          {tab === "meter" && (
            <>
              <SettingsItem title="레이아웃">
                <SettingsRow
                  title="버튼 위치"
                  description="미터 헤더 버튼을 위 또는 아래에 둡니다.">
                  <Select
                    value={headerPosition}
                    onValueChange={(v) => setHeaderPosition(v as HeaderPosition)}>
                    <SelectTrigger className="w-24 bg-white/5 border-white/10 ">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem
                        value="top"
                        className="px-4 py-2">
                        상단
                      </SelectItem>
                      <SelectItem
                        value="bottom"
                        className="px-4 py-2">
                        하단
                      </SelectItem>
                    </SelectContent>
                  </Select>
                </SettingsRow>
                <SettingsRow
                  title="컴팩트 모드"
                  description="헤더를 숨기고 전투 중 미터만 남깁니다.">
                  <Switch
                    checked={isMinimal}
                    onCheckedChange={(v) => setIsMinimal(v)}
                    className="data-[state=checked]:bg-amber-500"
                  />
                </SettingsRow>
                <SettingsRow title="컴팩트 모드 중 전투 시간 표시">
                  <Switch
                    checked={showCombatTimerInMinimal}
                    disabled={!isMinimal}
                    onCheckedChange={(v) => setShowCombatTimerInMinimal(v)}
                    className="data-[state=checked]:bg-amber-500 disabled:opacity-30"
                  />
                </SettingsRow>
                <SettingsRow title="컴팩트 모드 중 보스 표시">
                  <Switch
                    checked={showTargetInfoInMinimal}
                    disabled={!isMinimal}
                    onCheckedChange={(v) => setShowTargetInfoInMinimal(v)}
                    className="data-[state=checked]:bg-amber-500 disabled:opacity-30"
                  />
                </SettingsRow>
              </SettingsItem>
              <SettingsItem title="숫자 표시">
              <SettingsRow
                title="기여도 표시 방식"
                align="center"
                rightClassName="w-44">
                <Select
                  value={contributionMode}
                  onValueChange={(v) => setContributionMode(v as ContributionMode)}>
                  <SelectTrigger className="text-xs w-44 bg-white/5 border-white/10">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem
                      value="contribution"
                      className="px-4 py-2">
                      파티 기여도 (상대)
                    </SelectItem>
                    <SelectItem
                      value="entireContribution"
                      className="px-4 py-2">
                      보스 체력 기여도 (절대)
                    </SelectItem>
                  </SelectContent>
                </Select>
              </SettingsRow>
              <SettingsRow
                title="딜 지표"
                description="rDPS = 실제 피해. nDPS = 파티 시너지 근사 제거(낫터기와 다를 수 있음)."
                align="center"
                rightClassName="w-44">
                <Select
                  value={dpsMetric}
                  onValueChange={(v) => setDpsMetric(v as DpsMetric)}>
                  <SelectTrigger className="text-xs w-44 bg-white/5 border-white/10">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {DPS_METRICS.map(({ value, label }) => (
                      <SelectItem
                        key={value}
                        value={value}
                        className="px-4 py-2">
                        {label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </SettingsRow>
              <SettingsRow
                title="표시 형식"
                align="center"
                rightClassName="w-44">
                <Select
                  value={displayMode}
                  onValueChange={(v) => setDisplayMode(v as DisplayMode)}>
                  <SelectTrigger className="text-xs w-44 bg-white/5 border-white/10 ">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {DISPLAY_MODES.map(({ value, label }) => (
                      <SelectItem
                        key={value}
                        value={value}
                        className="px-4 py-2">
                        {label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </SettingsRow>
              <SettingsRow
                title="보스 표시 형식"
                align="center"
                rightClassName="w-44">
                <Select
                  value={targetInfoDisplayMode}
                  onValueChange={(v) => setTargetInfoDisplayMode(v as TargetInfoDisplayMode)}>
                  <SelectTrigger className="text-xs w-44 bg-white/5 border-white/10 ">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {TARGET_INFO_DISPLAY_MODES.map(({ value, label }) => (
                      <SelectItem
                        key={value}
                        value={value}
                        className="px-4 py-2">
                        {label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </SettingsRow>
              <SettingsRow
                title="아이디 표기"
                align="center"
                rightClassName="w-44">
                <Select
                  value={nameDisplay}
                  onValueChange={(v) => setNameDisplay(v as NameDisplay)}>
                  <SelectTrigger className="text-xs w-44 bg-white/5 border-white/10 ">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {NAME_DISPLAY_MODES.map(({ value, label }) => (
                      <SelectItem
                        key={value}
                        value={value}
                        className="px-4 py-2">
                        {label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </SettingsRow>
              <SettingsRow
                title="행 높이"
                align="center"
                rightClassName="w-44">
                <div className="flex h-8 items-center gap-3 ">
                  <Slider
                    min={24}
                    max={80}
                    step={1}
                    className="cursor-pointer"
                    value={[rowHeight]}
                    onValueChange={(value) => setRowHeight(value[0])}
                  />
                  <span className="text-xs opacity-60 w-12 text-right tabular-nums">{rowHeight}px</span>
                </div>
              </SettingsRow>
            </SettingsItem>
            </>
          )}

          {tab === "theme" && (
            <>
              <SettingsItem title="투명도 조정">
                <SettingsRow
                  title="미터 목록 투명도"
                  align="center"
                  rightClassName="w-44">
                  <div className="flex h-8 items-center gap-3">
                    <Slider
                      min={0.1}
                      max={1}
                      step={0.05}
                      className="cursor-pointer"
                      value={[meterListOpacity]}
                      onValueChange={(value) => setMeterListOpacity(value[0])}
                    />
                    <span className="text-xs opacity-60 w-12 text-right tabular-nums">
                      {Math.round(meterListOpacity * 100)}%
                    </span>
                  </div>
                </SettingsRow>
                <SettingsRow
                  title="미터 배경 투명도"
                  align="center"
                  rightClassName="w-44">
                  <div className="flex h-8 items-center gap-3">
                    <Slider
                      min={0}
                      max={1}
                      step={0.05}
                      className="cursor-pointer"
                      value={[meterOpacity]}
                      onValueChange={(value) => setMeterOpacity(value[0])}
                    />
                    <span className="text-xs opacity-60 w-12 text-right tabular-nums">
                      {Math.round(meterOpacity * 100)}%
                    </span>
                  </div>
                </SettingsRow>
              </SettingsItem>
              <SettingsItem title="유저 이름 색상">
                <div className="flex flex-col gap-2.5">
                  <ColorSwatch
                    label="천족"
                    value={theme.serverAColor}
                    onChange={(v) => setThemeColor("serverAColor", v)}
                  />
                  <ColorSwatch
                    label="마족"
                    value={theme.serverBColor}
                    onChange={(v) => setThemeColor("serverBColor", v)}
                  />
                </div>
              </SettingsItem>
              <SettingsItem title="미터 바 색상">
                <div className="flex flex-col gap-2.5">
                  <GradientRow
                    label="내 캐릭터"
                    value={theme.userBar}
                    onChange={(v) => setThemeColor("userBar", v)}
                  />
                  <GradientRow
                    label="일반"
                    value={theme.normalBar}
                    onChange={(v) => setThemeColor("normalBar", v)}
                  />
                  <GradientRow
                    label="경고 (기여도 5% 미만)"
                    value={theme.warningBar}
                    onChange={(v) => setThemeColor("warningBar", v)}
                  />
                  <GradientRow
                    label="에러 (기여도 3% 미만)"
                    value={theme.errorBar}
                    onChange={(v) => setThemeColor("errorBar", v)}
                  />
                </div>
              </SettingsItem>
              <SettingsItem title="미터 텍스트 색상">
                <div className="flex flex-col gap-2.5">
                  <ColorSwatch
                    label="누적"
                    value={theme.meterStatAmount}
                    onChange={(v) => setThemeColor("meterStatAmount", v)}
                  />
                  <ColorSwatch
                    label="DPS"
                    value={theme.meterStatDps}
                    onChange={(v) => setThemeColor("meterStatDps", v)}
                  />
                  <ColorSwatch
                    label="퍼센트"
                    value={theme.meterStatPercent}
                    onChange={(v) => setThemeColor("meterStatPercent", v)}
                  />
                  <ColorSwatch
                    label="전투 시간"
                    value={theme.combatTimeColor}
                    onChange={(v) => setThemeColor("combatTimeColor", v)}
                  />
                </div>
              </SettingsItem>
              <SettingsItem title="보스 / 전투 기록">
                <div className="flex flex-col gap-2.5">
                  <GradientRow
                    label="타겟 / 전투 기록"
                    value={theme.bossBar}
                    onChange={(v) => setThemeColor("bossBar", v)}
                  />
                </div>
                <div className="flex flex-col gap-2.5">
                  <ColorSwatch
                    label="남은 체력 / 경과 시간"
                    value={theme.bossRightValue}
                    onChange={(v) => setThemeColor("bossRightValue", v)}
                  />
                </div>
              </SettingsItem>
              <SettingsItem className="pb-2">
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={resetTheme}
                  className="w-full opacity-50 hover:opacity-100 hover:bg-amber-500/10 transition-opacity flex items-center gap-2 text-xs">
                  <RotateCcw className="w-3 h-3" />
                  빛 레기온 테마로 초기화
                </Button>
              </SettingsItem>
            </>
          )}

          {tab === "controls" && (
            <>
              <SettingsItem>
                <SettingsRow
                  title="최소화 단축키 설정"
                  align="center"
                  rightClassName="w-44">
                  <SettingsControlInput
                    readOnly
                    onFocus={startHide}
                    onBlur={stopHide}
                    value={formatHotkey(pendingHide.modifiers, pendingHide.vkCode)}
                    className="cursor-pointer"
                  />
                </SettingsRow>
                <SettingsRow
                  title="패스스루"
                  description="다른 모니터로 옮긴 뒤에는 빈 투명 영역이 클릭을 먹을 수 있습니다. 패스스루(기본 Ctrl+T)로 게임을 클릭하세요.">
                  <Switch
                    checked={isClickThrough}
                    disabled
                    className="data-[state=checked]:bg-amber-500"
                  />
                </SettingsRow>
                <SettingsRow
                  title="패스스루 단축키 설정"
                  align="center"
                  rightClassName="w-44">
                  <SettingsControlInput
                    readOnly
                    onFocus={startClickThrough}
                    onBlur={stopClickThrough}
                    value={formatHotkey(pendingClickThrough.modifiers, pendingClickThrough.vkCode)}
                    className="cursor-pointer"
                  />
                </SettingsRow>
              </SettingsItem>
              <SettingsItem className="pb-2">
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={resetMeterPosition}
                  className="w-full opacity-50 hover:opacity-100 hover:bg-transition transition-opacity flex items-center gap-2 text-xs">
                  <RotateCcw className="w-3 h-3" />
                  미터기 위치 초기화
                </Button>
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={resetJoinPanelPosition}
                  className="w-full opacity-50 hover:opacity-100 hover:bg-transition transition-opacity flex items-center gap-2 text-xs">
                  <RotateCcw className="w-3 h-3" />
                  파티 신청 위치 초기화
                </Button>
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={resetSidePanelPosition}
                  className="w-full opacity-50 hover:opacity-100 hover:bg-transition transition-opacity flex items-center gap-2 text-xs">
                  <RotateCcw className="w-3 h-3" />
                  사이드 패널 위치 초기화
                </Button>
              </SettingsItem>
            </>
          )}

          {tab === "tracker" && (
            <SettingsItem>
              <SettingsRow
                title="추적 오버레이"
                description="미터기와 다른 창으로 뜹니다. 따로 드래그할 수 있고, 게임 위에 고정합니다.">
                <Switch
                  checked={trackerOverlayEnabled}
                  onCheckedChange={setTrackerOverlayEnabled}
                  className="data-[state=checked]:bg-amber-500"
                />
              </SettingsRow>
              <SettingsRow
                title="빈 오드·열쇠 칸"
                description="미터 헤더와 추적 창에 오드·열쇠를 둡니다. 캐릭터가 월드에 들어오면 숫자가 채워지고, 끄면 값이 없을 때 칸을 숨깁니다.">
                <Switch
                  checked={showEmptyResourceChips}
                  onCheckedChange={setShowEmptyResourceChips}
                  className="data-[state=checked]:bg-amber-500"
                />
              </SettingsRow>
              {trackerOverlayEnabled && <TrackerSkillPicker />}
            </SettingsItem>
          )}

          {tab === "guild" && (
            <>
              <GuildPairSettings />
              <SettingsItem>
                <SettingsRow
                  title="길드·보스 알람"
                  description="연동되면 아그로·우선 필드보스 임박을 미터 위에 띄웁니다.">
                  <Switch
                    checked={guildAlertsEnabled}
                    onCheckedChange={setGuildAlertsEnabled}
                    className="data-[state=checked]:bg-amber-500"
                  />
                </SettingsRow>
              </SettingsItem>
            </>
          )}
        </div>

        <SettingsMeterPreview />
      </div>
      <div className="flex w-full min-w-0 shrink-0 justify-end gap-2 border-t border-white/10 px-4 py-3">
        <Button
          onClick={handleCancel}
          size="lg"
          className="p-4 w-20 opacity-60 hover:opacity-100 transition-opacity">
          취소
        </Button>
        <Button
          onClick={handleSave}
          className="bg-amber-500 hover:bg-amber-400 text-[#0b0d17] transition-colors p-4 w-20">
          저장
        </Button>
      </div>
    </div>
  );
};
