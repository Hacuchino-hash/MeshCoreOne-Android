// AndroidOnly: WP-002 Mandatory evidence rejects absent, zero, skipped or malformed test reports.
package com.meshcoreone.buildlogic

import java.io.File
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

internal fun secureXmlFactory(): DocumentBuilderFactory = DocumentBuilderFactory.newInstance().apply {
    setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    setFeature("http://xml.org/sax/features/external-general-entities", false)
    setFeature("http://xml.org/sax/features/external-parameter-entities", false)
    setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
    setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
}

internal data class TestEvidence(val discovered: Int, val passed: Int, val failed: Int, val errors: Int, val skipped: Int)

internal fun readTestEvidence(directory: File): TestEvidence {
    val reports = directory.listFiles { file -> file.name.startsWith("TEST-") && file.extension == "xml" }.orEmpty()
    require(reports.isNotEmpty()) { "Missing mandatory test reports: ${directory.name}" }
    var discovered = 0
    var failed = 0
    var errors = 0
    var skipped = 0
    reports.sortedBy { it.name }.forEach { report ->
        val suite = secureXmlFactory().newDocumentBuilder().parse(report).documentElement
        require(suite.tagName == "testsuite") { "Malformed test report: ${report.name}" }
        fun count(name: String): Int {
            val value = suite.getAttribute(name).toIntOrNull()
            require(value != null && value >= 0) { "Invalid $name count: ${report.name}" }
            return value
        }
        val tests = count("tests")
        val failures = count("failures")
        val suiteErrors = count("errors")
        val suiteSkipped = count("skipped")
        val cases = suite.getElementsByTagName("testcase")
        require(tests == cases.length && tests > 0) { "Zero/mismatched test discovery: ${report.name}" }
        fun actual(name: String) = (0 until cases.length).count {
            (cases.item(it) as Element).getElementsByTagName(name).length > 0
        }
        require(failures == actual("failure") && suiteErrors == actual("error") && suiteSkipped == actual("skipped")) {
            "Mismatched test outcomes: ${report.name}"
        }
        require(failures == 0 && suiteErrors == 0 && suiteSkipped == 0) {
            "Mandatory suite failed/skipped: ${report.name}"
        }
        discovered = Math.addExact(discovered, tests)
        failed = Math.addExact(failed, failures)
        errors = Math.addExact(errors, suiteErrors)
        skipped = Math.addExact(skipped, suiteSkipped)
    }
    return TestEvidence(discovered, discovered - failed - errors - skipped, failed, errors, skipped)
}
