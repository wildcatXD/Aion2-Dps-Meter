import { useEffect, useRef } from "react";
import { TrackerHud } from "@/components/TrackerOverlay";
import { useSettingsStore } from "@/stores/useSettingsStore";

/**
 * 미터기와 분리된 버프 추적 창. 네이티브 Stage가 화면 좌표를 갖고,
 * 여기선 크기만 맞추고 드래그로 Stage를 옮깁니다.
 */
export function TrackerApp() {
  const isLoaded = useSettingsStore((s) => s.isLoaded);
  const isClickThrough = useSettingsStore((s) => s.isClickThrough);
  const trackerX = useSettingsStore((s) => s.trackerX);
  const trackerY = useSettingsStore((s) => s.trackerY);
  const setTrackerPosition = useSettingsStore((s) => s.setTrackerPosition);
  const rootRef = useRef<HTMLDivElement>(null);
  const posRef = useRef({ x: trackerX, y: trackerY });

  useEffect(() => {
    posRef.current = { x: trackerX, y: trackerY };
  }, [trackerX, trackerY]);

  useEffect(() => {
    (window as any).onClickThroughChanged = (v: boolean) => {
      useSettingsStore.setState({ isClickThrough: v });
    };
  }, []);

  useEffect(() => {
    const el = rootRef.current;
    if (!el) return;
    const fit = () => {
      const r = el.getBoundingClientRect();
      window.javaBridge?.fitTrackerWindow?.(Math.ceil(r.width + 2), Math.ceil(r.height + 2));
    };
    fit();
    const ro = new ResizeObserver(fit);
    ro.observe(el);
    return () => ro.disconnect();
  }, [isLoaded]);

  useEffect(() => {
    const el = rootRef.current;
    if (!el || isClickThrough) return;
    let dragging = false;
    let startScreenX = 0;
    let startScreenY = 0;
    let startX = 0;
    let startY = 0;

    const onDown = (e: MouseEvent) => {
      if (e.button !== 0) return;
      dragging = true;
      startScreenX = e.screenX;
      startScreenY = e.screenY;
      startX = posRef.current.x;
      startY = posRef.current.y;
      e.preventDefault();
    };
    const onMove = (e: MouseEvent) => {
      if (!dragging) return;
      const nextX = startX + (e.screenX - startScreenX);
      const nextY = startY + (e.screenY - startScreenY);
      posRef.current = { x: nextX, y: nextY };
      window.javaBridge?.moveWindow?.(nextX, nextY);
    };
    const onUp = () => {
      if (!dragging) return;
      dragging = false;
      setTrackerPosition(posRef.current.x, posRef.current.y);
    };

    el.addEventListener("mousedown", onDown);
    window.addEventListener("mousemove", onMove);
    window.addEventListener("mouseup", onUp);
    return () => {
      el.removeEventListener("mousedown", onDown);
      window.removeEventListener("mousemove", onMove);
      window.removeEventListener("mouseup", onUp);
    };
  }, [isClickThrough, setTrackerPosition]);

  return (
    <div
      ref={rootRef}
      className="inline-flex cursor-move select-none p-1.5"
      style={{ visibility: isLoaded ? "visible" : "hidden" }}>
      <TrackerHud />
    </div>
  );
}
