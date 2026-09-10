package com.tbread.config

/**
 * 미터기가 종료된 뒤에 msiexec를 돌리는 cmd 스크립트.
 * 경로는 인자로 넘기지 않고 파일에 그대로 넣습니다. `start`가 Program Files
 * 공백을 쪼개면 무인 설치가 실패하고, 예전 코드는 그때 설치 마법사를 띄웠습니다.
 */
object SilentUpdateScript {
    fun render(waitPid: Long, msiPath: String, exePath: String, logPath: String): String {
        return """
            @echo off
            setlocal EnableExtensions
            set "WAIT_PID=${waitPid}"
            set "MSI=${escapeForBatch(msiPath)}"
            set "EXE=${escapeForBatch(exePath)}"
            set "LOG=${escapeForBatch(logPath)}"

            :wait
            timeout /t 1 /nobreak >nul
            tasklist /FI "PID eq %WAIT_PID%" 2>nul | findstr /I /C:" %WAIT_PID% " >nul
            if not errorlevel 1 goto wait

            timeout /t 3 /nobreak >nul

            start /wait msiexec /i "%MSI%" /qn /norestart REBOOT=ReallySuppress /l*v "%LOG%"
            set "EC=%ERRORLEVEL%"
            if "%EC%"=="0" goto relaunch
            if "%EC%"=="3010" goto relaunch
            exit /b %EC%

            :relaunch
            if exist "%EXE%" start "" "%EXE%"
            exit /b 0
        """.trimIndent() + "\n"
    }

    internal fun escapeForBatch(path: String): String {
        return path.replace("%", "%%")
    }
}
