// AndroidOnly: WP-002 Lock component graphs without guessing AGP secondary artifact variants.
package com.meshcoreone.buildlogic

import org.gradle.api.GradleException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.result.UnresolvedDependencyResult

internal fun resolveDependencyGraph(configuration: Configuration): Set<String> {
    val result = configuration.incoming.resolutionResult
    val unresolved = result.allDependencies.filterIsInstance<UnresolvedDependencyResult>()
    if (unresolved.isNotEmpty()) {
        throw GradleException(
            "Unresolved dependency graph ${configuration.name}: ${unresolved.joinToString { it.attempted.displayName }}",
            unresolved.first().failure,
        )
    }
    return result.allComponents.map { it.id.displayName }.toSet()
}
