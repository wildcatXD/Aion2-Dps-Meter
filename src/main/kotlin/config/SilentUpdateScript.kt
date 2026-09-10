package com.tbread.config

import java.io.File

/**
 * 미터기가 종료된 뒤에 msiexec를 돌리는 cmd 스크립트.
 * 경로는 인자로 넘기지 않고 파일에 그대로 넣습니다. `start`가 Program Files
 * 공백을 쪼개면 무인 설치가 실패하고, 예전 코드는 그때 설치 마법사를 띄웠습니다.
 *
 * 설치가 끝나면 같은 실행 파일(또는 기본 설치 폴더)을 다시 켭니다. 업데이트
 * 느낌이 다운로드 → 잠깐 종료 → 바로 다시 켜짐으로 이어지게 하기 위함입니다.
 */
object SilentUpdateScript {
    const val LAUNCHER_EXE = "bit-dps-meter.exe"
    const val INSTALL_FOLDER = "bit-dps-meter"

    fun render(waitPid: Long, msiPath: String, exePath: String, logPath: String): String {
        val launcher = resolveLauncherPath(exePath).ifBlank { exePath }
        return """
            @echo off
            setlocal EnableExtensions
            set "WAIT_PID=${waitPid}"
            set "MSI=${escapeForBatch(msiPath)}"
            set "EXE=${escapeForBatch(launcher)}"
            set "LOG=${escapeForBatch(logPath)}"
            set "PF86=%ProgramFiles(x86)%"

            :wait
            timeout /t 1 /nobreak >nul
            tasklist /FI "PID eq %WAIT_PID%" 2>nul | findstr /I /C:" %WAIT_PID% " >nul
            if not errorlevel 1 goto wait

            timeout /t 3 /nobreak >nul

            >>"%LOG%" echo [apply-update] starting silent msiexec
            start /wait msiexec /i "%MSI%" /qn /norestart REBOOT=ReallySuppress /l*v "%LOG%"
            set "EC=%ERRORLEVEL%"
            >>"%LOG%" echo [apply-update] msiexec exit %EC%
            if "%EC%"=="0" goto relaunch
            if "%EC%"=="3010" goto relaunch
            exit /b %EC%

            :relaunch
            set "TRIES=0"
            :find_exe
            set /a TRIES+=1
            if exist "%EXE%" goto do_launch
            if exist "%ProgramFiles%\$INSTALL_FOLDER\$LAUNCHER_EXE" (
              set "EXE=%ProgramFiles%\$INSTALL_FOLDER\$LAUNCHER_EXE"
              goto do_launch
            )
            if exist "%PF86%\$INSTALL_FOLDER\$LAUNCHER_EXE" (
              set "EXE=%PF86%\$INSTALL_FOLDER\$LAUNCHER_EXE"
              goto do_launch
            )
            if exist "%LocalAppData%\$INSTALL_FOLDER\$LAUNCHER_EXE" (
              set "EXE=%LocalAppData%\$INSTALL_FOLDER\$LAUNCHER_EXE"
              goto do_launch
            )
            if %TRIES% LSS 20 (
              timeout /t 1 /nobreak >nul
              goto find_exe
            )
            >>"%LOG%" echo [apply-update] launcher not found after install
            exit /b 0

            :do_launch
            >>"%LOG%" echo [apply-update] launching "%EXE%"
            for %%I in ("%EXE%") do (
              pushd "%%~dpI"
              start "" /NORMAL "%%~nxI"
              popd
            )
            exit /b 0
        """.trimIndent() + "\n"
    }

    /**
     * jpackage가 javaw.exe를 프로세스 명령으로 남기면 그 경로를 다시 켜도
     * 미터기가 안 뜹니다. 설치 폴더의 런처 exe를 찾아 씁니다.
     */
    fun resolveLauncherPath(commandPath: String?): String {
        if (commandPath.isNullOrBlank()) return ""
        val slashName = commandPath.substringAfterLast('/').substringAfterLast('\\')
        if (slashName.equals(LAUNCHER_EXE, ignoreCase = true)) return commandPath
        val file = File(commandPath)
        var dir = file.parentFile
        while (dir != null) {
            val candidate = File(dir, LAUNCHER_EXE)
            if (candidate.isFile) return candidate.absolutePath
            dir = dir.parentFile
        }
        return commandPath
    }

    internal fun escapeForBatch(path: String): String {
        return path.replace("%", "%%")
    }
}
