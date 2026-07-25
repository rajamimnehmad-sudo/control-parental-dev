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
    @Inject
    lateinit var coordinator: DagV2BrowserCoordinator

    @Inject
    lateinit var metrics: DagV2Metrics

    @Inject
    lateinit var serviceWorkerRouter: DagV2ServiceWorkerRouter

    @Inject
    lateinit var resourceRouter: DagV2ResourceRouter

    @Inject
    lateinit var pageAnalyzer: DagV2PageAnalyzer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        serviceWorkerRouter.install()
        setContent {
            MaterialTheme {
                DagV2LabScreen(
                    coordinator = coordinator,
                    metrics = metrics,
                    resourceRouter = resourceRouter,
                    serviceWorkerRouter = serviceWorkerRouter,
                    pageAnalyzer = pageAnalyzer,
                )
            }
        }
    }

    override fun onDestroy() {
        coordinator.closeSession()
        serviceWorkerRouter.uninstall()
        resourceRouter.close()
        super.onDestroy()
    }
}
