package com.contentfilter.feature.vpn.dns

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

internal class BoundedDnsRequestDispatcher<T>(
    scope: CoroutineScope,
    workerCount: Int,
    queueCapacity: Int,
    private val handler: suspend (T) -> Unit,
    private val onFailure: suspend (Throwable) -> Unit,
) {
    private val queue: Channel<T>
    private val workers: List<Job>

    init {
        require(workerCount > 0)
        require(queueCapacity > 0)
        queue = Channel(queueCapacity)
        workers =
            List(workerCount) {
                scope.launch {
                    for (request in queue) {
                        try {
                            handler(request)
                        } catch (exception: CancellationException) {
                            throw exception
                        } catch (exception: Throwable) {
                            runCatching { onFailure(exception) }
                        }
                    }
                }
            }
    }

    suspend fun submit(request: T) {
        queue.send(request)
    }

    fun cancel() {
        queue.cancel()
        workers.forEach(Job::cancel)
    }
}
