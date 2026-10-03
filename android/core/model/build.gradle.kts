// AndroidOnly: WP-002 JVM model shell; no speculative DTOs or byte/identity implementations.
plugins { id("mesh.jvm.library") }
dependencies { api(project(":core:protocol")) }
