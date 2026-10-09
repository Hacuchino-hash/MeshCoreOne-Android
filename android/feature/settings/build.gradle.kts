// AndroidOnly: WP-002 Settings entry shell; no billing, backend, updater or translation engine.
plugins { id("mesh.android.feature") }
rootProject.tasks.named("verifyScaffoldTests") { dependsOn(":feature:settings:testDebugUnitTest") }
