// AndroidOnly: WP-002 Assertion-bearing discovery evidence must fail closed.
package com.meshcoreone.buildlogic

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.io.TempDir

class ScaffoldReportsTest {
    @TempDir
    lateinit var directory: File

    private fun report(tests: Int, cases: String, skipped: Int = 0, failures: Int = 0) {
        File(directory, "TEST-fixture.xml").writeText(
            """<testsuite tests="$tests" failures="$failures" errors="0" skipped="$skipped">$cases</testsuite>""",
        )
    }

    @Test
    fun `nonzero actual assertions produce evidence`() {
        report(2, """<testcase name="one"/><testcase name="two"/>""")
        assertEquals(TestEvidence(2, 2, 0, 0, 0), readTestEvidence(directory))
    }

    @Test
    fun `missing report fails`() {
        assertFailsWith<IllegalArgumentException> { readTestEvidence(directory) }
    }

    @Test
    fun `zero discovered tests fail`() {
        report(0, "")
        assertFailsWith<IllegalArgumentException> { readTestEvidence(directory) }
    }

    @Test
    fun `skipped required assertion fails`() {
        report(1, """<testcase name="skipped"><skipped/></testcase>""", skipped = 1)
        assertFailsWith<IllegalArgumentException> { readTestEvidence(directory) }
    }

    @Test
    fun `failed required assertion fails`() {
        report(1, """<testcase name="failed"><failure/></testcase>""", failures = 1)
        assertFailsWith<IllegalArgumentException> { readTestEvidence(directory) }
    }

    @Test
    fun `declared discovery cannot exceed actual cases`() {
        report(2, """<testcase name="one"/>""")
        assertFailsWith<IllegalArgumentException> { readTestEvidence(directory) }
    }

    @Test
    fun `undeclared skip cannot be hidden`() {
        report(1, """<testcase name="skipped"><skipped/></testcase>""")
        assertFailsWith<IllegalArgumentException> { readTestEvidence(directory) }
    }
}
