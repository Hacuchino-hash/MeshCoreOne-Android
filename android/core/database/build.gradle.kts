// AndroidOnly: WP-002 Room generator convention shell; actual store/schema remains WP-201/202.
plugins { id("mesh.android.room") }
dependencies { implementation(project(":core:model")) }
