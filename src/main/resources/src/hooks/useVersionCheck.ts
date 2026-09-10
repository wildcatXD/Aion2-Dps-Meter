import { useEffect, useRef, useState, useCallback } from "react";
import type { Version, UpdateInfo, DownloadState, CheckStatus } from "@/types";

const API = "https://api.github.com/repos/wildcatXD/Aion2-Dps-Meter/releases?per_page=10";
const RELEASE_URL = "https://github.com/wildcatXD/Aion2-Dps-Meter/releases";

const RETRY_INTERVAL = 800;
const RETRY_LIMIT = 5;

// 미터기를 켜놓은 동안 주기적으로 새 버전이 있는지 다시 확인합니다.
// GitHub API 익명 요청은 IP당 시간에 60회까지 허용되는데, 30분마다 1회면
// 인스턴스 하나당 시간당 2회 수준이라 서버(GitHub)에 부담이 거의 없습니다.
const AUTO_RECHECK_INTERVAL = 30 * 60 * 1000;

const parseVersion = (value: string): Version | null => {
  const raw = String(value || "")
    .trim()
    .replace(/^v/i, "");
  const match = raw.match(/^(\d+)\.(\d+)\.(\d+)(?:-(.+))?$/);
  if (!match) return null;
  return {
    major: Number(match[1]),
    minor: Number(match[2]),
    patch: Number(match[3]),
    pre: match[4] || null,
    raw,
  };
};

const compareVersion = (a: Version, b: Version): number => {
  if (a.major !== b.major) return a.major > b.major ? 1 : -1;
  if (a.minor !== b.minor) return a.minor > b.minor ? 1 : -1;
  if (a.patch !== b.patch) return a.patch > b.patch ? 1 : -1;
  if (!a.pre && !b.pre) return 0;
  if (!a.pre) return 1;
  if (!b.pre) return -1;
  return a.pre.localeCompare(b.pre);
};

const pickLatest = (
  releases: any[],
  wantPrerelease: boolean,
): { version: Version; msiUrl: string } | null => {
  return releases
    .filter((r) => !r.draft && !!r.prerelease === wantPrerelease)
    .reduce<{ version: Version; msiUrl: string } | null>((best, r) => {
      const v = parseVersion(r.tag_name);
      if (!v) return best;
      const msiAsset = r.assets?.find((a: any) => a.name?.endsWith(".msi"));
      if (!msiAsset) return best;
      if (!best || compareVersion(v, best.version) > 0)
        return { version: v, msiUrl: msiAsset.browser_download_url };
      return best;
    }, null);
};

export const useVersionCheck = () => {
  const [updateInfo, setUpdateInfo] = useState<UpdateInfo | null>(null);
  const [downloadState, setDownloadState] = useState<DownloadState>({ status: "idle" });
  const [checkStatus, setCheckStatus] = useState<CheckStatus>("idle");
  const retryCountRef = useRef(0);
  const [currentVersion, setCurrentVersion] = useState<string | null>(null);
  const cancelledRef = useRef(false);

  const checkUpdate = useCallback(async () => {
    cancelledRef.current = true;
    await new Promise((r) => setTimeout(r, 0));
    cancelledRef.current = false;
    retryCountRef.current = 0;
    setCheckStatus("checking");

    const check = async () => {
      if (cancelledRef.current) return;

      const version = (window as any).javaBridge?.getVersion?.();
      const current = parseVersion(version ?? "");

      if (!current || !(window as any).javaBridge) {
        if (retryCountRef.current < RETRY_LIMIT) {
          retryCountRef.current++;
          setTimeout(check, RETRY_INTERVAL);
        } else {
          setCheckStatus("error");
        }
        return;
      }

      setCurrentVersion(current.raw);

      try {
        const res = await fetch(API, {
          headers: { Accept: "application/vnd.github+json" },
          cache: "no-store",
        });

        if (cancelledRef.current) return;
        if (!res.ok) {
          setCheckStatus("error");
          return;
        }

        const releases = await res.json();
        const latestStable = pickLatest(releases, false);
        const latestBeta = pickLatest(releases, true);

        let target: { version: Version; msiUrl: string } | null = null;
        let isPrerelease = false;

        if (latestStable && compareVersion(latestStable.version, current) > 0) {
          target = latestStable;
          isPrerelease = false;
        } else if (current.pre && latestBeta && compareVersion(latestBeta.version, current) > 0) {
          target = latestBeta;
          isPrerelease = true;
        }

        if (cancelledRef.current) return;

        if (target) {
          setUpdateInfo((prev) => {
            if (prev?.latestVersion === target.version.raw) return prev;
            return {
              currentVersion: current.raw,
              latestVersion: target.version.raw,
              isPrerelease,
              msiUrl: target.msiUrl,
            };
          });
          setCheckStatus("updateAvailable");
        } else {
          setUpdateInfo(null);
          setCheckStatus("upToDate");
        }
      } catch (e) {
        if (!cancelledRef.current) setCheckStatus("error");
      }
    };

    check();
  }, []);

  useEffect(() => {
    checkUpdate();
    return () => {
      cancelledRef.current = true;
    };
  }, []);

  // 다운로드/설치 중에는 재확인을 건너뛰기 위해 최신 downloadState를
  // ref로도 들고 있습니다 (interval 콜백이 클로저에 갇힌 값을 안 쓰도록).
  const downloadStateRef = useRef<DownloadState>(downloadState);
  useEffect(() => {
    downloadStateRef.current = downloadState;
  }, [downloadState]);

  useEffect(() => {
    const interval = setInterval(() => {
      const status = downloadStateRef.current.status;
      if (status === "downloading" || status === "installing" || status === "complete") return;
      checkUpdate();
    }, AUTO_RECHECK_INTERVAL);
    return () => clearInterval(interval);
  }, [checkUpdate]);

  useEffect(() => {
    (window as any).onDownloadProgress = (percent: number) => {
      setDownloadState({ status: "downloading", percent });
    };
    (window as any).onDownloadComplete = () => {
      setDownloadState({ status: "complete" });
    };
    (window as any).onInstallStarting = () => {
      setDownloadState({ status: "installing" });
    };
    (window as any).onDownloadError = () => {
      setDownloadState({ status: "error" });
    };
    return () => {
      delete (window as any).onDownloadProgress;
      delete (window as any).onDownloadComplete;
      delete (window as any).onInstallStarting;
      delete (window as any).onDownloadError;
    };
  }, []);

  const startUpdate = () => {
    if (!updateInfo) return;
    setDownloadState({ status: "downloading", percent: 0 });
    (window as any).javaBridge.startUpdate(updateInfo.msiUrl);
  };

  const retryDownload = () => {
    startUpdate();
  };

  const openReleasePage = () => {
    (window as any).javaBridge.openBrowser(RELEASE_URL);
    (window as any).javaBridge.exitApp();
  };

  return {
    updateInfo,
    currentVersion,
    downloadState,
    checkStatus,
    startUpdate,
    retryDownload,
    openReleasePage,
    checkUpdate,
  };
};
