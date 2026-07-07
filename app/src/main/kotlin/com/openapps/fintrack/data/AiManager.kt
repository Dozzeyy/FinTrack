/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Bhuvan
 */

package com.openapps.fintrack.data

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import java.io.File

enum class ModelState {
    NOT_DOWNLOADED,
    DOWNLOADING,
    READY,
    INITIALIZING,
    ERROR
}

class AiManager(private val context: Context) {
    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val MODEL_URL = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.task?download=true"
    private val MODEL_FILE_NAME = "qwen2_5_model.task"
    
    private val _modelState = MutableStateFlow(ModelState.NOT_DOWNLOADED)
    val modelState = _modelState.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0)
    val downloadProgress = _downloadProgress.asStateFlow()

    private var llmInference: LlmInference? = null
    private val managerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        checkModelState()
    }

    fun getModelFile(): File {
        return File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), MODEL_FILE_NAME)
    }

    private fun checkModelState() {
        val file = getModelFile()
        if (file.exists() && file.length() > 1_000_000_000L) {
            _modelState.value = ModelState.READY
        } else {
            _modelState.value = ModelState.NOT_DOWNLOADED
        }
    }

    fun startDownload(): Long {
        val file = getModelFile()
        if (file.exists()) file.delete()

        val request = DownloadManager.Request(Uri.parse(MODEL_URL))
            .setTitle("Downloading AI Model")
            .setDescription("Qwen 2.5 for FinTrack AI Chat")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, MODEL_FILE_NAME)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadId = downloadManager.enqueue(request)
        monitorDownload(downloadId)
        return downloadId
    }

    private fun monitorDownload(downloadId: Long) {
        managerScope.launch {
            _modelState.value = ModelState.DOWNLOADING
            var downloading = true
            while (downloading) {
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = downloadManager.query(query)
                if (cursor != null && cursor.moveToFirst()) {
                    val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val downloadedIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val totalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    
                    if (statusIdx != -1 && downloadedIdx != -1 && totalIdx != -1) {
                        val status = cursor.getInt(statusIdx)
                        val downloaded = cursor.getLong(downloadedIdx)
                        val total = cursor.getLong(totalIdx)
                        
                        if (total > 0) {
                            _downloadProgress.value = (downloaded * 100 / total).toInt()
                        }

                        when (status) {
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                _modelState.value = ModelState.READY
                                downloading = false
                            }
                            DownloadManager.STATUS_FAILED -> {
                                _modelState.value = ModelState.ERROR
                                downloading = false
                            }
                        }
                    }
                }
                cursor?.close()
                delay(1000)
            }
        }
    }

    suspend fun initLlm(): Result<Unit> = withContext(Dispatchers.IO) {
        if (llmInference != null) return@withContext Result.success(Unit)
        
        _modelState.value = ModelState.INITIALIZING
        try {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(getModelFile().absolutePath)
                .setMaxTokens(1280)
                .build()
            
            llmInference = LlmInference.createFromOptions(context, options)
            _modelState.value = ModelState.READY
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("AiManager", "LLM Init Error", e)
            _modelState.value = ModelState.ERROR
            Result.failure(e)
        }
    }

    fun generateResponseStream(prompt: String): Flow<String> = callbackFlow {
        val inference = llmInference
        if (inference == null) {
            close(IllegalStateException("LLM not initialized"))
            return@callbackFlow
        }

        try {
            inference.generateResponseAsync(prompt) { partial, done ->
                trySend(partial)
                if (done) close()
            }
        } catch (e: Exception) {
            close(e)
        }
        awaitClose { }
    }

    fun close() {
        llmInference?.close()
        llmInference = null
    }
}
