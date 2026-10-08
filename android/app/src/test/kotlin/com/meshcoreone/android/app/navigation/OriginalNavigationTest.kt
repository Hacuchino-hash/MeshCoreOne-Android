// AndroidOnly: WP-302 Execute the complete original assertion bodies against actual SDK31/37 native SQLite and policy callbacks.
package com.meshcoreone.android.app.navigation.cases

import androidx.test.core.app.ApplicationProvider
import android.os.Build
import com.meshcoreone.android.app.navigation.emitNavigationExecutionBinding
import com.meshcoreone.android.app.navigation.NavigationLookup
import com.meshcoreone.android.core.model.ChannelDTO
import com.meshcoreone.android.core.model.ContactDTO
import com.meshcoreone.android.core.model.RemoteNodeSessionDTO
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31, 37])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class OriginalNavigationTest : OriginalNavigationCases() {
    @get:Rule val executionName = TestName()
    private lateinit var persistence: NavigationPersistenceFixture
    @Before fun openProcessStore() {
        emitNavigationExecutionBinding(javaClass.name, executionName.methodName, Build.VERSION.SDK_INT.toString())
        persistence = NavigationPersistenceFixture(ApplicationProvider.getApplicationContext())
    }
    @After fun closeProcessStore() = persistence.close()
    override fun lookup(contact: ContactDTO?, channel: ChannelDTO?, room: RemoteNodeSessionDTO?): NavigationLookup =
        persistence.seeded(contact, channel, room)
}
