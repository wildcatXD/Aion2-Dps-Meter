package com.tbread.config

import java.io.File

/**
 * 미터기가 종료된 뒤에 msiexec를 돌리는 숨김 VBS 헬퍼.
 *
 * 1.9.1~1.9.3 은 cmd.exe 를 띄웠습니다. `start /min` 이어도 검은 콘솔이 남고,
 * UTF-8 배치 + 한글 %TEMP% 경로는 msiexec 가 파일을 못 찾아 설치가 실패했습니다.
 * `timeout /t` 는 숨긴 콘솔에서 멈추기도 합니다.
 *
 * 1.9.4 헬퍼는 콘솔 없는 wscript 로 바꿨지만, 설치가 실패해도(권한 부족·파일 잠금)
 * 예전 exe 를 그대로 다시 켰습니다. Program Files 설치본은 `/qn` 만으로는 1603 이
 * 나고, 그때마다 업데이트 알림이 반복됩니다. 성공한 뒤에는 예전 경로보다
 * 표준 설치 경로를 먼저 켭니다.
 *
 * 1.9.8 은 msiexec 가 예전 파일을 지운 뒤 새 파일을 다 못 쓴 경우(exe 만 있고
 * `app\bit-dps-meter.cfg` 가 없는 상태)를 설치 완료로 보지 않습니다. 그 실행 파일을
 * 다시 켜면 jpackage 런처가 "Error opening …cfg" 대화상자를 띄우고 끝납니다.
 * 설치 전에 폴더를 백업하고, 업그레이드가 불완전하면 xcopy 로 되돌린 다음
 * 깨진 exe 는 켜지 않습니다.
 *
 * 이 헬퍼는 콘솔이 없는 wscript + WMI 대기로 돌리고, 스크립트 파일은
 * UTF-16 LE BOM 으로 써서 한글 경로를 유지합니다.
 */
object SilentUpdateScript {
    const val LAUNCHER_EXE = "bit-dps-meter.exe"
    const val LAUNCHER_CFG = "bit-dps-meter.cfg"
    const val INSTALL_FOLDER = "bit-dps-meter"
    const val BACKUP_FOLDER = "bit-dps-meter-prev"

