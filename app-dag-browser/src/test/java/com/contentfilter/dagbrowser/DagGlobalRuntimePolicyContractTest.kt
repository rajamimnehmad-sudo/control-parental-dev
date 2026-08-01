package com.contentfilter.dagbrowser

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse

class DagGlobalRuntimePolicyContractTest {
    @Test
    fun `runtime policy has no retailer or phone model exceptions`() {
        val runtimeRoots =
            listOf(
                File("src/main/java"),
                File("src/main/assets/dag-protection"),
            )
        val runtime =
            runtimeRoots
                .flatMap { root -> root.walkTopDown().filter(File::isFile).toList() }
                .joinToString("\n") { file -> file.readText() }
                .lowercase()

        listOf(
            "cheeky.com",
            "mimo.com",
            "fravega.com",
            "frávega.com",
            "sm-a235m",
            "sm_a235m",
            "sm-s908e",
        ).forEach { forbidden ->
            assertFalse(
                runtime.contains(forbidden),
                "DAG runtime must not contain a site or device-specific exception: $forbidden",
            )
        }
    }
}
