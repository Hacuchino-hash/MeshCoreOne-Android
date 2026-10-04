// AndroidOnly: WP-002 Honest launcher/module assembly without a product AppContainer.
plugins {
    id("mesh.android.application")
    id("mesh.android.robolectric")
}

dependencies {
    implementation(project(":core:protocol"))
    implementation(project(":core:model"))
    implementation(project(":core:contracts"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:data"))
    implementation(project(":core:ble"))
    implementation(project(":core:connectivity"))
    implementation(project(":core:runtime"))
    implementation(project(":core:services"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))
    implementation(project(":core:maps"))
    implementation(project(":core:l10n"))
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:chats"))
    implementation(project(":feature:nodes"))
    implementation(project(":feature:remotenodes"))
    implementation(project(":feature:map"))
    implementation(project(":feature:tools"))
    implementation(project(":feature:settings"))
    implementation(project(":platform:notifications"))
    implementation(project(":platform:widgets"))
    implementation(project(":platform:shortcuts"))
    implementation(project(":platform:translation"))
    testImplementation(libs.androidx.compose.ui.test.junit4)
}
