package com.taqijafri.notisave.util

import android.content.Context
import android.util.Base64
import android.util.Base64OutputStream
import android.util.Base64InputStream
import android.util.JsonReader
import com.taqijafri.notisave.data.NotiDbHelper
import com.taqijafri.notisave.data.SavedNotification
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.FilterOutputStream
import javax.crypto.CipherOutputStream
import java.io.OutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.IOException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object NotificationBackup {
    private const val MAGIC = "NOTISAVE-ENC-1"
    private const val ITERATIONS = 210_000
    private const val MAX_BACKUP_BYTES = 64 * 1024 * 1024
    private const val MAX_PLAINTEXT_BYTES = 45 * 1024 * 1024
    private const val PAGE_SIZE = 500

    fun export(context: Context, uri: android.net.Uri, password: CharArray) {
        require(password.size >= 8) { "Use a password with at least 8 characters" }
        val temp = createExportTempFile(context)
        try {
            val salt = ByteArray(16).also(SecureRandom()::nextBytes)
            val iv = ByteArray(12).also(SecureRandom()::nextBytes)
            val key = deriveKey(password, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
            FileOutputStream(temp).use { raw ->
                val stream = BufferedOutputStream(raw)
                stream.write("$MAGIC\n${Base64.encodeToString(salt, Base64.NO_WRAP)}\n${Base64.encodeToString(iv, Base64.NO_WRAP)}\n".toByteArray(Charsets.UTF_8))
                val encoded = Base64OutputStream(NonClosingOutputStream(stream), Base64.NO_WRAP)
                CipherOutputStream(encoded, cipher).use { encrypted ->
                    encrypted.write('['.code)
                    var recordCount = 0
                    var plainBytes = 2L
                    NotiDbHelper.getInstance(context).forEachNotificationSnapshot { item ->
                        val record = JSONObject().apply {
                            put("package", item.packageName); put("app", item.appName)
                            put("title", item.title); put("text", item.text); put("bigText", item.bigText)
                            put("time", item.postTime); put("read", item.isRead)
                        }.toString().toByteArray(Charsets.UTF_8)
                        val delimiterBytes = if (recordCount > 0) 1 else 0
                        plainBytes += record.size + delimiterBytes
                        require(plainBytes <= MAX_PLAINTEXT_BYTES) { "History is too large to export" }
                        require(++recordCount <= 100_000) { "History contains too many records" }
                        if (delimiterBytes != 0) encrypted.write(','.code)
                        encrypted.write(record)
                    }
                    encrypted.write(']'.code)
                }
                stream.flush()
            }
            context.contentResolver.openOutputStream(uri, "w")!!.use { target ->
                FileInputStream(temp).use { source -> source.copyTo(target) }
            }
        } finally {
            temp.delete()
        }
    }

    private fun createExportTempFile(context: Context): File {
        val encryptedBytes = MAX_PLAINTEXT_BYTES.toLong() + 16
        val encodedBytes = ((encryptedBytes + 2) / 3) * 4
        val headerBytes = MAGIC.toByteArray(Charsets.UTF_8).size + 1 + 24 + 1 + 16 + 1
        val requiredBytes = encodedBytes + headerBytes
        val directory = listOfNotNull(context.filesDir, context.getExternalFilesDir(null))
            .distinctBy { it.absolutePath }
            .filter { (it.isDirectory || it.mkdirs()) && it.usableSpace >= requiredBytes }
            .maxByOrNull { it.usableSpace }
            ?: throw IOException("Not enough storage space to export backup")
        return File.createTempFile("notisave-export-", ".nsb", directory)
    }

    fun import(context: Context, uri: android.net.Uri, password: CharArray): Int {
        require(password.size >= 8) { "Use a password with at least 8 characters" }
        context.contentResolver.openInputStream(uri)!!.use { source ->
            val limited = LimitedInputStream(source, MAX_BACKUP_BYTES)
            require(readLine(limited) == MAGIC) { "Unsupported backup file" }
            val salt = Base64.decode(readLine(limited), Base64.DEFAULT)
            val iv = Base64.decode(readLine(limited), Base64.DEFAULT)
            require(salt.size == 16 && iv.size == 12) { "Invalid backup parameters" }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(128, iv))
            val decoded = Base64InputStream(limited, Base64.DEFAULT)
            val plaintext = LimitedInputStream(javax.crypto.CipherInputStream(decoded, cipher), MAX_PLAINTEXT_BYTES)
            val temp = File.createTempFile("notisave-restore-", ".json", context.cacheDir)
            try {
                FileOutputStream(temp).use { output ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = plaintext.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                }
                forEachNotification(temp) { }
                val db = NotiDbHelper.getInstance(context)
                val batch = ArrayList<SavedNotification>(PAGE_SIZE)
                var total = 0
                forEachNotification(temp) { item ->
                    batch.add(item)
                    if (batch.size == PAGE_SIZE) {
                        total += db.restoreNotifications(batch)
                        batch.clear()
                    }
                }
                if (batch.isNotEmpty()) total += db.restoreNotifications(batch)
                return total
            } finally {
                temp.delete()
            }
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        return try {
            SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun readNotification(reader: JsonReader): SavedNotification {
        var packageName: String? = null
        var appName: String? = null
        var title: String? = null
        var text: String? = null
        var bigText = ""
        var postTime: Long? = null
        var isRead = false
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "package" -> packageName = reader.nextString()
                "app" -> appName = reader.nextString()
                "title" -> title = reader.nextString()
                "text" -> text = reader.nextString()
                "bigText" -> bigText = reader.nextString()
                "time" -> postTime = reader.nextLong()
                "read" -> isRead = reader.nextBoolean()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return SavedNotification(
            packageName = requireNotNull(packageName), appName = requireNotNull(appName),
            title = requireNotNull(title), text = requireNotNull(text), bigText = bigText,
            postTime = requireNotNull(postTime), isRead = isRead
        )
    }

    private fun forEachNotification(file: File, action: (SavedNotification) -> Unit): Int {
        FileInputStream(file).use { cleartext ->
            JsonReader(InputStreamReader(cleartext, Charsets.UTF_8)).use { reader ->
                reader.beginArray()
                var count = 0
                while (reader.hasNext()) {
                    require(++count <= 100_000) { "Backup contains too many records" }
                    action(readNotification(reader))
                }
                reader.endArray()
                require(reader.peek() == android.util.JsonToken.END_DOCUMENT) { "Unexpected data after backup" }
                return count
            }
        }
    }

    private fun readLine(input: InputStream): String {
        val bytes = ByteArrayOutputStream()
        while (bytes.size() <= 128) {
            val value = input.read()
            require(value >= 0) { "Incomplete backup file" }
            if (value == '\n'.code) return bytes.toString("UTF-8")
            bytes.write(value)
        }
        throw IllegalArgumentException("Invalid backup header")
    }

    private class LimitedInputStream(input: InputStream, private val maxBytes: Int) : FilterInputStream(input) {
        private var count = 0
        override fun read(): Int {
            val value = super.read()
            if (value >= 0 && ++count > maxBytes) throw IOException("Backup data is too large")
            return value
        }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val read = super.read(buffer, offset, length)
            if (read > 0) {
                count += read
                if (count > maxBytes) throw IOException("Backup data is too large")
            }
            return read
        }
    }

    private class NonClosingOutputStream(output: OutputStream) : FilterOutputStream(output) {
        override fun close() = flush()
    }
}
