package com.tbread.addon

import com.tbread.entity.DpsLog

interface BattleLogUploader {
    /** Upload a stored fight. Prefer `log.encounterJson()` (`bit-legion-encounter-v1`) over raw packets. */
    fun upload(log: DpsLog): Boolean
}