    fun render(waitPid: Long, msiPath: String, exePath: String, logPath: String): String {
        val launcher = resolveLauncherPath(exePath).ifBlank { exePath }
        val msiLog = "$logPath.msi.txt"
        val body = """
            Option Explicit
            Dim sh, fso, pid, msi, exe, logPath, msiLog, elevLog, errFile, tries, ec, launch, backup
            Set sh = CreateObject("Wscript.Shell")
            Set fso = CreateObject("Scripting.FileSystemObject")
            pid = $waitPid
            msi = "${escapeForVbs(msiPath)}"
            exe = "${escapeForVbs(launcher)}"
            logPath = "${escapeForVbs(logPath)}"
            msiLog = "${escapeForVbs(msiLog)}"
            elevLog = msiLog & ".elev.txt"
            errFile = sh.ExpandEnvironmentStrings("%APPDATA%\Aion2DpsMeter\update-last-error.txt")
            backup = sh.ExpandEnvironmentStrings("%TEMP%\${BACKUP_FOLDER}")

            Sub LogLine(msg)
              On Error Resume Next
              Dim ts
              Set ts = fso.OpenTextFile(logPath, 8, True, -1)
              ts.WriteLine msg
              ts.Close
            End Sub

            Function ProcessExists(processId)
              On Error Resume Next
              Dim wmi, col
              ProcessExists = False
              Set wmi = GetObject("winmgmts:\\.\root\cimv2")
              Set col = wmi.ExecQuery("SELECT * FROM Win32_Process WHERE ProcessId=" & processId)
              If Err.Number = 0 Then ProcessExists = (col.Count > 0)
            End Function

            Sub KillMeter()
              On Error Resume Next
              Dim wmi, col, p, path
              Set wmi = GetObject("winmgmts:\\.\root\cimv2")
              Set col = wmi.ExecQuery("SELECT ProcessId, Name, ExecutablePath FROM Win32_Process")
              For Each p In col
                If LCase(p.Name) = "bit-dps-meter.exe" Then
                  p.Terminate
                ElseIf LCase(p.Name) = "javaw.exe" Or LCase(p.Name) = "java.exe" Then
                  path = LCase("" & p.ExecutablePath)
                  If InStr(path, "\bit-dps-meter\") > 0 Then p.Terminate
                End If
              Next
            End Sub

            Function FileExists(path)
              On Error Resume Next
              FileExists = False
              If path <> "" Then FileExists = fso.FileExists(path)
            End Function

            Function LauncherCfgPath(exePath)
              LauncherCfgPath = ""
              If exePath <> "" Then LauncherCfgPath = fso.GetParentFolderName(exePath) & "\app\${LAUNCHER_CFG}"
            End Function

            Function InstallLooksComplete(exePath)
              On Error Resume Next
              InstallLooksComplete = False
              If exePath = "" Then Exit Function
              If FileExists(exePath) And FileExists(LauncherCfgPath(exePath)) Then InstallLooksComplete = True
            End Function

            Function InstallOk(code)
              InstallOk = (code = 0 Or code = 3010)
            End Function

            Function NeedsElevation(code)
              NeedsElevation = (code = 1603 Or code = 1625 Or code = 1923)
            End Function

            Function FindInstalledExe()
              Dim c(3), i
              FindInstalledExe = ""
              c(0) = sh.ExpandEnvironmentStrings("%ProgramFiles%\bit-dps-meter\bit-dps-meter.exe")
              c(1) = sh.ExpandEnvironmentStrings("%ProgramFiles(x86)%\bit-dps-meter\bit-dps-meter.exe")
              c(2) = sh.ExpandEnvironmentStrings("%LocalAppData%\Programs\bit-dps-meter\bit-dps-meter.exe")
              c(3) = sh.ExpandEnvironmentStrings("%LocalAppData%\bit-dps-meter\bit-dps-meter.exe")
              For i = 0 To 3
                If InstallLooksComplete(c(i)) Then
                  FindInstalledExe = c(i)
                  Exit Function
                End If
              Next
            End Function

            Function PickCompleteLaunch()
              PickCompleteLaunch = FindInstalledExe()
              If PickCompleteLaunch = "" And InstallLooksComplete(exe) Then PickCompleteLaunch = exe
            End Function

            Function MsiexecStillRunning()
              On Error Resume Next
              Dim wmi, col, p, cmd
              MsiexecStillRunning = False
              Set wmi = GetObject("winmgmts:\\.\root\cimv2")
              Set col = wmi.ExecQuery("SELECT CommandLine FROM Win32_Process WHERE Name=" & Chr(34) & "msiexec.exe" & Chr(34))
              For Each p In col
                cmd = LCase("" & p.CommandLine)
                If InStr(cmd, LCase(msi)) > 0 Then
                  MsiexecStillRunning = True
                  Exit Function
                End If
              Next
            End Function

            Function XcopyStillRunning()
              On Error Resume Next
              Dim wmi, col, p, cmd
              XcopyStillRunning = False
              Set wmi = GetObject("winmgmts:\\.\root\cimv2")
              Set col = wmi.ExecQuery("SELECT CommandLine FROM Win32_Process WHERE Name=" & Chr(34) & "xcopy.exe" & Chr(34))
              For Each p In col
                cmd = LCase("" & p.CommandLine)
                If InStr(cmd, LCase(backup)) > 0 Then
                  XcopyStillRunning = True
                  Exit Function
                End If
              Next
            End Function

            Function ParseMsiexecLogFormat(path, fmt)
              On Error Resume Next
              Dim ts, line, last
              ParseMsiexecLogFormat = -1
              last = ""
              If Not FileExists(path) Then Exit Function
              Err.Clear
              Set ts = fso.OpenTextFile(path, 1, False, fmt)
              If Err.Number <> 0 Then
                Err.Clear
                Exit Function
              End If
              Do Until ts.AtEndOfStream
                line = ts.ReadLine
                If InStr(line, "Installation success or error status:") > 0 Then last = line
              Loop
              ts.Close
              If last <> "" Then ParseMsiexecLogFormat = CLng(Trim(Mid(last, InStrRev(last, ":") + 1)))
            End Function

            Function ParseMsiexecLog(path)
              Dim status
              ParseMsiexecLog = -1
              status = ParseMsiexecLogFormat(path, -1)
              If status >= 0 Then
                ParseMsiexecLog = status
                Exit Function
              End If
              ParseMsiexecLog = ParseMsiexecLogFormat(path, 0)
            End Function

            Sub RunElevatedMsiexec()
              On Error Resume Next
              Dim app, waited, parsed
              Set app = CreateObject("Shell.Application")
              LogLine "[apply-update] msiexec elevated runas"
              app.ShellExecute "msiexec.exe", "/i " & Chr(34) & msi & Chr(34) & " /qn /norestart REBOOT=ReallySuppress /l*v " & Chr(34) & elevLog & Chr(34), "", "runas", 0
              WScript.Sleep 2000
              waited = 0
              Do While MsiexecStillRunning() And waited < 180
                WScript.Sleep 1000
                waited = waited + 1
              Loop
              parsed = ParseMsiexecLog(elevLog)
              If parsed >= 0 Then
                ec = parsed
              End If
              LogLine "[apply-update] msiexec elevated exit " & ec
            End Sub

            Sub BackupCurrentInstall()
              On Error Resume Next
              Dim src, candidate
              candidate = FindInstalledExe()
              If candidate = "" Then candidate = exe
              src = fso.GetParentFolderName(candidate)
              If src = "" Or Not fso.FolderExists(src) Then
                LogLine "[apply-update] no install folder to backup"
                Exit Sub
              End If
              If fso.FolderExists(backup) Then fso.DeleteFolder backup, True
              fso.CopyFolder src, backup, True
              If fso.FolderExists(backup) Then
                LogLine "[apply-update] backed up " & src
              Else
                LogLine "[apply-update] backup failed for " & src
              End If
            End Sub

            Function RestoreDest()
              RestoreDest = fso.GetParentFolderName(exe)
              If RestoreDest = "" Then RestoreDest = sh.ExpandEnvironmentStrings("%ProgramFiles%\bit-dps-meter")
            End Function

            Sub RestoreBackup()
              On Error Resume Next
              Dim dest, app, waited, restored
              dest = RestoreDest()
              If Not fso.FolderExists(backup) Then
                LogLine "[apply-update] no backup to restore"
                Exit Sub
              End If
              If dest = "" Then
                LogLine "[apply-update] no restore destination"
                Exit Sub
              End If
              LogLine "[apply-update] restoring backup to " & dest
              sh.Run "xcopy.exe " & Chr(34) & backup & Chr(34) & " " & Chr(34) & dest & Chr(34) & " /E /I /Y /H /R", 0, True
              restored = dest & "\bit-dps-meter.exe"
              If InstallLooksComplete(restored) Then
                LogLine "[apply-update] restore copy ok"
                Exit Sub
              End If
              LogLine "[apply-update] restore needs elevation"
              Set app = CreateObject("Shell.Application")
              app.ShellExecute "xcopy.exe", Chr(34) & backup & Chr(34) & " " & Chr(34) & dest & Chr(34) & " /E /I /Y /H /R", "", "runas", 0
              WScript.Sleep 2000
              waited = 0
              Do While XcopyStillRunning() And waited < 120
                WScript.Sleep 1000
                waited = waited + 1
              Loop
              LogLine "[apply-update] restore xcopy finished"
            End Sub

            Sub ClearUpdateError()
              On Error Resume Next
              If FileExists(errFile) Then fso.DeleteFile errFile, True
            End Sub

            Sub WriteUpdateError(msg)
              On Error Resume Next
              Dim ts, dir
              dir = fso.GetParentFolderName(errFile)
              If dir <> "" And Not fso.FolderExists(dir) Then fso.CreateFolder dir
              Set ts = fso.OpenTextFile(errFile, 2, True, -1)
              ts.WriteLine msg
              ts.Close
            End Sub

            Do While ProcessExists(pid)
              WScript.Sleep 1000
            Loop
            WScript.Sleep 3000
            KillMeter
            WScript.Sleep 2000
            BackupCurrentInstall

            tries = 0
            ec = 1
            Do
              tries = tries + 1
              LogLine "[apply-update] msiexec try " & tries
              ec = sh.Run("msiexec.exe /i " & Chr(34) & msi & Chr(34) & " /qn /norestart REBOOT=ReallySuppress /l*v " & Chr(34) & msiLog & Chr(34), 0, True)
              LogLine "[apply-update] msiexec exit " & ec
              If InstallOk(ec) Then Exit Do
              If tries >= 3 Then Exit Do
              WScript.Sleep 3000
            Loop

            If Not InstallOk(ec) And NeedsElevation(ec) Then
              RunElevatedMsiexec
            End If

            If InstallOk(ec) Then
              LogLine "[apply-update] silent install succeeded"
            Else
              LogLine "[apply-update] silent install failed"
              WriteUpdateError "msiexec exit " & ec
            End If

            launch = PickCompleteLaunch()
            If launch = "" Then
              LogLine "[apply-update] install incomplete, retry elevated"
              RunElevatedMsiexec
              launch = PickCompleteLaunch()
            End If

            If launch = "" Then
              RestoreBackup
              launch = PickCompleteLaunch()
            End If

            If InstallLooksComplete(launch) Then
              LogLine "[apply-update] launching " & launch
              ClearUpdateError
              sh.Run Chr(34) & launch & Chr(34), 1, False
            Else
              LogLine "[apply-update] not launching incomplete install"
              WriteUpdateError "incomplete install; missing app\${LAUNCHER_CFG}"
            End If
        """.trimIndent()
        return toCrLf(body) + "\r\n"
    }

    fun writeUtf16LeBom(file: File, text: String) {
        file.writeBytes(utf16LeBomBytes(text))
    }

    fun utf16LeBomBytes(text: String): ByteArray {
        val bom = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
        return bom + text.toByteArray(Charsets.UTF_16LE)
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

    internal fun escapeForVbs(value: String): String {
        return value.replace("\"", "\"\"")
    }

    internal fun toCrLf(text: String): String {
        return text.replace("\r\n", "\n").replace("\n", "\r\n")
    }
}
