// AndroidOnly: WP-302 Separate real instrumentation execution of original bodies; never credited by local Robolectric.
package com.meshcoreone.android.app.navigation.cases

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.meshcoreone.android.app.navigation.NavigationLookup
import com.meshcoreone.android.core.model.ChannelDTO
import com.meshcoreone.android.core.model.ContactDTO
import com.meshcoreone.android.core.model.RemoteNodeSessionDTO
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OriginalNavigationInstrumentedTest : OriginalNavigationCases() {
    private lateinit var persistence: NavigationPersistenceFixture
    @Before fun openProcessStore() {
        persistence = NavigationPersistenceFixture(ApplicationProvider.getApplicationContext())
    }
    @After fun closeProcessStore() = persistence.close()
    override fun lookup(contact: ContactDTO?, channel: ChannelDTO?, room: RemoteNodeSessionDTO?): NavigationLookup =
        persistence.seeded(contact, channel, room)
}
