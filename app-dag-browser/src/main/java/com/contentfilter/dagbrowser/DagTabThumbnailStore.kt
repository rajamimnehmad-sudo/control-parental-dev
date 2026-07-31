package com.contentfilter.dagbrowser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal object DagTabThumbnailKeyPolicy {
    private val KeyPattern = Regex("^[a-f0-9]{32}$")

    fun isValid(key: String): Boolean = KeyPattern.matches(key)
}

internal class DagTabThumbnailStore(context: Context) {
    private val directory = File(context.filesDir, DirectoryName)

    fun encode(bitmap: Bitmap): ByteArray? =
        runCatching {
            ByteArrayOutputStream().use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JpegQuality, output)) return null
                output.toByteArray().takeIf { it.size in 1..MaxEncodedBytes }
            }
        }.getOrNull()

    fun save(
        key: String,
        encoded: ByteArray,
    ): Boolean {
        if (!DagTabThumbnailKeyPolicy.isValid(key) || encoded.size !in 1..MaxEncodedBytes) return false
        return runCatching {
            directory.mkdirs()
            val destination = fileFor(key)
            val partial = File(directory, ".$key.tmp")
            partial.outputStream().use { output ->
                output.write(encoded)
                output.flush()
            }
            Files.move(
                partial.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
            true
        }.getOrDefault(false)
    }

    fun load(key: String): Bitmap? {
        if (!DagTabThumbnailKeyPolicy.isValid(key)) return null
        val file = fileFor(key)
        if (!file.isFile || file.length() !in 1..MaxEncodedBytes.toLong()) return null
        val bitmap = runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
        if (
            bitmap == null ||
            bitmap.width !in 1..DagTabCapacityPolicy.ThumbnailWidth ||
            bitmap.height !in 1..DagTabCapacityPolicy.ThumbnailHeight
        ) {
            bitmap?.recycle()
            file.delete()
            return null
        }
        return bitmap
    }

    fun delete(key: String) {
        if (DagTabThumbnailKeyPolicy.isValid(key)) fileFor(key).delete()
    }

    fun retain(keys: Set<String>) {
        directory.listFiles().orEmpty().forEach { file ->
            val key = file.name.removeSuffix(FileSuffix)
            if (!file.isFile || !file.name.endsWith(FileSuffix) || key !in keys) file.delete()
        }
    }

    fun clear() {
        directory.listFiles().orEmpty().forEach(File::delete)
        directory.delete()
    }

    private fun fileFor(key: String): File = File(directory, "$key$FileSuffix")

    private companion object {
        const val DirectoryName = "tab-previews"
        const val FileSuffix = ".jpg"
        const val JpegQuality = 78
        const val MaxEncodedBytes = 256 * 1024
    }
}
