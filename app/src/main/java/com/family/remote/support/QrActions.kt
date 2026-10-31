package com.family.remote.support

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * v0.6.1 — owner actions on the pairing QR shown by the phone that needs help:
 * copy the readable code, save the QR as a PNG into the phone's Download
 * folder, or hand both to the Android share sheet (WhatsApp/SMS/etc.).
 */

/** Copy [text] to the system clipboard. Returns true when it worked. */
fun copyToClipboard(context: Context, label: String, text: String): Boolean =
    try {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        true
    } catch (_: Exception) {
        false
    }

/**
 * Save [bitmap] as a PNG into the public Download folder and return a short
 * human-readable description of the saved file (null on failure).
 *
 * API 29+: MediaStore.Downloads — no storage permission required.
 * API 26–28: direct write to the public Downloads directory, which needs
 * WRITE_EXTERNAL_STORAGE (declared with maxSdkVersion="28" and requested
 * at runtime by the caller before this function is used).
 */
fun saveQrToDownloads(context: Context, bitmap: Bitmap, fileName: String): String? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "image/png")
            }
            val uri = context.contentResolver
                .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return null
            val ok = context.contentResolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            } ?: false
            if (!ok) return null
            "Download/$fileName"
        } else {
            @Suppress("DEPRECATION")
            val dir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
            )
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, fileName)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            "Download/$fileName"
        }
    } catch (_: Exception) {
        null
    }
}

/**
 * Build a share-sheet intent carrying the QR PNG (via FileProvider, because a
 * raw file URI is forbidden on API 24+) plus the readable pairing code as text.
 * Returns null when the image cannot be staged in the cache.
 *
 * M8 (v0.34.0) — the chooser title and the shared message body resolve from
 * values/strings.xml (app language) instead of hardcoded English.
 */
fun buildQrShareIntent(context: Context, bitmap: Bitmap, code: String): Intent? =
    try {
        val shareDir = File(context.cacheDir, "qr_share").apply { mkdirs() }
        val file = File(shareDir, "pairing-code-${code.substringBefore('-')}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(
                Intent.EXTRA_TEXT,
                context.getString(R.string.qr_share_text, code)
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        Intent.createChooser(send, context.getString(R.string.qr_share_chooser_title))
    } catch (_: Exception) {
        null
    }
