// AndroidOnly: WP-313 Native check that handlers ignore responses whose prefix cannot identify the session.
package com.meshcoreone.android.feature.remotenodes.status

import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.feature.remotenodes.support.bytes
import com.meshcoreone.android.feature.remotenodes.support.session
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class NodeStatusSessionMatchTest {
    private val state = NodeStatusState(session = session(publicKey = bytes(32, 0x42)))

    @Test
    fun `empty prefix never matches a session`() {
        assertFalse(state.matchesSession(Bytes.EMPTY))
    }

    @Test
    fun `matching prefix matches and a different one does not`() {
        assertTrue(state.matchesSession(bytes(6, 0x42)))
        assertFalse(state.matchesSession(bytes(6, 0x43)))
    }

    @Test
    fun `no session matches nothing`() {
        assertFalse(NodeStatusState().matchesSession(bytes(6, 0x42)))
    }
}
