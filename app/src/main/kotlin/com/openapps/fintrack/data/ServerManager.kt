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

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.openapps.fintrack.R
import com.openapps.fintrack.domain.repository.FinanceRepository
import io.ktor.http.*
import io.ktor.network.tls.certificates.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.NetworkInterface
import java.time.LocalDate
import java.util.*
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class TransactionDto(
    val id: Int = 0, val date: String, val time: String, val accountId: Int,
    val toAccountId: Int? = null, val categoryId: Int?, val amount: Double,
    val note: String?, val tags: String? = null, val transactionNumber: String? = null,
    val partyId: Int? = null, val toPartyId: Int? = null,
    val subName: String? = null, val subFrequency: Int? = null,
    val categoryName: String? = null, val categoryType: String? = null, 
    val accountName: String? = null, val toAccountName: String? = null,
    val partyName: String? = null, val toPartyName: String? = null,
    val isReconciled: Boolean = false
)

@Serializable
data class CategoryDto(val id: Int, val name: String, val type: String)

@Serializable
data class MajorHeadDto(val id: Int, val name: String)

@Serializable
data class MinorHeadDto(val id: Int, val name: String, val majorHeadId: Int)

@Serializable
data class AccountDto(val id: Int, val name: String, val type: String, val balance: Double, val majorHeadId: Int? = null, val minorHeadId: Int? = null, val openingBalance: Double = 0.0)

@Serializable
data class TagDto(val id: Int, val name: String, val isEnabled: Boolean)

@Serializable
data class TemplateDto(
    val id: Int, val name: String, val type: String, val accountId: Int?,
    val toAccountId: Int?, val categoryId: Int?, val amount: Double?,
    val note: String?, val tags: String?
)

@Serializable
data class ClientConnection(val ip: String, val lastSeen: Long, val userAgent: String)

@Serializable
data class NoteDto(val id: Int = 0, val title: String, val content: String, val type: String = "text", val notebookId: Int? = null, val createdAt: Long, val tags: String? = null, val editedAt: Long? = null)

@Serializable
data class NotebookDto(val id: Int, val name: String, val createdAt: Long)

@Serializable
data class PairingNewResponse(val id: String)

@Serializable
data class PairingStatusResponse(
    val authorized: Boolean,
    val token: String? = null,
    val echo_id: String? = null,
    val error: String? = null
)

data class TerminationLog(val timestamp: Long, val client: String, val reason: String)

class ServerManager(private val context: Context, private val repository: FinanceRepository) {

    private val _isRunning = MutableStateFlow(false)
    val isRunning = _isRunning.asStateFlow()

    private val _isStopping = MutableStateFlow(false)
    val isStopping = _isStopping.asStateFlow()

    private val _httpUrl = MutableStateFlow<String?>(null)
    val httpUrl = _httpUrl.asStateFlow()

    private val _httpsUrl = MutableStateFlow<String?>(null)
    val httpsUrl = _httpsUrl.asStateFlow()
    
    private val _serverError = MutableStateFlow<String?>(null)
    val serverError = _serverError.asStateFlow()

    private val _activeClients = MutableStateFlow<Map<String, ClientConnection>>(emptyMap())
    val activeClients = _activeClients.asStateFlow()

    private val _terminationLogs = MutableStateFlow<List<TerminationLog>>(emptyList())
    val terminationLogs = _terminationLogs.asStateFlow()

    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
    private var serverJob: Job? = null
    private val serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val dbWriteMutex = Mutex()

    private val jwtSecret: String by lazy {
        val existing = EncryptedPrefsHelper.getString("jwt_secret", null)
        if (existing != null) existing
        else {
            val newSecret = UUID.randomUUID().toString()
            EncryptedPrefsHelper.putString("jwt_secret", newSecret)
            newSecret
        }
    }
    private val jwtIssuer = "fintrack-server"
    private val jwtAudience = "fintrack-client"
    private val jwtRealm = "fintrack"

    private val pendingPairings = ConcurrentHashMap<String, Boolean>()
    private val pairingRateLimit = ConcurrentHashMap<String, Long>()

    var onDatabaseChange: (() -> Unit)? = null

    private val NOTIFICATION_ID = 888

