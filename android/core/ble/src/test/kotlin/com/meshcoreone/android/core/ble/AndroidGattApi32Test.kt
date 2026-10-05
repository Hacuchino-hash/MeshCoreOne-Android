// AndroidOnly: WP-205 Actual API32 framework shadow execution, not a physical Android/radio result.
package com.meshcoreone.android.core.ble

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], manifest = Config.NONE, shadows = [ControlledGattShadow::class])
class AndroidGattApi32Test : AndroidGattAdapterTest()
