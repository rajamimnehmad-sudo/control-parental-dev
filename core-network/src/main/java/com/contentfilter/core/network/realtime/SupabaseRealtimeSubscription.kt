package com.contentfilter.core.network.realtime

import com.contentfilter.core.network.config.AuthTokenProvider
import com.contentfilter.core.network.config.DeviceTokenProvider
import com.contentfilter.core.network.config.SupabaseConfigProvider
import com.contentfilter.core.network.remote.SupabaseTable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseRealtimeSubscription
    @Inject
    constructor(
        private val configProvider: SupabaseConfigProvider,
        private val authTokenProvider: AuthTokenProvider,
        private val deviceTokenProvider: DeviceTokenProvider,
        private val httpClient: OkHttpClient,
    ) : RealtimeSubscription {
        private val changes = MutableSharedFlow<RealtimeChange>(extraBufferCapacity = 32)
        private val scope = CoroutineScope(Dispatchers.IO)
        private var webSocket: WebSocket? = null
        private var heartbeatJob: Job? = null
        private var desiredDeviceId: String? = null
        private var shouldReconnect = false
        private var reconnectScheduled = false

        override fun observeChanges(): Flow<RealtimeChange> = changes.asSharedFlow()

        override fun connect(deviceId: String?) {
            desiredDeviceId = deviceId
            shouldReconnect = true
            if (webSocket != null) return
            scope.launch {
                openSocket()
            }
        }

        override fun disconnect() {
            shouldReconnect = false
            reconnectScheduled = false
            heartbeatJob?.cancel()
            heartbeatJob = null
            webSocket?.close(1000, "Closing realtime subscription.")
            webSocket = null
        }

        private suspend fun openSocket() {
            if (!shouldReconnect || webSocket != null) return
            val config = configProvider.current()
            val baseUrl = config.normalizedUrlOrNull()
            val token = authTokenProvider.currentToken()
            val deviceToken = deviceTokenProvider.currentDeviceToken()
            if (baseUrl == null || (token == null && deviceToken == null) || webSocket != null) return
            val realtimeUrl =
                baseUrl
                    .replace("https://", "wss://")
                    .replace("http://", "ws://")
            val accessToken = token ?: config.anonKey
            val requestBuilder =
                Request.Builder()
                    .url(
                        "$realtimeUrl/realtime/v1/websocket?apikey=${config.anonKey}&access_token=$accessToken&vsn=1.0.0",
                    )
                    .header("apikey", config.anonKey)
                    .header("Authorization", "Bearer $accessToken")
            deviceToken?.let { requestBuilder.header("x-device-token", it) }
            val listener = listener(includePostgresChanges = token != null, deviceId = desiredDeviceId)
            webSocket = httpClient.newWebSocket(requestBuilder.build(), listener)
        }

        private fun listener(
            includePostgresChanges: Boolean,
            deviceId: String?,
        ): WebSocketListener =
            object : WebSocketListener() {
                override fun onOpen(
                    webSocket: WebSocket,
                    response: Response,
                ) {
                    if (includePostgresChanges) {
                        RealtimeTables.forEach { table ->
                            webSocket.send(subscriptionMessage(table))
                        }
                    }
                    deviceId?.let { webSocket.send(policySubscriptionMessage(it)) }
                    heartbeatJob?.cancel()
                    heartbeatJob =
                        scope.launch {
                            while (shouldReconnect) {
                                delay(HeartbeatIntervalMillis)
                                if (!webSocket.send(heartbeatMessage())) break
                            }
                        }
                }

                override fun onMessage(
                    webSocket: WebSocket,
                    text: String,
                ) {
                    parseRealtimeChange(text, deviceId)?.let(changes::tryEmit)
                }

                override fun onClosed(
                    webSocket: WebSocket,
                    code: Int,
                    reason: String,
                ) {
                    handleSocketClosed(webSocket)
                }

                override fun onFailure(
                    webSocket: WebSocket,
                    t: Throwable,
                    response: Response?,
                ) {
                    handleSocketClosed(webSocket)
                }
            }

        private fun handleSocketClosed(closedSocket: WebSocket) {
            if (webSocket !== closedSocket) return
            webSocket = null
            heartbeatJob?.cancel()
            heartbeatJob = null
            scheduleReconnect()
        }

        private fun scheduleReconnect() {
            if (!shouldReconnect || reconnectScheduled) return
            reconnectScheduled = true
            scope.launch {
                delay(ReconnectDelayMillis)
                reconnectScheduled = false
                openSocket()
            }
        }

        private fun subscriptionMessage(table: SupabaseTable): String =
            """
            {
              "topic":"realtime:public:${table.tableName}",
              "event":"phx_join",
              "payload":{
                "config":{
                  "postgres_changes":[{"event":"*","schema":"public","table":"${table.tableName}"}]
                }
              },
              "ref":"${table.tableName}"
            }
            """.trimIndent()

        private fun policySubscriptionMessage(deviceId: String): String =
            JSONObject()
                .put("topic", "realtime:policy:$deviceId")
                .put("event", "phx_join")
                .put(
                    "payload",
                    JSONObject().put(
                        "config",
                        JSONObject()
                            .put("broadcast", JSONObject().put("ack", false).put("self", false))
                            .put("presence", JSONObject().put("key", ""))
                            .put("postgres_changes", org.json.JSONArray())
                            .put("private", false),
                    ),
                )
                .put("ref", "policy:$deviceId")
                .toString()

        private fun heartbeatMessage(): String =
            JSONObject()
                .put("topic", "phoenix")
                .put("event", "heartbeat")
                .put("payload", JSONObject())
                .put("ref", "heartbeat")
                .toString()

        private companion object {
            const val HeartbeatIntervalMillis = 25_000L
            const val ReconnectDelayMillis = 2_000L
            val RealtimeTables =
                listOf(
                    SupabaseTable.Devices,
                    SupabaseTable.Policies,
                    SupabaseTable.PolicyRules,
                    SupabaseTable.DailyLimits,
                    SupabaseTable.AccessRequests,
                    SupabaseTable.ExtraTimeGrants,
                )
        }
    }

internal fun parseRealtimeChange(
    text: String,
    expectedDeviceId: String?,
): RealtimeChange? {
    val json = runCatching { JSONObject(text) }.getOrNull() ?: return null
    val payload = json.optJSONObject("payload")
    if (json.optString("event") == "broadcast" && payload?.optString("event") == "policy_revision") {
        val revisionPayload = payload.optJSONObject("payload") ?: return null
        val deviceId = revisionPayload.optString("device_id")
        if (expectedDeviceId != null && deviceId != expectedDeviceId) return null
        val requestId = revisionPayload.optString("request_id")
        val policyId = revisionPayload.optString("policy_id")
        val revision = revisionPayload.optLong("revision", 0L)
        if (requestId.isBlank() || deviceId.isBlank() || policyId.isBlank() || revision <= 0L) return null
        return RealtimeChange.PolicyRevision(requestId, deviceId, policyId, revision)
    }
    val table = RealtimeTablesForParsing.firstOrNull { text.contains(it.tableName) } ?: return null
    return RealtimeChange.Table(table = table, payload = text)
}

private val RealtimeTablesForParsing =
    listOf(
        SupabaseTable.Devices,
        SupabaseTable.Policies,
        SupabaseTable.PolicyRules,
        SupabaseTable.DailyLimits,
        SupabaseTable.AccessRequests,
        SupabaseTable.ExtraTimeGrants,
    )
