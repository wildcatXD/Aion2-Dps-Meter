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
            msiPath = """C:\Users\Bit Legion\bit-dps-meter-update.msi""",
            exePath = """C:\Program Files\bit-dps-meter\bit-dps-meter.exe""",
            logPath = """C:\Users\Bit Legion\install.log""",
        )
        assertTrue(script.contains("""set "MSI=C:\Users\Bit Legion\bit-dps-meter-update.msi""""))
        assertTrue(script.contains("""set "EXE=C:\Program Files\bit-dps-meter\bit-dps-meter.exe""""))
        assertTrue(script.contains("set \"WAIT_PID=4242\""))
        assertTrue(script.contains("start /wait msiexec"))
        assertTrue(script.contains("/qn"))
    }

    @Test
    fun neverFallsBackToSetupWizard() {
        val script = SilentUpdateScript.render(1, "a.msi", "a.exe", "a.log")
        val msiexecLines = script.lines().map { it.trim() }.filter {
            it.contains("msiexec") && !it.contains("echo")
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
        assertTrue(script.contains(":relaunch"))
        assertTrue(script.contains("start \"\" /NORMAL"))
        assertTrue(script.contains("""%ProgramFiles%\bit-dps-meter\bit-dps-meter.exe"""))
        assertTrue(script.contains("launcher not found after install"))
        assertTrue(script.contains("set /a TRIES+="))
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

    @Test
    fun escapesPercentInPaths() {
        assertTrue(SilentUpdateScript.escapeForBatch("""C:\%TEMP%\x.msi""").contains("%%TEMP%%"))
    }
}