    fun startServer(httpPort: Int = 8080, httpsPort: Int = 8443) {
        if (_isRunning.value) return
        _serverError.value = null

        val ip = getLocalIpAddress() ?: "127.0.0.1"
        _httpUrl.value = "http://$ip:$httpPort"
        _httpsUrl.value = "https://$ip:$httpsPort"
        Log.d("ServerManager", "Starting server on ${_httpUrl.value} and ${_httpsUrl.value}")

        ensureJksProvider()
        val keyStoreFile = File(context.filesDir, "fintrack_keystore.jks")
        val keyStore: java.security.KeyStore? = try {
            if (!keyStoreFile.exists()) {
                generateCertificate(
                    file = keyStoreFile,
                    keyAlias = "fintrack",
                    keyPassword = "changeit",
                    jksPassword = "changeit"
                )
            } else {
                java.security.KeyStore.getInstance("JKS").apply {
                    keyStoreFile.inputStream().use { load(it, "changeit".toCharArray()) }
                }
            }
        } catch (e: Exception) {
            Log.e("ServerManager", "KeyStore initialization failed", e)
            null
        }

        serverJob = serverScope.launch {
            while (isActive) {
                delay(60000) 
                val now = System.currentTimeMillis()
                val currentClients = _activeClients.value
                val pruned = currentClients.filter { now - it.value.lastSeen < 300000 }
                if (pruned.size != currentClients.size) {
                    _activeClients.value = pruned
                }
            }
        }

        try {
            server = embeddedServer(Netty, port = httpPort, host = "0.0.0.0") {
                install(ContentNegotiation) { json() }
                install(CORS) {
                    anyHost()
                    allowHeader(HttpHeaders.Authorization)
                    allowHeader(HttpHeaders.ContentType)
                    allowHeader("authorization")
                    allowMethod(HttpMethod.Options)
                    allowMethod(HttpMethod.Get)
                    allowMethod(HttpMethod.Post)
                    exposeHeader("X-Debug-Reason")
                }
                install(Authentication) {
                    jwt("auth-jwt") {
                        realm = jwtRealm
                        verifier(
                            JWT.require(Algorithm.HMAC256(jwtSecret))
                                .withAudience(jwtAudience)
                                .withIssuer(jwtIssuer)
                                .build()
                        )
                        validate { credential ->
                            if (credential.payload.audience.contains(jwtAudience)) {
                                JWTPrincipal(credential.payload)
                            } else null
                        }
                    }
                }

                routing {
                    get("/") {
                        this@ServerManager.addLog("Web", "Client loaded index.html")
                        call.respondText(this@ServerManager.getWebClientHtml(), ContentType.Text.Html)
                    }

                    get("/pairing/new") {
                        val remoteIp = call.request.local.remoteHost
                        val now = System.currentTimeMillis()
                        val last = this@ServerManager.pairingRateLimit[remoteIp] ?: 0L
                        if (now - last < 10000) {
                            call.respond(HttpStatusCode.TooManyRequests, "Rate limit exceeded")
                            return@get
                        }
                        this@ServerManager.pairingRateLimit[remoteIp] = now

                        val pairingId = UUID.randomUUID().toString()
                        this@ServerManager.pendingPairings[pairingId] = false
                        this@ServerManager.addLog("Pairing", "New session: $pairingId")
                        call.respond(PairingNewResponse(pairingId))
                    }

                    get("/pairing/qr/{id}") {
                        val id = call.parameters["id"]
                        if (id != null && this@ServerManager.pendingPairings.containsKey(id)) {
                            val stream = ByteArrayOutputStream()
                            this@ServerManager.generateQrBitmap(id).compress(Bitmap.CompressFormat.PNG, 100, stream)
                            this@ServerManager.addLog("QR", "QR served for $id")
                            call.respondBytes(stream.toByteArray(), ContentType.Image.PNG)
                        } else {
                            this@ServerManager.addLog("QR", "QR NOT FOUND: $id")
                            call.respond(HttpStatusCode.NotFound)
                        }
                    }

                    get("/pairing/status/{id}") {
                        val id = call.parameters["id"]?.trim()?.lowercase() ?: ""
                        if (this@ServerManager.pendingPairings.containsKey(id)) {
                            if (this@ServerManager.pendingPairings[id] == true) {
                                val token = JWT.create()
                                    .withAudience(this@ServerManager.jwtAudience)
                                    .withIssuer(this@ServerManager.jwtIssuer)
                                    .withExpiresAt(Date(System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000))
                                    .sign(Algorithm.HMAC256(this@ServerManager.jwtSecret))
                                
                                call.respond(PairingStatusResponse(authorized = true, token = token, echo_id = id))
                                this@ServerManager.addLog("Auth", "Pairing Successful")
                                this@ServerManager.serverScope.launch { delay(30000); this@ServerManager.pendingPairings.remove(id) }
                            } else {
                                call.respond(PairingStatusResponse(authorized = false, echo_id = id))
                            }
                        } else {
                            call.respond(HttpStatusCode.NotFound)
                        }
                    }

                    authenticate("auth-jwt") {
                        intercept(ApplicationCallPipeline.Plugins) {
                            val remoteIp = call.request.local.remoteHost
                            this@ServerManager._activeClients.value = this@ServerManager._activeClients.value.toMutableMap().apply {
                                put(remoteIp, ClientConnection(remoteIp, System.currentTimeMillis(), call.request.headers["User-Agent"] ?: "Browser"))
                            }
                        }
                        route("/api") {
                            get("/accounts") {
                                try {
                                    val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                                    val data = this@ServerManager.repository.getAccountBalances(dateStr).first()
                                    val minorHeads = this@ServerManager.repository.getAllMinorHeads().first()
                                    val accountsRaw = this@ServerManager.repository.getAllAccounts().first()
                                    
                                    call.respond(data.map { acc ->
                                        val minor = minorHeads.find { it.id == acc.minorHeadId }
                                        val majorId = minor?.majorHeadId
                                        val accountRaw = accountsRaw.find { it.id == acc.id }
                                        AccountDto(acc.id, acc.name, acc.type, acc.balance.toDouble() / 100.0, majorId, acc.minorHeadId, accountRaw?.openingBalance ?: 0.0)
                                    })
                                } catch (e: Exception) {
                                    Log.e("ServerManager", "Error fetching accounts", e)
                                    call.respond(HttpStatusCode.InternalServerError, "DB Error: ${e.message}")
                                }
                            }

                            get("/major_heads") {
                                try {
                                    val data = this@ServerManager.repository.getAllMajorHeads().first()
                                    call.respond(data.map { MajorHeadDto(it.id, it.name) })
                                } catch (e: Exception) {
                                    call.respond(HttpStatusCode.InternalServerError, "DB Error")
                                }
                            }

                            get("/minor_heads") {
                                try {
                                    val data = this@ServerManager.repository.getAllMinorHeads().first()
                                    call.respond(data.map { MinorHeadDto(it.id, it.name, it.majorHeadId) })
                                } catch (e: Exception) {
                                    call.respond(HttpStatusCode.InternalServerError, "DB Error")
                                }
                            }

                            get("/categories") {
                                try {
                                    val categories = this@ServerManager.repository.getAllCategories().first()
                                    call.respond(categories.map { CategoryDto(it.id, it.name, it.type) })
                                } catch (e: Exception) {
                                    call.respond(HttpStatusCode.InternalServerError, "DB Error")
                                }
                            }

                            get("/tags") {
                                try {
                                    val tags = this@ServerManager.repository.getAllTags().first()
                                    call.respond(tags.map { TagDto(it.id, it.name, it.isEnabled) })
                                } catch (e: Exception) {
                                    call.respond(HttpStatusCode.InternalServerError, "DB Error")
                                }
                            }

                            get("/subscription_statuses") {
                                try {
                                    val data = this@ServerManager.repository.getAllSubscriptionStatuses().first()
                                    call.respond(data)
                                } catch (e: Exception) {
                                    call.respond(HttpStatusCode.InternalServerError, "DB Error")
                                }
                            }
                            
                            get("/parties") {
                                try {
                                    val parties = this@ServerManager.repository.getAllParties().first()
                                    call.respond(parties.map { TagDto(it.id, it.name, it.isEnabled) })
                                } catch (e: Exception) {
                                    call.respond(HttpStatusCode.InternalServerError, "DB Error")
                                }
                            }
                            
                            get("/transactions") {
                                val transactions = this@ServerManager.repository.getAllTransactionsWithDetails().first()
                                call.respond(transactions.map { this@ServerManager.toDto(it) })
                            }

                            get("/notes") {
                                val notes = this@ServerManager.repository.getAllNotes().first()
                                call.respond(notes.map { NoteDto(it.id, it.title, it.content, it.type, it.notebookId, it.createdAt, it.tags, it.editedAt) })
                            }

                            get("/notebooks") {
                                val notebooks = this@ServerManager.repository.getAllNotebooks().first()
                                call.respond(notebooks.map { NotebookDto(it.id, it.name, it.createdAt) })
                            }

                            post("/notebooks") {
                                try {
                                    val dto = call.receive<NotebookDto>()
                                    this@ServerManager.repository.upsertNotebook(Notebook(dto.id, dto.name, dto.createdAt))
                                    call.respond(HttpStatusCode.Created, "Saved")
                                } catch (e: Exception) {
                                    call.respond(HttpStatusCode.InternalServerError, "Save Fail")
                                }
                            }

                            post("/notes") {
                                try {
                                    val dto = call.receive<NoteDto>()
                                    this@ServerManager.repository.upsertNote(Note(
                                        id = dto.id,
                                        title = dto.title,
                                        content = dto.content,
                                        type = dto.type,
                                        notebookId = dto.notebookId,
                                        createdAt = dto.createdAt,
                                        tags = dto.tags,
                                        editedAt = dto.editedAt ?: System.currentTimeMillis(),
                                        isDeleted = false
                                    ))
                                    call.respond(HttpStatusCode.Created, "Saved")
                                } catch (e: Exception) {
                                    call.respond(HttpStatusCode.InternalServerError, "Save Fail")
                                }
                            }

                            delete("/notes/{id}") {
                                val id = call.parameters["id"]?.toIntOrNull()
                                if (id != null) {
                                    val notes = this@ServerManager.repository.getAllNotes().first()
                                    val note = notes.find { it.id == id }
                                    if (note != null) {
                                        this@ServerManager.repository.deleteNote(note)
                                        call.respond(HttpStatusCode.OK)
                                    } else {
                                        call.respond(HttpStatusCode.NotFound)
                                    }
                                } else {
                                    call.respond(HttpStatusCode.BadRequest)
                                }
                            }
                            
                            post("/transactions") {
                                try {
                                    val dto = call.receive<TransactionDto>()
                                    
                                    val type = when {
                                        dto.toAccountId != null -> "transfer"
                                        dto.categoryId != null -> {
                                            val cat = this@ServerManager.repository.getAllCategories().first().find { it.id == dto.categoryId }
                                            cat?.type ?: "expense"
                                        }
                                        else -> "expense"
                                    }

                                    val prefix = when (type) {
                                        "income" -> "INC"
                                        "expense" -> "EXP"
                                        "transfer" -> "TNF"
                                        else -> "TXN"
                                    }

                                    this@ServerManager.dbWriteMutex.withLock {
                                        val lastNum = this@ServerManager.repository.getLastTransactionNumber(prefix)
                                        val nextSerial = if (lastNum != null) {
                                            val parts = lastNum.split("/")
                                            val lastSerial = parts.last().toIntOrNull() ?: 99999
                                            lastSerial + 1
                                        } else {
                                            100000
                                        }
                                        
                                        val year = try { LocalDate.parse(dto.date).year } catch(e: Exception) { LocalDate.now().year }
                                        val txnNumber = "$prefix/$year/$nextSerial"

                                        this@ServerManager.repository.insertTransactionLegacy(this@ServerManager.toEntity(dto).copy(transactionNumber = txnNumber))
                                    }
                                    
                                    withContext(Dispatchers.Main) {
                                        this@ServerManager.onDatabaseChange?.invoke()
                                    }
                                    
                                    call.respond(HttpStatusCode.Created, "Saved")
                                } catch (e: Exception) {
                                    Log.e("ServerManager", "Save fail", e)
                                    call.respond(HttpStatusCode.InternalServerError, "Save Fail: ${e.message}")
                                }
                            }
                        }
                    }
                }
            }

            serverScope.launch {
                try {
                    server?.start(wait = true)
                } catch (e: Exception) {
                    Log.e("ServerManager", "Server failed to start", e)
                    withContext(Dispatchers.Main) {
                        _isRunning.value = false
                        _serverError.value = "Start failed: ${e.localizedMessage}"
                        hideNotification()
                    }
                }
            }
            _isRunning.value = true
            showPersistentNotification()
        } catch (e: Exception) {
            _serverError.value = "Init failed: ${e.localizedMessage}"
            _isRunning.value = false
        }
    }

