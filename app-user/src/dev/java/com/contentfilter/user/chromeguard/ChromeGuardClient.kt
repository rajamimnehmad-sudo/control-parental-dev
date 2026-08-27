package com.contentfilter.user.chromeguard

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.os.RemoteException
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicLong

internal data class ChromeGuardClientSession(
    val protectionGeneration: Long,
    val sessionId: String,
    val mainProcessNonce: String,
    val bootMarker: Long,
    val bootstrapGeneration: Int,
    var heartbeatSequence: Long = 0L,
)

internal class ChromeGuardClient(
    context: Context,
    private val onGuardDisconnected: () -> Unit,
) {
    private val appContext = context.applicationContext
    private val requestIds = AtomicLong()
    private val replyMessenger = Messenger(ReplyHandler())
    private var remote: Messenger? = null
    private var connectionWaiter = CompletableDeferred<Messenger>()
    private var sessionWaiter: CompletableDeferred<Long>? = null
    private var sessionRequestId = 0L
    private var bound = false
    private val connection = GuardConnection()

    suspend fun openSession(
        sessionId: String,
        mainProcessNonce: String,
        bootstrapGeneration: Int,
    ): ChromeGuardClientSession {
        val messenger = connect()
        val requestId = requestIds.incrementAndGet()
        val request =
            ChromeGuardSessionRequest(
                sessionId = sessionId,
                mainProcessNonce = mainProcessNonce,
                bootMarker = ChromeGuardBootMarker.current(appContext),
                bootstrapGeneration = bootstrapGeneration,
            )
        val waiter = CompletableDeferred<Long>()
        sessionRequestId = requestId
        sessionWaiter = waiter
        val message = Message.obtain(null, ChromeGuardContract.MessageBeginSession)
        message.replyTo = replyMessenger
        message.data = ChromeGuardBundleCodec.session(request).apply {
            putLong(ChromeGuardContract.KeyRequestId, requestId)
            putInt(ChromeGuardService.KeyMainPid, Process.myPid())
        }
        messenger.send(message)
        val generation = withTimeout(ChromeGuardContract.IpcTimeoutMillis) { waiter.await() }
        check(generation > 0L) { "chrome_guard_session_rejected" }
        return ChromeGuardClientSession(
            protectionGeneration = generation,
            sessionId = sessionId,
            mainProcessNonce = mainProcessNonce,
            bootMarker = request.bootMarker,
            bootstrapGeneration = bootstrapGeneration,
        )
    }

    fun publishHeartbeat(
        session: ChromeGuardClientSession,
        health: ChromeGuardHealth,
    ): Boolean {
        val messenger = remote ?: return false
        val now = SystemClock.elapsedRealtime()
        val sequence = ++session.heartbeatSequence
        val lease =
            ChromeGuardLease(
                protectionGeneration = session.protectionGeneration,
                sessionId = session.sessionId,
                mainProcessNonce = session.mainProcessNonce,
                bootMarker = session.bootMarker,
                heartbeatSequence = sequence,
                issuedAtElapsedRealtime = now,
                expiresAtElapsedRealtime = now + ChromeGuardContract.LeaseTtlMillis,
                transportGeneration = session.protectionGeneration,
                proxyGeneration = session.protectionGeneration,
                bootstrapGeneration = session.bootstrapGeneration,
                health = health,
            )
        return send(ChromeGuardContract.MessageHeartbeat, ChromeGuardBundleCodec.lease(lease))
    }

    fun revoke(reason: String): Boolean =
        send(
            ChromeGuardContract.MessageRevoke,
            android.os.Bundle().apply { putString(ChromeGuardContract.KeyReason, reason) },
        )

    fun requestStatus(): Boolean = send(ChromeGuardContract.MessageStatus)

    fun devKillGuard(): Boolean = send(ChromeGuardContract.MessageDevKillGuard)

    fun stopGuard(reason: String): Boolean =
        send(
            ChromeGuardContract.MessageStop,
            android.os.Bundle().apply { putString(ChromeGuardContract.KeyReason, reason) },
        )

    fun disconnect() {
        if (bound) runCatching { appContext.unbindService(connection) }
        bound = false
        remote = null
        connectionWaiter = CompletableDeferred()
    }

    private suspend fun connect(): Messenger {
        remote?.let { return it }
        ContextCompat.startForegroundService(
            appContext,
            Intent(appContext, ChromeGuardService::class.java).setAction(ChromeGuardService.ActionStart),
        )
        if (!bound) {
            bound = appContext.bindService(Intent(appContext, ChromeGuardService::class.java), connection, Context.BIND_AUTO_CREATE)
            check(bound) { "chrome_guard_bind_failed" }
        }
        return withTimeout(ChromeGuardContract.IpcTimeoutMillis) { connectionWaiter.await() }
    }

    private fun send(
        what: Int,
        data: android.os.Bundle = android.os.Bundle.EMPTY,
    ): Boolean {
        val message = Message.obtain(null, what)
        message.data = data
        return try {
            remote?.send(message) ?: return false
            true
        } catch (_: RemoteException) {
            remote = null
            onGuardDisconnected()
            false
        }
    }

    private inner class GuardConnection : ServiceConnection {
        override fun onServiceConnected(
            name: ComponentName,
            binder: IBinder,
        ) {
            val connected = Messenger(binder)
            remote = connected
            connectionWaiter.complete(connected)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            // Android keeps the binding registered across an unexpected remote-process death and
            // reconnects this same ServiceConnection when the service returns.
            disconnected(bindingStillRegistered = true)
        }

        override fun onBindingDied(name: ComponentName) {
            if (bound) runCatching { appContext.unbindService(this) }
            disconnected(bindingStillRegistered = false)
        }

        private fun disconnected(bindingStillRegistered: Boolean) {
            remote = null
            bound = bindingStillRegistered
            connectionWaiter = CompletableDeferred()
            onGuardDisconnected()
        }
    }

    private inner class ReplyHandler : Handler(Looper.getMainLooper()) {
        override fun handleMessage(message: Message) {
            val requestId = message.data.getLong(ChromeGuardContract.KeyRequestId)
            if (requestId != sessionRequestId) return
            when (message.what) {
                ChromeGuardContract.MessageSessionOpened ->
                    sessionWaiter?.complete(message.data.getLong(ChromeGuardContract.KeyProtectionGeneration))
                ChromeGuardContract.MessageRejected -> sessionWaiter?.complete(0L)
                else -> super.handleMessage(message)
            }
        }
    }
}
