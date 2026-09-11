package com.tbread.config

import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PropertyHandlerTest {
    @Test
    fun persistableSettingsDropsVersion() {
        val source = Properties()
        source.setProperty("version", "1.9.4")
        source.setProperty("isAutoHide", "true")
        val persisted = PropertyHandler.persistableSettings(source)
        assertNull(persisted.getProperty("version"))
        assertEquals("true", persisted.getProperty("isAutoHide"))
        assertEquals("1.9.4", source.getProperty("version"))
    }
}

class VersionConfigTest {
    @Test
    fun packagedVersionIsSemverNotSettingsStaleValue() {
        val version = VersionConfig.packagedVersion()
        assertTrue(version.matches(Regex("""\d+\.\d+\.\d+""")))
        assertFalse(version.contains("\${"))
    }
}
