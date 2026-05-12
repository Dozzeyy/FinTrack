/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Bhuvan
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
import io.ktor.http.*
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
    val categoryName: String? = null, val accountName: String? = null, val toAccountName: String? = null,
    val partyName: String? = null, val toPartyName: String? = null
)

@Serializable
data class CategoryDto(val id: Int, val name: String, val type: String)

@Serializable
data class AccountDto(val id: Int, val name: String, val type: String, val balance: Double)

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
data class PairingNewResponse(val id: String)

@Serializable
data class PairingStatusResponse(
    val authorized: Boolean,
    val token: String? = null,
    val echo_id: String? = null,
    val error: String? = null
)

data class TerminationLog(val timestamp: Long, val client: String, val reason: String)

class ServerManager(private val context: Context, private val dao: ExpenseDao) {

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

    private var server: NettyApplicationEngine? = null
    private val serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val dbWriteMutex = Mutex()

    private val jwtSecret = UUID.randomUUID().toString()
    private val jwtIssuer = "fintrack-server"
    private val jwtAudience = "fintrack-client"
    private val jwtRealm = "fintrack"

    private val pendingPairings = ConcurrentHashMap<String, Boolean>()

    var onDatabaseChange: (() -> Unit)? = null

    private val NOTIFICATION_ID = 888

    fun startServer(httpPort: Int = 8080) {
        if (_isRunning.value) return
        _serverError.value = null

        val ip = getLocalIpAddress() ?: "127.0.0.1"
        _httpUrl.value = "http://$ip:$httpPort"

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
                        call.respondText(getWebClientHtml(), ContentType.Text.Html)
                    }

                    get("/pairing/new") {
                        val pairingId = UUID.randomUUID().toString()
                        pendingPairings[pairingId] = false
                        call.respond(PairingNewResponse(pairingId))
                    }

                    get("/pairing/qr/{id}") {
                        val id = call.parameters["id"]
                        if (id != null && pendingPairings.containsKey(id)) {
                            val stream = ByteArrayOutputStream()
                            generateQrBitmap(id).compress(Bitmap.CompressFormat.PNG, 100, stream)
                            call.respondBytes(stream.toByteArray(), ContentType.Image.PNG)
                        } else {
                            call.respond(HttpStatusCode.NotFound)
                        }
                    }

                    get("/pairing/status/{id}") {
                        val id = call.parameters["id"]?.trim()?.lowercase() ?: ""
                        if (pendingPairings.containsKey(id)) {
                            if (pendingPairings[id] == true) {
                                val token = JWT.create()
                                    .withAudience(jwtAudience)
                                    .withIssuer(jwtIssuer)
                                    .withExpiresAt(Date(System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000))
                                    .sign(Algorithm.HMAC256(jwtSecret))
                                
                                call.respond(PairingStatusResponse(authorized = true, token = token, echo_id = id))
                                addLog("Auth", "Pairing Successful")
                                serverScope.launch { delay(30000); pendingPairings.remove(id) }
                            } else {
                                call.respond(PairingStatusResponse(authorized = false, echo_id = id))
                            }
                        } else {
                            call.respond(HttpStatusCode.NotFound)
                        }
                    }

