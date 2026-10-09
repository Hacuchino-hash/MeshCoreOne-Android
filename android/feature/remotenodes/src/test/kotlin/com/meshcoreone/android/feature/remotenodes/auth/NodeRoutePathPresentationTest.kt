// AndroidOnly: WP-313 Native checks of the read-only route display (no Swift test covers NodeRoutePathSection).
package com.meshcoreone.android.feature.remotenodes.auth

import com.meshcoreone.android.core.l10n.generated.AppContactsStrings
import com.meshcoreone.android.core.l10n.generated.AppRemoteNodesStrings
import com.meshcoreone.android.core.model.ContactDTO
import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.core.protocol.model.ContactType
import com.meshcoreone.android.feature.remotenodes.common.RemoteNodesText
import com.meshcoreone.android.feature.remotenodes.support.TEST_RADIO
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.junit.Test

class NodeRoutePathPresentationTest {
    private fun contact(pathLength: UByte, path: Bytes = Bytes.EMPTY, key: Bytes = Bytes(ByteArray(32) { 0x33 }), type: ContactType = ContactType.REPEATER) =
        ContactDTO(radioId = TEST_RADIO, publicKey = key, name = "n", typeRawValue = type.rawValue, outPathLength = pathLength, outPath = path, lastHeardTimestamp = null)

    @Test
    fun `flood routed contact has no route and direct shows Direct`() {
        assertEquals(NodeRoutePath.NoRoute, NodeRoutePathPresentation.of(contact(255u), emptyList(), emptyList(), null, Locale.US))
        val direct = assertIs<NodeRoutePath.Route>(NodeRoutePathPresentation.of(contact(0u), emptyList(), emptyList(), null, Locale.US))
        assertEquals(RemoteNodesText.resource(AppContactsStrings.contactsRouteDirect), direct.summary)
        assertEquals(RemoteNodesText.resource(AppContactsStrings.contactsDetailRouteDirect), direct.accessibilityLabel)
        assertEquals(emptyList(), direct.hops)
    }

    @Test
    fun `hops resolve to repeater names and unknown hops use the placeholder`() {
        val repeater = contact(255u, key = Bytes(ByteArray(32) { 0xA3.toByte() })).copy(name = "Ridge")
        val route = assertIs<NodeRoutePath.Route>(
            NodeRoutePathPresentation.of(contact(2u, Bytes.of(0xA3, 0x7F)), listOf(repeater), emptyList(), null, Locale.US),
        )
        assertEquals(RemoteNodesText.Verbatim("A3 → 7F"), route.summary)
        assertEquals(
            listOf(
                HopRow("A3", RemoteNodesText.Verbatim("Ridge"), possibleMatch = false),
                HopRow("7F", RemoteNodesText.resource(AppRemoteNodesStrings.remoteNodesAuthPathHopUnknown), possibleMatch = false),
            ),
            route.hops,
        )
    }
}
