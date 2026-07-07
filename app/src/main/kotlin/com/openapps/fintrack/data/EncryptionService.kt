/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Bhuvan
 */

package com.openapps.fintrack.data

import android.util.Base64
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.crypto.AEADBadTagException

object EncryptionService {
    private const val HEADER = "XPT" // FinTrack Data
    private const val VERSION = "01"
    private const val ITERATIONS = 600000
    private const val KEY_LENGTH = 256
    private const val SALT_LENGTH = 32
    private const val IV_LENGTH = 12
    private const val TAG_LENGTH = 128

    fun encryptFile(inputFile: File, outputFile: File, password: String, onProgress: (Float) -> Unit = {}): Result<Unit> {
        val tempFile = File(outputFile.path + ".tmp")
        if (tempFile.exists()) tempFile.delete()

        return try {
            val salt = ByteArray(SALT_LENGTH)
            SecureRandom().nextBytes(salt)

            val secretKey = deriveKey(password, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val iv = ByteArray(IV_LENGTH)
            SecureRandom().nextBytes(iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH, iv))

            val totalSize = inputFile.length()
            var processed = 0L

            FileOutputStream(tempFile).use { fos ->
                val saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP)
                val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
                val headerString = "$HEADER$VERSION$saltBase64$ivBase64:"
                fos.write(headerString.toByteArray())

                val buffer = ByteArray(8192)
                FileInputStream(inputFile).use { fis ->
                    var read: Int
                    while (fis.read(buffer).also { read = it } != -1) {
                        val output = cipher.update(buffer, 0, read)
                        if (output != null) fos.write(output)
                        processed += read
                        if (totalSize > 0) onProgress(processed.toFloat() / totalSize)
                    }
                }
                val finalOutput = cipher.doFinal()
                if (finalOutput != null) fos.write(finalOutput)
                onProgress(1.0f)
            }
            
            if (tempFile.renameTo(outputFile)) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to finalize encrypted file"))
            }
        } catch (e: Exception) {
            Log.e("EncryptionService", "Encryption failed", e)
            if (tempFile.exists()) tempFile.delete()
            Result.failure(e)
        }
    }

    fun decryptFile(inputFile: File, outputFile: File, password: String, onProgress: (Float) -> Unit = {}): Result<Unit> {
        val tempFile = File(outputFile.path + ".tmp")
        if (tempFile.exists()) tempFile.delete()

        return try {
            val totalSize = inputFile.length()
            var processed = 0L

            FileInputStream(inputFile).use { fis ->
                val headerBuffer = ByteArray(128)
                val readHeader = fis.read(headerBuffer)
                val content = String(headerBuffer, 0, readHeader)
                val colonIndex = content.indexOf(':')
                if (colonIndex == -1) return Result.failure(Exception("Invalid file format: No header separator"))

                val headerPart = content.substring(0, colonIndex)
                if (!headerPart.startsWith(HEADER)) return Result.failure(Exception("Invalid file format: Wrong header"))
                
                val saltBase64 = headerPart.substring(5, 5 + 44)
                val ivBase64 = headerPart.substring(49, 49 + 16)

                val salt = Base64.decode(saltBase64, Base64.DEFAULT)
                val iv = Base64.decode(ivBase64, Base64.DEFAULT)

                val secretKey = deriveKey(password, salt)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH, iv))

                val actualHeaderByteCount = headerPart.toByteArray().size + 1
                processed += actualHeaderByteCount
                
                FileOutputStream(tempFile).use { fos ->
                    FileInputStream(inputFile).use { dataFis ->
                        dataFis.skip(actualHeaderByteCount.toLong())
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (dataFis.read(buffer).also { read = it } != -1) {
                            val output = cipher.update(buffer, 0, read)
                            if (output != null) fos.write(output)
                            processed += read
                            if (totalSize > 0) onProgress(processed.toFloat() / totalSize)
                        }
                        val finalOutput = cipher.doFinal()
                        if (finalOutput != null) fos.write(finalOutput)
                        onProgress(1.0f)
                    }
                }
            }

            if (tempFile.renameTo(outputFile)) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to finalize decrypted file"))
            }
        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            if (e is AEADBadTagException) {
                Log.e("EncryptionService", "Integrity check failed: File corrupted or tampered.", e)
                return Result.failure(Exception("File integrity check failed. The backup may be corrupted."))
            }
            Log.e("EncryptionService", "Decryption failed", e)
            Result.failure(e)
        }
    }

    fun isEncrypted(file: File): Boolean {
        if (!file.exists() || file.length() < 3) return false
        return try {
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(3)
                fis.read(buffer)
                String(buffer) == HEADER
            }
        } catch (e: Exception) {
            false
        }
    }

    fun isValidSQLite(file: File): Boolean {
        if (!file.exists() || file.length() < 16) return false
        return try {
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(16)
                if (fis.read(buffer) != 16) return false
                val header = String(buffer)
                header.startsWith("SQLite format 3")
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        return try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
            val tmp = factory.generateSecret(spec)
            SecretKeySpec(tmp.encoded, "AES")
        } catch (e: Exception) {
            Log.e("EncryptionService", "Failed to derive secret key", e)
            throw Exception("Encryption failed: Could not generate a secret key. Please ensure your password is correct and try again.")
        }
    }
}
