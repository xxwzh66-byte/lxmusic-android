package com.lxmusic.android.local

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.lxmusic.android.data.model.MusicInfo
import java.io.File

sealed class DeleteResult {
    /** File removed (or already gone). */
    object Done : DeleteResult()
    /** Android 10/11+ needs user confirmation: launch this sender from the Activity. */
    data class NeedsConfirmation(val sender: android.content.IntentSender) : DeleteResult()
    /** Nothing happened; [reason] is human-readable. */
    data class Failed(val reason: String) : DeleteResult()
}

/**
 * Deletes the physical audio file behind a local track (T4).
 *
 * Handles the two storage models we support:
 *  - SAF `content://.../document/...` URIs -> [DocumentsContract.deleteDocument]
 *  - plain `file://` paths (legacy MediaStore `DATA` column) -> [File.delete]
 *
 * MediaStore-owned `content://media/...` rows cannot be deleted silently on
 * Android 11+; those report an explanatory message instead.
 */
object LocalFileDeleter {

    private const val TAG = "LocalFileDeleter"

    /**
     * Attempts to delete the file backing [music].
     *
     * @return `null` on success, otherwise a human-readable reason.
     */
    fun delete(context: Context, music: MusicInfo): DeleteResult {
        val raw = music.localPath ?: music.extra["uri"]
            ?: return DeleteResult.Failed("找不到文件路径")
        return try {
            when {
                raw.startsWith("content://") -> deleteContent(context, Uri.parse(raw))
                raw.startsWith("file://") -> deleteFile(File(Uri.parse(raw).path ?: return DeleteResult.Failed("无效路径")))
                raw.startsWith("/") -> deleteFile(File(raw))
                else -> DeleteResult.Failed("不支持的文件路径")
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "delete denied for $raw", e)
            DeleteResult.Failed("该文件不支持删除（无写入权限）")
        } catch (e: UnsupportedOperationException) {
            Log.w(TAG, "delete unsupported for $raw", e)
            DeleteResult.Failed("该文件不支持删除")
        } catch (e: Exception) {
            Log.w(TAG, "delete failed for $raw", e)
            DeleteResult.Failed("删除失败: ${e.message}")
        }
    }

    private fun deleteContent(context: Context, uri: Uri): DeleteResult {
        // SAF document URIs (picked folders / files) are directly deletable.
        if (DocumentsContract.isDocumentUri(context, uri) ||
            uri.authority?.startsWith("com.android.externalstorage") == true ||
            uri.path?.contains("/tree/") == true ||
            uri.path?.contains("/document/") == true
        ) {
            val doc = DocumentFile.fromSingleUri(context, uri)
            if (doc != null && !doc.canWrite()) return DeleteResult.Failed("该文件不支持删除（只读）")
            val ok = runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
                .getOrElse { throw it }
            return if (ok) DeleteResult.Done else DeleteResult.Failed("系统未删除该文件")
        }
        // MediaStore rows: Android 11+ (API 30+) must go through a user-confirmed request.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val pi = android.provider.MediaStore.createDeleteRequest(context.contentResolver, listOf(uri))
            return DeleteResult.NeedsConfirmation(pi.intentSender)
        }
        // Android < 11: direct delete still works; RecoverableSecurityException is caught above.
        val rows = context.contentResolver.delete(uri, null, null)
        return if (rows > 0) DeleteResult.Done else DeleteResult.Failed("系统未删除该文件")
    }

    private fun deleteFile(file: File): DeleteResult = when {
        !file.exists() -> DeleteResult.Done
        !file.canWrite() -> DeleteResult.Failed("该文件不支持删除（只读）")
        file.delete() -> DeleteResult.Done
        else -> DeleteResult.Failed("删除失败")
    }
}
