// AndroidOnly: WP-002 Validate the actual generator output shape without inventing a product database.
package com.meshcoreone.buildlogic

import groovy.json.JsonSlurper
import java.io.File

internal fun verifyScaffoldSchema(schema: File) {
    require(schema.isFile && schema.length() > 0) { "Missing exported Room verification schema" }
    val root = JsonSlurper().parse(schema) as? Map<*, *>
    require(root?.get("formatVersion") == 1) { "Unexpected Room schema format" }
    val database = root["database"] as? Map<*, *>
    require(database?.get("version") == 1) { "Unexpected verification database version" }
    val entities = database["entities"] as? List<*>
    require(entities?.size == 1) { "Verification schema must contain exactly its single fixture table" }
    val entity = entities.single() as? Map<*, *>
    require(entity?.get("tableName") == "scaffold_rows") { "Unexpected verification table" }
    val fields = entity["fields"] as? List<*>
    require(fields?.map { (it as? Map<*, *>)?.get("columnName") } == listOf("partition", "id", "payload")) {
        "Unexpected verification fields"
    }
    val primaryKey = entity["primaryKey"] as? Map<*, *>
    require(primaryKey?.get("columnNames") == listOf("partition", "id")) { "Partitioned fixture key was not exported" }
}

internal fun verifyLocalCatalogBookkeeping(file: File) {
    if (!file.exists()) return
    val entries = file.readLines().map(String::trim).filter { it.isNotEmpty() && !it.startsWith("#") }
    require(entries == listOf("empty=incomingCatalogForLibs0")) {
        "Settings catalog has remote coordinates; an approved settings-lock write path is required"
    }
}
