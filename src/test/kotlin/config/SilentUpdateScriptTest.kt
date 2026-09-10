package com.tbread.config

import kotlin.test.Test
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
        val msiexecLines = script.lines().map { it.trim() }.filter { it.contains("msiexec") }
        assertTrue(msiexecLines.isNotEmpty())
        assertTrue(msiexecLines.all { it.contains("/qn") })
        assertFalse(script.contains("ALLUSERS="))
        assertFalse(script.contains("INSTALLDIR="))
    }

    @Test
    fun escapesPercentInPaths() {
        assertTrue(SilentUpdateScript.escapeForBatch("""C:\%TEMP%\x.msi""").contains("%%TEMP%%"))
    }
}
