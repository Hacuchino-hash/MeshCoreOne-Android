// PortedFrom: MC1Tests/Extensions/ErrorDispatchCoverageTests.swift@db14559b39d32322b06477c6ae676112f583db50
// Copy coverage is asserted independently; missing concrete producer dispatch remains an acceptance blocker.
package com.meshcoreone.android.core.ui

import java.io.File
import kotlin.test.*
import org.junit.Test

class ErrorDispatchCoverageTest : SourceCaseProof() {
    @ProducerBindingPending("WP-205-206-207-208-209-210-211-214")
    @OriginalCase("ErrorDispatchCoverageTests::Every LocalizedError enum in MC1Services and MeshCore has a dispatch arm or is allowlisted()")
    @Test fun everyOriginalLocalizedErrorHasCopyAccountingOrTheOriginalControlFlowAllowlist() = prove {
        val root = File(requireNotNull(System.getProperty("repositoryDirectory")))
        val patterns = listOf(
            Regex("(?m)^\\s*(?:public\\s+)?enum\\s+(\\w+)\\s*:[^{]*\\bLocalizedError\\b"),
            Regex("(?m)^\\s*extension\\s+(\\w+)\\s*:[^{]*\\bLocalizedError\\b"),
        )
        val discovered = linkedSetOf<String>()
        for (directory in listOf("MC1Services/Sources", "MeshCore/Sources")) {
            val files = File(root, directory).walkTopDown().filter { it.isFile && it.extension == "swift" }.toList()
            assertTrue(files.isNotEmpty())
            for (file in files) for (pattern in patterns) {
                discovered += pattern.findAll(file.readText()).map { it.groupValues[1] }.toList()
            }
        }
        val allowlist = setOf("PairingError", "DevicePairingError")
        assertTrue(allowlist.all { it in discovered })
        val removedBilling = setOf("StoreServiceError")
        assertTrue((discovered - ErrorCopy.sourceFamilies - allowlist - removedBilling).isEmpty(),
            (discovered - ErrorCopy.sourceFamilies - allowlist - removedBilling).toString())
        assertTrue(discovered.isNotEmpty())
    }
}
