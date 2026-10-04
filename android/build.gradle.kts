// AndroidOnly: WP-002 Actual graph, test-discovery and schema verification entry points.
buildscript {
    dependencyLocking {
        lockAllConfigurations()
        lockMode.set(LockMode.STRICT)
        lockFile.set(layout.projectDirectory.file("gradle/dependency-locks/root-buildscript.lockfile"))
    }
}

plugins {
    id("mesh.scaffold.root")
}

allprojects {
    val lockName = path.removePrefix(":").replace(":", "-").ifEmpty { "root" }
    dependencyLocking {
        lockAllConfigurations()
        lockMode.set(LockMode.STRICT)
        lockFile.set(rootProject.layout.projectDirectory.file("gradle/dependency-locks/$lockName.lockfile"))
    }
    if (this != rootProject) {
        buildscript.dependencyLocking {
            lockAllConfigurations()
            lockMode.set(LockMode.STRICT)
            lockFile.set(rootProject.layout.projectDirectory.file("gradle/dependency-locks/$lockName-buildscript.lockfile"))
        }
    }
}
