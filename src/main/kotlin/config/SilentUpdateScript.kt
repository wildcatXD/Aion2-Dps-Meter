package com.tbread.config

import java.io.File

/**
 * 미터기가 종료된 뒤에 msiexec를 돌리는 숨김 VBS 헬퍼.
 *
 * 1.9.1~1.9.3 은 cmd.exe 를 띄웠습니다. `start /min` 이어도 검은 콘솔이 남고,
 * UTF-8 배치 + 한글 %TEMP% 경로는 msiexec 가 파일을 못 찾아 설치가 실패했습니다.
 * `timeout /t` 는 숨긴 콘솔에서 멈추기도 합니다.
 *
 * 이 헬퍼는 콘솔이 없는 wscript + WMI 대기로 돌리고, 스크립트 파일은
 * UTF-16 LE BOM 으로 써서 한글 경로를 유지합니다. 설치가 실패해도 이전
 * exe 를 다시 켭니다.
 */
object SilentUpdateScript {
    const val LAUNCHER_EXE = "bit-dps-meter.exe"
    const val INSTALL_FOLDER = "bit-dps-meter"

    fun render(waitPid: Long, msiPath: String, exePath: String, logPath: String): String {
        val launcher = resolveLauncherPath(exePath).ifBlank { exePath }
        val msiLog = "$logPath.msi.txt"
        val body = """
            Option Explicit
            Dim sh, fso, pid, msi, exe, logPath, msiLog, tries, ec, launch
            Set sh = CreateObject("Wscript.Shell")
            Set fso = CreateObject("Scripting.FileSystemObject")
            pid = $waitPid
            msi = "${escapeForVbs(msiPath)}"
            exe = "${escapeForVbs(launcher)}"
            logPath = "${escapeForVbs(logPath)}"
            msiLog = "${escapeForVbs(msiLog)}"

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
              Dim wmi, col, p
              Set wmi = GetObject("winmgmts:\\.\root\cimv2")
              Set col = wmi.ExecQuery("SELECT * FROM Win32_Process WHERE Name=" & Chr(34) & "bit-dps-meter.exe" & Chr(34))
              For Each p In col
                p.Terminate
              Next
            End Sub

            Function FileExists(path)
              On Error Resume Next
              FileExists = False
              If path <> "" Then FileExists = fso.FileExists(path)
            End Function

            Do While ProcessExists(pid)
              WScript.Sleep 1000
            Loop
            WScript.Sleep 3000
            KillMeter
            WScript.Sleep 2000

            tries = 0
            ec = 1
            Do
              tries = tries + 1
              LogLine "[apply-update] msiexec try " & tries
              ec = sh.Run("msiexec.exe /i " & Chr(34) & msi & Chr(34) & " /qn /norestart REBOOT=ReallySuppress /l*v " & Chr(34) & msiLog & Chr(34), 0, True)
              LogLine "[apply-update] msiexec exit " & ec
              If ec = 0 Or ec = 3010 Then Exit Do
              If tries >= 3 Then Exit Do
              WScript.Sleep 3000
            Loop

            If ec <> 0 And ec <> 3010 Then
              LogLine "[apply-update] silent install failed"
            End If

            launch = exe
            If Not FileExists(launch) Then launch = sh.ExpandEnvironmentStrings("%ProgramFiles%\bit-dps-meter\bit-dps-meter.exe")
            If Not FileExists(launch) Then launch = sh.ExpandEnvironmentStrings("%ProgramFiles(x86)%\bit-dps-meter\bit-dps-meter.exe")
            If Not FileExists(launch) Then launch = sh.ExpandEnvironmentStrings("%LocalAppData%\bit-dps-meter\bit-dps-meter.exe")

            If FileExists(launch) Then
              LogLine "[apply-update] launching " & launch
              sh.Run Chr(34) & launch & Chr(34), 1, False
            Else
              LogLine "[apply-update] launcher not found after install"
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
