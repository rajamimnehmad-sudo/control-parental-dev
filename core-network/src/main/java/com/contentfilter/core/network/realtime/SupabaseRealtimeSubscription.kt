package com.contentfilter.core.network.realtime

import com.contentfilter.core.network.config.AuthTokenProvider
import com.contentfilter.core.network.config.SupabaseConfigProvider
import com.contentfilter.core.network.remote.SupabaseTable
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

@Singleton
class SupabaseRealtimeSubscription
    @Inject
    constructor(
        private val configProvider: SupabaseConfigProvider,
        private val authTokenProvider: AuthTokenProvider,
        private val httpClient: OkHttpClient,
    ) : RealtimeSubscription {
        private val changes = MutableSharedFlow<RealtimeChange>(extraBufferCapacity = 32)
        private val scope = CoroutineScope(Dispatchers.IO)
        private var webSocket: WebSocket? = null

        override fun observeChanges(): Flow<RealtimeChange> = changes.asSharedFlow()

        override fun connect() {
            if (webSocket != null) return
            scope.launch {
                val config = configProvider.current()
                val baseUrl = config.normalizedUrlOrNull()
                val token = authTokenProvider.currentToken()
                if (baseUrl == null || token == null || webSocket != null) return@launch
                val realtimeUrl = baseUrl
                    .replace("https://", "wss://")
                    .replace("http://", "ws://")
                val request = Request.Builder()
                    .url("$realtimeUrl/realtime/v1/websocket?apikey=${config.anonKey}&access_token=$token&vsn=1.0.0")
                    .build()
                webSocket = httpClient.newWebSocket(request, listener())
            }
        }

        override fun disconnect() {
            webSocket?.close(1000, "Closing realtime subscription.")
            webSocket = null
        }

        private fun listener(): WebSocketListener =
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    RealtimeTables.forEach { table ->
                        webSocket.send(subscriptionMessage(table))
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val table = RealtimeTables.firstOrNull { text.contains(it.tableName) } ?: return
                    changes.tryEmit(RealtimeChange(table = table, payload = text))
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    this@SupabaseRealtimeSubscription.webSocket = null
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

        private companion object {
            val RealtimeTables = listOf(
                SupabaseTable.Devices,
                SupabaseTable.Policies,
                SupabaseTable.PolicyRules,
                SupabaseTable.DailyLimits,
                SupabaseTable.AccessRequests,
                SupabaseTable.ExtraTimeGrants,
            )
        }
    }
