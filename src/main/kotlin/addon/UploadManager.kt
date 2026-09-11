package com.tbread.addon

import com.tbread.entity.DpsLog

object UploadManager {
    private val uploader: BattleLogUploader = UploadAddonImpl()

    fun isAvailable(): Boolean = true

    fun upload(log: DpsLog): String? = uploader.upload(log)
}