    private fun showPersistentNotification() {
        val channelId = "server_status"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Server Status", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }

        val stopIntent = Intent(context, ServerActionReceiver::class.java).apply {
            action = "STOP_SERVER"
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            context, 0, stopIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("FinTrack Server Online")
            .setContentText("Access from: ${_httpUrl.value}")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Server", stopPendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun hideNotification() {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun addLog(client: String, reason: String) {
        _terminationLogs.value = (listOf(TerminationLog(System.currentTimeMillis(), client, reason)) + _terminationLogs.value).take(15)
    }

    fun authorizePairing(pairingId: String): Boolean {
        val id = pairingId.trim().lowercase().removePrefix("fintrack:")
        if (pendingPairings.containsKey(id)) {
            pendingPairings[id] = true
            return true
        }
        return false
    }

    fun disconnectClient(ip: String) {
        _activeClients.value = _activeClients.value.toMutableMap().apply { remove(ip) }
    }

    fun stopServer() {
        if (_isStopping.value) return
        _isStopping.value = true
        serverScope.launch {
            try {
                server?.stop(500, 1000)
                server = null
            } catch (e: Exception) {
                Log.e("ServerManager", "Error stopping server", e)
            }
            serverJob?.cancel()
            serverJob = null
            _isRunning.value = false
            _httpUrl.value = null
            _activeClients.value = emptyMap()
            pendingPairings.clear()
            hideNotification()
            _isStopping.value = false
        }
    }

    fun restartServer(httpPort: Int = 8080, httpsPort: Int = 8443) {
        serverScope.launch {
            if (_isRunning.value || _isStopping.value) {
                _isStopping.value = true
                try {
                    server?.stop(500, 1000)
                    server = null
                } catch (e: Exception) {
                    Log.e("ServerManager", "Error stopping server", e)
                }
                serverJob?.cancel()
                serverJob = null
                _isRunning.value = false
                _httpUrl.value = null
                _activeClients.value = emptyMap()
                pendingPairings.clear()
                hideNotification()
                _isStopping.value = false
            }
            startServer(httpPort, httpsPort)
        }
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                for (addr in intf.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) return addr.hostAddress
                }
            }
        } catch (ex: Exception) {}
        return null
    }

    private fun generateQrBitmap(content: String): Bitmap {
        return BarcodeEncoder().encodeBitmap(content, BarcodeFormat.QR_CODE, 400, 400)
    }

    private fun toDto(detail: TransactionWithDetails) = TransactionDto(
        id = detail.transaction.id,
        date = detail.transaction.date,
        time = detail.transaction.time,
        accountId = detail.transaction.accountId,
        toAccountId = detail.transaction.toAccountId,
        categoryId = detail.transaction.categoryId,
        amount = detail.transaction.amount,
        note = detail.transaction.note,
        tags = detail.transaction.tags,
        transactionNumber = detail.transaction.transactionNumber,
        partyId = detail.transaction.partyId,
        toPartyId = detail.transaction.toPartyId,
        subName = detail.transaction.subName,
        subFrequency = detail.transaction.subFrequency,
        categoryName = detail.categoryName,
        categoryType = detail.categoryType,
        accountName = detail.accountName,
        toAccountName = detail.toAccountName,
        partyName = detail.partyName,
        toPartyName = detail.toPartyName,
        isReconciled = detail.transaction.isReconciled
    )

    private fun toEntity(dto: TransactionDto) = TransactionLegacy(
        id = dto.id,
        date = dto.date,
        time = dto.time,
        accountId = dto.accountId,
        toAccountId = dto.toAccountId,
        categoryId = if (dto.toAccountId != null) null else dto.categoryId,
        amount = dto.amount,
        note = dto.note,
        tags = dto.tags,
        transactionNumber = dto.transactionNumber,
        partyId = dto.partyId,
        toPartyId = dto.toPartyId,
        subName = dto.subName,
        subFrequency = dto.subFrequency,
        isReconciled = dto.isReconciled
    )

    private fun ensureJksProvider() {
        if (java.security.Security.getProvider("JKSProvider") != null) return
        try {
            val bcProvider = java.security.Security.getProvider("BC")
            if (bcProvider != null) {
                val provider = object : java.security.Provider("JKSProvider", 1.0, "JKS Provider") {}
                val pkcs12Class = bcProvider.get("KeyStore.PKCS12") ?: "com.android.org.bouncycastle.jcajce.provider.keystore.pkcs12.PKCS12KeyStoreSpi\$BC"
                provider.put("KeyStore.JKS", pkcs12Class)
                java.security.Security.addProvider(provider)
            }
        } catch (e: Exception) {
            Log.e("ServerManager", "Failed to register JKSProvider", e)
        }
    }

    private fun getWebClientHtml(): String {
        return try {
            context.assets.open("web_client.html").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            "<html><body><h1>Error loading web client</h1><p>${e.message}</p></body></html>"
        }
    }

    companion object {
        @Volatile private var INSTANCE: ServerManager? = null
        fun getInstance(context: Context, repository: FinanceRepository): ServerManager {
            return INSTANCE ?: synchronized(this) { INSTANCE ?: ServerManager(context.applicationContext, repository).also { INSTANCE = it } }
        }
    }
}
