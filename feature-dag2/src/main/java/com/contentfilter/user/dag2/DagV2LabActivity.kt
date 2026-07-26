package com.contentfilter.user.dag2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class DagV2LabActivity : ComponentActivity() {
    private val webViewHost = DagV2WebViewHost<android.webkit.WebView>()

    @Inject
    lateinit var coordinator: DagV2BrowserCoordinator

    @Inject
    lateinit var metrics: DagV2Metrics

    @Inject
    lateinit var serviceWorkerRouter: DagV2ServiceWorkerRouter

    @Inject
    lateinit var resourceInterceptor: DagV2ResourceInterceptor

    @Inject
    lateinit var pageAnalyzer: DagV2PageAnalyzer

    @Inject
    lateinit var webViewLifecycle: DagV2WebViewLifecycle

    @Inject
    lateinit var lifecycleGate: DagV2LabLifecycleGate

    @Inject
    lateinit var calibrationController: DagV2CalibrationController

    private var lifecycleGeneration = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleGeneration = lifecycleGate.acquire()
        coordinator.closeSession()
        calibrationController.resetLabSession()
        serviceWorkerRouter.setNoCacheMode(false)
        enableEdgeToEdge()
        serviceWorkerRouter.install()
        setContent {
            MaterialTheme {
                DagV2LabScreen(
                    coordinator = coordinator,
                    metrics = metrics,
                    resourceInterceptor = resourceInterceptor,
                    serviceWorkerRouter = serviceWorkerRouter,
                    pageAnalyzer = pageAnalyzer,
                    webViewLifecycle = webViewLifecycle,
                    webViewHost = webViewHost,
                    calibrationController = calibrationController,
                )
            }
        }
    }

    override fun onDestroy() {
        calibrationController.closeLab()
        webViewHost.close()
        if (lifecycleGate.release(lifecycleGeneration)) {
            coordinator.closeSession()
        }
        super.onDestroy()
    }
}
