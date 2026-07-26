package com.contentfilter.user.dag2

import com.contentfilter.core.network.security.PublicDestinationDecision
import com.contentfilter.core.network.security.PublicNetworkDestinationGuard
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DagV2NetworkGuard
    @Inject
    constructor(
        private val destinationGuard: PublicNetworkDestinationGuard,
    ) {
        suspend fun validate(url: String): DagV2PolicyResult {
            val result = destinationGuard.validateNavigation(url)
            return if (result.decision == PublicDestinationDecision.Allow) {
                DagV2PolicyResult(DagV2SiteDecision.Allow, "Destino de red público.")
            } else {
                blocked("El destino no es HTTPS público o no pudo validarse de forma segura.")
            }
        }

        private fun blocked(reason: String) = DagV2PolicyResult(DagV2SiteDecision.Block, reason)
    }
