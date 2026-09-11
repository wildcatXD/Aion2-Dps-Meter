import { SkillIcon } from "@/components/SkillIcon";
import { GROUPED_BY_JOB, getSkillName } from "@/constants/codes";
import { useSettingsStore } from "@/stores/useSettingsStore";
import { getJobIconSrc } from "@/utils/icons";
import { cn } from "@/lib/utils";

const MAX_TRACKED = 8;

export function TrackerSkillPicker() {
  const codes = useSettingsStore((s) => s.trackedBuffCodes);
  const setCodes = useSettingsStore((s) => s.setTrackedBuffCodes);

  const toggle = (code: number) => {
    if (codes.includes(code)) {
      setCodes(codes.filter((c) => c !== code));
      return;
    }
    if (codes.length >= MAX_TRACKED) return;
    setCodes([...codes, code]);
  };

  return (
    <div className="space-y-3 pt-1">
      <p className="text-[11px] opacity-50">
        고른 스킬만 추적합니다. 최대 {MAX_TRACKED}개. 미터기와 다른 창에 뜨며, 지금은 본인 버프의 남은 지속시간입니다.
      </p>
      {GROUPED_BY_JOB.map(({ job, normalSkills, stigmaSkills }) => {
        const skills = [...normalSkills, ...stigmaSkills];
        if (skills.length === 0) return null;
        return (
          <div key={job}>
            <div className="mb-1.5 flex items-center gap-1.5 text-[11px] font-bold">
              <img src={getJobIconSrc(job)} className="h-4 w-4 object-contain" alt="" />
              {job}
            </div>
            <div className="flex flex-wrap gap-1">
              {skills.map((code) => {
                const on = codes.includes(code);
                return (
                  <button
                    key={code}
                    type="button"
                    onClick={() => toggle(code)}
                    className={cn(
                      "flex items-center gap-1 rounded-md px-1.5 py-1 text-[10px]",
                      on ? "bg-amber-500/20 text-amber-100" : "bg-white/5 opacity-40",
                    )}>
                    <SkillIcon code={code} size={12} />
                    {getSkillName(code) ?? code}
                  </button>
                );
              })}
            </div>
          </div>
        );
      })}
    </div>
  );
}
