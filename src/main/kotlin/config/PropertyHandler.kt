package com.tbread.config

import org.slf4j.LoggerFactory
import java.io.*
import java.util.*

object PropertyHandler {
    private val props = Properties()
    private const val SETTING_PROPERTY_FILE_NAME = "settings.properties"
    private const val VERSION_PROPERTY_FILE_NAME = "version.properties"
    internal const val VERSION_KEY = "version"
    private val logger = LoggerFactory.getLogger(PropertyHandler::class.java)

    private val settingFile: File = run {
        val appData = System.getenv("APPDATA") ?: System.getProperty("user.home")
        val dir = File(appData, "Aion2DpsMeter")
        dir.mkdirs()
        File(dir, SETTING_PROPERTY_FILE_NAME)
    }

    init {
        loadSettings()
        loadProperties(VERSION_PROPERTY_FILE_NAME, false)
    }

    private fun loadSettings() {
        try {
            if (settingFile.exists()) {
                FileInputStream(settingFile).use { fis ->
                    props.load(fis)
                }
                // 설치본 버전은 settings 에 두지 않습니다. 예전에 저장된 값은 버립니다.
                props.remove(VERSION_KEY)
            } else {
                logger.info("설정파일이 존재하지 않아 파일을 생성합니다. 경로: ${settingFile.absolutePath}")
                settingFile.createNewFile()
            }
        } catch (e: IOException) {
            logger.error("설정파일 읽기에 실패했습니다.")
        }
    }

    private fun loadProperties(fname: String, outerFlag: Boolean) {
        try {
            if (outerFlag) {
                FileInputStream(fname).use { fis ->
                    props.load(fis)
                }
            } else {
                object {}.javaClass.getResourceAsStream("/$fname")?.use {
                    props.load(it)
                }
            }
        } catch (e: FileNotFoundException) {
            logger.info("설정파일이 존재하지 않아 파일을 생성합니다.")
            FileOutputStream(fname).use {}
        } catch (e: IOException) {
            logger.error("설정파일 읽기에 실패했습니다.")
        }
    }

    private fun encodeToEucKr(key: String?): String? {
        if (key == null) return null
        return try {
            String(key.toByteArray(Charsets.ISO_8859_1), charset("EUC-KR"))
        } catch (e: UnsupportedEncodingException) {
            key
        }
    }

    private fun save() {
        FileOutputStream(settingFile).use { fos ->
            persistableSettings(props).store(fos, "settings")
        }
    }

    internal fun persistableSettings(source: Properties): Properties {
        val out = Properties()
        for (name in source.stringPropertyNames()) {
            if (name != VERSION_KEY) {
                out.setProperty(name, source.getProperty(name))
            }
        }
        return out
    }

    fun getProperty(key: String): String? {
        return encodeToEucKr(props.getProperty(key))
    }

    fun getProperty(key: String, defaultValue: String): String? {
        return encodeToEucKr(props.getProperty(key, defaultValue))
    }

    fun setProperty(key: String, value: String) {
        if (key == VERSION_KEY) return
        props.setProperty(key, value)
        save()
    }


}