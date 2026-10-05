// AndroidOnly: WP-205 Actual API33 immutable-value GATT API execution in framework shadows.
package com.meshcoreone.android.core.ble

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, shadows = [ControlledGattShadow::class])
class AndroidGattApi33Test : AndroidGattAdapterTest()
