// PortedFrom: MC1/Services/ElevationService.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.app.content

import android.content.Context
import com.meshcoreone.android.core.l10n.R
import com.meshcoreone.android.core.services.content.ElevationServiceError

fun ElevationServiceError.localizedDescription(context: Context): String = when (this) {
    is ElevationServiceError.NetworkError ->
        context.getString(R.string.l10n_app_localizable_common_error_networkerror, description)
    ElevationServiceError.InvalidResponse ->
        context.getString(R.string.l10n_app_localizable_common_error_invalidresponse)
    is ElevationServiceError.ApiError ->
        context.getString(R.string.l10n_app_localizable_common_error_apierror, apiMessage)
    ElevationServiceError.NoData ->
        context.getString(R.string.l10n_app_localizable_common_error_noelevationdata)
    ElevationServiceError.RateLimited ->
        context.getString(R.string.l10n_app_localizable_common_error_ratelimited)
}