                    authenticate("auth-jwt") {
                        intercept(ApplicationCallPipeline.Plugins) {
                            val ip = call.request.local.remoteHost
                            _activeClients.value = _activeClients.value.toMutableMap().apply {
                                put(ip, ClientConnection(ip, System.currentTimeMillis(), call.request.headers["User-Agent"] ?: "Browser"))
                            }
                        }
                        route("/api") {
                            get("/accounts") {
                                try {
                                    val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                                    val data = dao.getAccountBalances(dateStr).first()
                                    call.respond(data.map { AccountDto(it.id, it.name, it.type, it.balance) })
                                } catch (e: Exception) {
                                    Log.e("ServerManager", "Error fetching accounts", e)
                                    call.respond(HttpStatusCode.InternalServerError, "DB Error: ${e.message}")
                                }
                            }

                            get("/categories") {
                                try {
                                    val categories = dao.getAllCategories().first()
                                    call.respond(categories.map { CategoryDto(it.id, it.name, it.type) })
                                } catch (e: Exception) {
                                    call.respond(HttpStatusCode.InternalServerError, "DB Error")
                                }
                            }

                            get("/tags") {
                                try {
                                    val tags = dao.getAllTags().first()
                                    call.respond(tags.map { TagDto(it.id, it.name, it.isEnabled) })
                                } catch (e: Exception) {
                                    call.respond(HttpStatusCode.InternalServerError, "DB Error")
                                }
                            }
                            
                            get("/parties") {
                                try {
                                    val parties = dao.getAllParties().first()
                                    call.respond(parties.map { TagDto(it.id, it.name, it.isEnabled) }) // Re-using TagDto for Party too
                                } catch (e: Exception) {
                                    call.respond(HttpStatusCode.InternalServerError, "DB Error")
                                }
                            }
                            
                            get("/transactions") {
                                val transactions = dao.getAllTransactionsWithDetails().first()
                                call.respond(transactions.map { it.toDto() })
                            }
                            
                            post("/transactions") {
                                try {
                                    val dto = call.receive<TransactionDto>()
                                    
                                    val type = when {
                                        dto.toAccountId != null -> "transfer"
                                        dto.categoryId != null -> {
                                            val cat = dao.getAllCategories().first().find { it.id == dto.categoryId }
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

                                    dbWriteMutex.withLock {
                                        val lastNum = dao.getLastTransactionNumber(prefix)
                                        val nextSerial = if (lastNum != null) {
                                            val parts = lastNum.split("/")
                                            val lastSerial = parts.last().toIntOrNull() ?: 99999
                                            lastSerial + 1
                                        } else {
                                            100000
                                        }
                                        
                                        val year = try { LocalDate.parse(dto.date).year } catch(e: Exception) { LocalDate.now().year }
                                        val txnNumber = "$prefix/$year/$nextSerial"

                                        dao.insertTransaction(dto.toEntity().copy(transactionNumber = txnNumber))
                                    }
                                    
                                    // Notify UI to refresh charts
                                    withContext(Dispatchers.Main) {
                                        onDatabaseChange?.invoke()
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
            _isRunning.value = false
            _httpUrl.value = null
            _activeClients.value = emptyMap()
            pendingPairings.clear()
            hideNotification()
            _isStopping.value = false
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

    private fun TransactionWithDetails.toDto() = TransactionDto(
        id = transaction.id,
        date = transaction.date,
        time = transaction.time,
        accountId = transaction.accountId,
        toAccountId = transaction.toAccountId,
        categoryId = transaction.categoryId,
        amount = transaction.amount,
        note = transaction.note,
        tags = transaction.tags,
        transactionNumber = transaction.transactionNumber,
        partyId = transaction.partyId,
        toPartyId = transaction.toPartyId,
        categoryName = categoryName,
        accountName = accountName,
        toAccountName = toAccountName,
        partyName = partyName,
        toPartyName = toPartyName
    )

    private fun TransactionDto.toEntity() = Transaction(
        id = id,
        date = date,
        time = time,
        accountId = accountId,
        toAccountId = toAccountId,
        categoryId = categoryId,
        amount = amount,
        note = note,
        tags = tags,
        transactionNumber = transactionNumber,
        partyId = partyId,
        toPartyId = toPartyId
    )

    private fun getWebClientHtml(): String {
        return try {
            context.assets.open("web_client.html").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            "<html><body><h1>Error loading web client</h1><p>${e.message}</p></body></html>"
        }
    }

    companion object {
        @Volatile private var INSTANCE: ServerManager? = null
        fun getInstance(context: Context, dao: ExpenseDao): ServerManager {
            return INSTANCE ?: synchronized(this) { INSTANCE ?: ServerManager(context.applicationContext, dao).also { INSTANCE = it } }
        }
    }
}
