// AndroidOnly: WP-002 Reject malformed/schema-shape and hidden catalog-input evidence.
package com.meshcoreone.buildlogic

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.io.TempDir

class ScaffoldSchemaTest {
    @TempDir
    lateinit var directory: File

    @Test
    fun `missing exported schema fails`() {
        assertFailsWith<IllegalArgumentException> { verifyScaffoldSchema(File(directory, "missing.json")) }
    }

    @Test
    fun `successful shaped strings without real schema structure fail`() {
        val schema = File(directory, "fake.json")
        schema.writeText("""{"version":1,"tableName":"scaffold_rows"}""")
        assertFailsWith<IllegalArgumentException> { verifyScaffoldSchema(schema) }
    }

    @Test
    fun `local file catalog bookkeeping has no external dependency state`() {
        val lock = File(directory, "settings-gradle.lockfile")
        lock.writeText("# Generated\nempty=incomingCatalogForLibs0\n")
        verifyLocalCatalogBookkeeping(lock)
    }

    @Test
    fun `remote catalog input cannot be silently ignored`() {
        val lock = File(directory, "settings-gradle.lockfile")
        lock.writeText("example:remote-catalog:1.0=incomingCatalogForLibs0\n")
        assertFailsWith<IllegalArgumentException> { verifyLocalCatalogBookkeeping(lock) }
    }
}
