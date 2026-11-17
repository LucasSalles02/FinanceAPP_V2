package com.financeapp.nativeapp.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Foto local por UID, sem compartilhamento com outras contas e sem precisar de
 * permissões de armazenamento ou de um bucket Firebase Storage pago.
 * Salva uma miniatura no armazenamento PRIVADO do aplicativo.
 */
object ProfilePhotoStore {
    private fun photoFile(context: Context, uid: String): File {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(uid.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(File(context.applicationContext.filesDir, "profile_photos"), "$hash.jpg")
    }

    suspend fun load(context: Context, uid: String): Bitmap? = withContext(Dispatchers.IO) {
        val file = photoFile(context, uid)
        if (file.isFile) BitmapFactory.decodeFile(file.absolutePath) else null
    }

    suspend fun save(context: Context, uid: String, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val boundsStream = resolver.openInputStream(uri)
                ?: error("Não foi possível ler a imagem escolhida.")
            boundsStream.use { BitmapFactory.decodeStream(it, null, options) }
            require(options.outWidth > 0 && options.outHeight > 0) { "Selecione uma imagem válida." }
            var sample = 1
            while (options.outWidth / sample > 1024 || options.outHeight / sample > 1024) sample *= 2
            val bitmap = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null,
                    BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 })
            } ?: error("Não foi possível abrir a foto.")
            val orientation = runCatching {
                resolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                ) } ?: ExifInterface.ORIENTATION_NORMAL
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            val matrix = Matrix().apply {
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                }
            }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            val maxSide = maxOf(rotated.width, rotated.height)
            val scaled = if (maxSide > 512) Bitmap.createScaledBitmap(
                rotated, (rotated.width * 512 / maxSide).coerceAtLeast(1), (rotated.height * 512 / maxSide).coerceAtLeast(1), true
            ) else rotated
            val target = photoFile(context, uid)
            check(target.parentFile?.mkdirs() == true || target.parentFile?.isDirectory == true)
            val tmp = File(target.parentFile, "${target.name}.tmp")
            try {
                tmp.outputStream().use { stream ->
                    check(scaled.compress(Bitmap.CompressFormat.JPEG, 85, stream))
                }
                check(tmp.renameTo(target) || run {
                    tmp.copyTo(target, overwrite = true)
                    tmp.delete()
                    true
                })
            } finally {
                tmp.delete()
            }
            true
        }.getOrDefault(false)
    }

    suspend fun remove(context: Context, uid: String): Boolean = withContext(Dispatchers.IO) {
        val file = photoFile(context, uid)
        !file.exists() || file.delete()
    }
}
