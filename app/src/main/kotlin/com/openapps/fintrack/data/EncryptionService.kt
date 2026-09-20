/*
 * FinTrack
 * Copyright (C) 2026 Bhuvan (app.upstream242@passmail.com)
 * SPDX-License-Identifier: GPL-3.0-or-later

 This program is free software; you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation; either version 2 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.
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
    private const val HEADER = "XPT"
    private const val VERSION = "01"
    private const val DEFAULT_ITERATIONS = 600000
    private const val KEY_LENGTH = 256
    private const val SALT_LENGTH = 32
    private const val IV_LENGTH = 12
    private const val TAG_LENGTH = 128

    private object Base64Compat {
        fun encodeToString(input: ByteArray): String {
            return try {
                Base64.encodeToString(input, Base64.NO_WRAP)
            } catch (e: Throwable) {
                java.util.Base64.getEncoder().encodeToString(input)
            }
        }

        fun decode(input: String): ByteArray {
            return try {
                Base64.decode(input, Base64.DEFAULT)
            } catch (e: Throwable) {
                java.util.Base64.getDecoder().decode(input)
            }
        }
    }

    private fun safeLog(msg: String, e: Throwable? = null) {
        try {
            if (e != null) Log.e("EncryptionService", msg, e) else Log.e("EncryptionService", msg)
        } catch (err: Throwable) {
            println("EncryptionService: $msg ${e?.message}")
        }
    }

    fun encryptFile(
        inputFile: File, 
        outputFile: File, 
        password: CharArray, 
        iterations: Int = DEFAULT_ITERATIONS,
        onProgress: (Float) -> Unit = {}
    ): Result<Unit> {
        val tempFile = File(outputFile.path + ".tmp")
        if (tempFile.exists()) tempFile.delete()

        return try {
            val salt = ByteArray(SALT_LENGTH)
            SecureRandom().nextBytes(salt)

            val secretKey = deriveKey(password, salt, iterations)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val iv = ByteArray(IV_LENGTH)
            SecureRandom().nextBytes(iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH, iv))

            val totalSize = inputFile.length()
            var processed = 0L

            FileOutputStream(tempFile).use { fos ->
                val saltBase64 = Base64Compat.encodeToString(salt)
                val ivBase64 = Base64Compat.encodeToString(iv)
                
                val headerString = "$HEADER|$VERSION|$iterations|$saltBase64|$ivBase64:"
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
            
            if (outputFile.exists()) outputFile.delete()
            if (tempFile.renameTo(outputFile)) {
                Result.success(Unit)
            } else {
                try {
                    tempFile.copyTo(outputFile, overwrite = true)
                    tempFile.delete()
                    Result.success(Unit)
                } catch (e: Exception) {
                    Result.failure(Exception("Failed to finalize encrypted file: ${e.message}"))
                }
            }
        } catch (e: Exception) {
            safeLog("Encryption failed", e)
            if (tempFile.exists()) tempFile.delete()
            Result.failure(e)
        }
    }

    fun decryptFile(inputFile: File, outputFile: File, password: CharArray, onProgress: (Float) -> Unit = {}): Result<Unit> {
        val tempFile = File(outputFile.path + ".tmp")
        if (tempFile.exists()) tempFile.delete()

        return try {
            val totalSize = inputFile.length()
            var processed = 0L

            FileInputStream(inputFile).use { fis ->
                val headerBuffer = ByteArray(256) 
                val readHeader = fis.read(headerBuffer)
                val content = String(headerBuffer, 0, readHeader)
                val colonIndex = content.indexOf(':')
                if (colonIndex == -1) return Result.failure(Exception("Invalid file format: No header separator"))

                val headerPart = content.substring(0, colonIndex)
                if (!headerPart.startsWith(HEADER)) return Result.failure(Exception("Invalid file format: Wrong header"))

                val salt: ByteArray
                val iv: ByteArray
                val iterations: Int

                if (headerPart.contains("|")) {
                    
                    val parts = headerPart.split("|")
                    if (parts.size >= 5) {
                        iterations = parts[2].toIntOrNull() ?: DEFAULT_ITERATIONS
                        salt = Base64Compat.decode(parts[3])
                        iv = Base64Compat.decode(parts[4])
                    } else if (parts.size == 3) {
                
                        iterations = parts[1].toIntOrNull() ?: DEFAULT_ITERATIONS
                        val rest = parts[2]
                        val saltBase64 = rest.substring(0, 44)
                        val ivBase64 = rest.substring(44)
                        salt = Base64Compat.decode(saltBase64)
                        iv = Base64Compat.decode(ivBase64)
                    } else {
                        return Result.failure(Exception("Invalid header format"))
                    }
                } else {
                    
                    val saltBase64 = headerPart.substring(5, 5 + 44)
                    val ivBase64 = headerPart.substring(49, 49 + 16)
                    salt = Base64Compat.decode(saltBase64)
                    iv = Base64Compat.decode(ivBase64)
                    iterations = 600000 
                }

                val secretKey = deriveKey(password, salt, iterations)
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

            if (outputFile.exists()) outputFile.delete()
            if (tempFile.renameTo(outputFile)) {
                Result.success(Unit)
            } else {
                try {
                    tempFile.copyTo(outputFile, overwrite = true)
                    tempFile.delete()
                    Result.success(Unit)
                } catch (e: Exception) {
                    Result.failure(Exception("Failed to finalize decrypted file: ${e.message}"))
                }
            }
        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            if (e is AEADBadTagException) {
                safeLog("Integrity check failed: File corrupted or tampered.", e)
                return Result.failure(Exception("File integrity check failed. The backup may be corrupted."))
            }
            safeLog("Decryption failed", e)
            Result.failure(e)
        }
    }

    fun rotatePassword(file: File, oldPassword: CharArray, newPassword: CharArray, iterations: Int = DEFAULT_ITERATIONS): Result<Unit> {
        val decryptedTemp = File(file.parent, "rotate_temp_dec.db")
        val encryptedTemp = File(file.parent, "rotate_temp_enc.db")
        
        return try {
            val decResult = decryptFile(file, decryptedTemp, oldPassword)
            if (decResult.isFailure) return decResult

            val encResult = encryptFile(decryptedTemp, encryptedTemp, newPassword, iterations)
            if (encResult.isFailure) return encResult

            if (file.exists()) file.delete()
            if (encryptedTemp.renameTo(file)) {
                Result.success(Unit)
            } else {
                encryptedTemp.copyTo(file, overwrite = true)
                encryptedTemp.delete()
                Result.success(Unit)
            }
        } catch (e: Exception) {
            safeLog("Password rotation failed", e)
            Result.failure(e)
        } finally {
            if (decryptedTemp.exists()) decryptedTemp.delete()
            if (encryptedTemp.exists()) encryptedTemp.delete()
        }
    }

    fun isEncrypted(file: File): Boolean {
        return try {
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(16)
                if (fis.read(buffer) < 3) return false
                val header = String(buffer)
                header.startsWith(HEADER)
            }
        } catch (e: Exception) {
            false
        }
    }

    fun isValidSQLite(file: File): Boolean {
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

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        return try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val spec = PBEKeySpec(password, salt, iterations, KEY_LENGTH)
            val tmp = factory.generateSecret(spec)
            SecretKeySpec(tmp.encoded, "AES")
        } catch (e: Exception) {
            safeLog("Failed to derive secret key", e)
            throw Exception("Encryption failed: Could not generate a secret key. Please ensure your password is correct and try again.")
        }
    }
}
