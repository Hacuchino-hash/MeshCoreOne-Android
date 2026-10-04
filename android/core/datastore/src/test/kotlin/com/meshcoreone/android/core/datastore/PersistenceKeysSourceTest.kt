// PortedFrom: MC1Services/Tests/MC1ServicesTests/PersistenceKeysThemeTests.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.datastore

import com.meshcoreone.android.core.model.PersistenceKeys
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.junit.Test

class PersistenceKeysSourceTest {
    @OriginalCase("PersistenceKeysThemeTests::theme keys are the exact bare-string on-disk names()")
    @Test
    fun exactBareNames() {
        assertEquals("selectedThemeID", PersistenceKeys.SELECTED_THEME_ID)
        assertEquals("appColorSchemePreference", PersistenceKeys.APP_COLOR_SCHEME_PREFERENCE)
        assertEquals(PersistenceKeys.SELECTED_THEME_ID, AppearanceStorageKey.selectedThemeID.rawValue)
        assertEquals(PersistenceKeys.APP_COLOR_SCHEME_PREFERENCE, AppearanceStorageKey.appColorSchemePreference.rawValue)
    }

    @OriginalCase("PersistenceKeysThemeTests::theme keys carry no com.pocketmesh prefix (bare-string convention)()")
    @Test
    fun themeKeysAreNotInfrastructureKeys() {
        assertFalse(PersistenceKeys.SELECTED_THEME_ID.contains("com.pocketmesh"))
        assertFalse(PersistenceKeys.APP_COLOR_SCHEME_PREFERENCE.contains("com.pocketmesh"))
    }
}
