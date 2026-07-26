package com.contentfilter.user.dag2

import com.contentfilter.user.dag.DagClassification
import com.contentfilter.user.dag.DagClassificationResult
import com.contentfilter.user.dag.DagContentClassifier
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DagV2CanonicalTextPolicyAdapter
    @Inject
    constructor(
        private val classifier: DagContentClassifier,
    ) : DagV2CanonicalTextPolicy {
        override fun classifyQuery(query: String): DagV2CanonicalTextResult =
            classifier.classifyQuery(query).toDagV2Result()

        override fun classifyNavigation(url: String): DagV2CanonicalTextResult {
            val direct = classifier.classifyDirectUrl(url)
            val routeAware =
                classifier.classifyResult(
                    title = url,
                    description = url,
                    url = url,
                )
            return listOf(direct, routeAware)
                .maxBy { it.dagV2Risk() }
                .toDagV2Result()
        }

        override fun classifyResult(result: DagV2SearchResult): DagV2CanonicalTextResult =
            classifier
                .classifyResult(
                    title = result.title,
                    description = result.description,
                    url = result.url,
                ).toDagV2Result()

        override fun classifyPage(
            url: String,
            title: String,
            visibleText: String,
        ): DagV2CanonicalTextResult = classifier.classifyPage(url, title, visibleText).toDagV2Result()

        private fun DagClassificationResult.toDagV2Result(): DagV2CanonicalTextResult =
            DagV2CanonicalTextResult(
                decision =
                    when (decision) {
                        DagClassification.Allowed -> DagV2CanonicalTextDecision.Allowed
                        DagClassification.Blocked -> DagV2CanonicalTextDecision.Blocked
                        DagClassification.Uncertain -> DagV2CanonicalTextDecision.Uncertain
                    },
                category = category,
            )

        private fun DagClassificationResult.dagV2Risk(): Int =
            when (decision) {
                DagClassification.Allowed -> 0
                DagClassification.Uncertain -> 1
                DagClassification.Blocked -> 2
            }
    }

@Module
@InstallIn(SingletonComponent::class)
abstract class DagV2CanonicalTextPolicyModule {
    @Binds
    abstract fun bindDagV2CanonicalTextPolicy(
        implementation: DagV2CanonicalTextPolicyAdapter,
    ): DagV2CanonicalTextPolicy
}
