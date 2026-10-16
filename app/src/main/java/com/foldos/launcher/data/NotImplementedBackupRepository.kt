package com.foldos.launcher.data

import com.foldos.launcher.core.repo.BackupRepository
import com.foldos.launcher.core.repo.ImportResult

/**
 * Placeholder [BackupRepository] until the Backup agent's implementation replaces it in
 * `AppGraphImpl.backup`. Export yields an empty JSON object, import always fails.
 */
class NotImplementedBackupRepository : BackupRepository {
    override suspend fun exportJson(): String = "{}"

    override suspend fun importJson(json: String): ImportResult =
        ImportResult(ok = false, message = "Backup is not implemented yet", itemsImported = 0)
}
