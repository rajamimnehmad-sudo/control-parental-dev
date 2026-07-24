package com.contentfilter.user.dag2

import android.os.Build
import android.webkit.ServiceWorkerClient
import android.webkit.ServiceWorkerController
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DagV2ServiceWorkerRouter
    @Inject
    constructor(
        private val resourceRouter: DagV2ResourceRouter,
    ) {
        private var installed = false

        fun install() {
            if (installed || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
            ServiceWorkerController
                .getInstance()
                .also { controller ->
                    controller.serviceWorkerWebSettings.apply {
                        allowContentAccess = false
                        allowFileAccess = false
                        blockNetworkLoads = false
                        cacheMode = WebSettings.LOAD_DEFAULT
                    }
                }.setServiceWorkerClient(
                    object : ServiceWorkerClient() {
                        override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? =
                            resourceRouter.intercept(request, DagV2ResourceSource.ServiceWorker)
                    },
                )
            installed = true
        }

        fun uninstall() {
            if (!installed || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
            ServiceWorkerController.getInstance().setServiceWorkerClient(null)
            installed = false
        }
    }
