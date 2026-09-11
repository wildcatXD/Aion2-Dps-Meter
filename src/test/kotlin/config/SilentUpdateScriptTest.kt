package com.tbread.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SilentUpdateScriptTest {
    @Test
    fun embedsQuotedPathsAndWaitsForMsiexec() {
        val script = SilentUpdateScript.render(
            waitPid = 4242,
            msiPath = """C:\Users\빛레기온\bit-dps-meter-update.msi""",
            exePath = """C:\Program Files\bit-dps-meter\bit-dps-meter.exe""",
            logPath = """C:\Users\빛레기온\install.log""",
        )
        assertTrue(script.contains("""msi = "C:\Users\빛레기온\bit-dps-meter-update.msi""""))
        assertTrue(script.contains("""exe = "C:\Program Files\bit-dps-meter\bit-dps-meter.exe""""))
        assertTrue(script.contains("pid = 4242"))
        assertTrue(script.contains("msiexec.exe /i"))
        assertTrue(script.contains("/qn"))
        assertTrue(script.contains("WScript.Sleep"))
        assertTrue(script.contains("Win32_Process"))
        assertFalse(script.contains("cmd.exe"))
        assertFalse(script.contains("start /wait msiexec"))
        assertFalse(script.contains("timeout /t"))
        assertTrue(script.contains("\r\n"))
    }

    @Test
    fun neverFallsBackToSetupWizard() {
        val script = SilentUpdateScript.render(1, "a.msi", "a.exe", "a.log")
        val msiexecLines = script.lines().map { it.trim() }.filter {
            it.contains("msiexec") && !it.contains("LogLine")
        }
        assertTrue(msiexecLines.isNotEmpty())
        assertTrue(msiexecLines.all { it.contains("/qn") })
        assertFalse(script.contains("ALLUSERS="))
        assertFalse(script.contains("INSTALLDIR="))
        assertFalse(script.contains("/qb"))
        assertFalse(script.contains("/passive"))
    }

    @Test
    fun relaunchesMeterAfterSilentInstall() {
        val script = SilentUpdateScript.render(
            waitPid = 9,
            msiPath = """C:\Temp\u.msi""",
            exePath = """C:\Program Files\bit-dps-meter\bit-dps-meter.exe""",
            logPath = """C:\Temp\u.log""",
        )
        assertTrue(script.contains("silent install failed"))
        assertTrue(script.contains("""%ProgramFiles%\bit-dps-meter\bit-dps-meter.exe"""))
        assertTrue(script.contains("launcher not found after install"))
        assertTrue(script.contains("sh.Run Chr(34) & launch & Chr(34), 1, False"))
        assertTrue(script.contains("tries >= 3"))
        assertTrue(script.contains("KillMeter"))
    }

    @Test
    fun utf16LeBomKeepsKoreanPath() {
        val script = SilentUpdateScript.render(
            waitPid = 1,
            msiPath = """C:\Users\한글\u.msi""",
            exePath = "a.exe",
            logPath = "a.log",
        )
        val bytes = SilentUpdateScript.utf16LeBomBytes(script)
        assertEquals(0xFF.toByte(), bytes[0])
        assertEquals(0xFE.toByte(), bytes[1])
        val decoded = String(bytes.copyOfRange(2, bytes.size), Charsets.UTF_16LE)
        assertTrue(decoded.contains("""C:\Users\한글\u.msi"""))
    }

    @Test
    fun escapeDoublesQuotesInVbs() {
        assertEquals("""C:\""quoted""\x.msi""", SilentUpdateScript.escapeForVbs("""C:\"quoted"\x.msi"""))
    }

    @Test
    fun resolveLauncherKeepsMeterExePath() {
        val path = """C:\Program Files\bit-dps-meter\bit-dps-meter.exe"""
        assertEquals(path, SilentUpdateScript.resolveLauncherPath(path))
    }

    @Test
    fun resolveLauncherReturnsEmptyWhenMissing() {
        assertEquals("", SilentUpdateScript.resolveLauncherPath(null))
        assertEquals("", SilentUpdateScript.resolveLauncherPath("  "))
    }
}
