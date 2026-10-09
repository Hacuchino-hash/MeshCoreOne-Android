// AndroidOnly: WP-318 Resource-backed text seam for the diagnostics section.
package com.meshcoreone.android.feature.settings.app.diagnostics.ui

import android.content.res.Resources
import com.meshcoreone.android.core.l10n.generated.AppSettingsStrings
import com.meshcoreone.android.feature.settings.app.diagnostics.LogExportStrings

class ResourceLogExportStrings(private val resources: Resources) : LogExportStrings {
    override fun exportFailed(): String = resources.getString(AppSettingsStrings.diagnosticsErrorExportFailed)
    override fun genericFailure(failure: Throwable): String = failure.message ?: failure.javaClass.simpleName
}
