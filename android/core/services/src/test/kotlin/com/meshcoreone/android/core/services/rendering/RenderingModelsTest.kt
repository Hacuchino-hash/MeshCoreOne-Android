// AndroidOnly: WP-213 behavior cases for the fragment builder and rendering models beyond the ported Swift suites.
package com.meshcoreone.android.core.services.rendering

import com.meshcoreone.android.core.model.LinkPreviewDataDTO
import com.meshcoreone.android.core.model.MessageDTO
import com.meshcoreone.android.core.model.MessageDirection
import com.meshcoreone.android.core.model.MessageStatus
import com.meshcoreone.android.core.model.SnapshotList
import com.meshcoreone.android.core.protocol.bytes.Bytes
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

class RenderingModelsTest {
    private fun url(value: String): WebURL = requireNotNull(WebURL.parse(value))

    private fun inputs(
        message: MessageDTO,
        previewState: PreviewLoadState = PreviewLoadState.IDLE,
        cachedURL: WebURL? = null,
        isInlineImageURL: Boolean = false,
        hasInlineImageRef: Boolean = false,
        loadedPreview: LinkPreviewDataDTO? = null,
        mapPreview: Pair<Double, Double>? = null,
        formattedPath: String? = null,
    ) = MessageBuildInputs(
        messageID = message.id, previewState = previewState, loadedPreview = loadedPreview, cachedURL = cachedURL,
        isInlineImageURL = isInlineImageURL, hasInlineImageRef = hasInlineImageRef, hasPreviewImageRef = true,
        hasPreviewIconRef = true, imageIsGIF = true, inlineImageAspect = 1.5, mapPreviewLatitude = mapPreview?.first,
        mapPreviewLongitude = mapPreview?.second, formattedText = null, baseColor = BaseColorSlot.OUTGOING,
        formattedPath = formattedPath, senderResolution = NodeNameResolution("Sender", NodeNameMatchKind.FALLBACK, "Nick"),
        showTimestamp = true, showDirectionGap = false, showSenderName = true, showNewMessagesDivider = false, showDayDivider = true,
    )

    private fun env(previews: Boolean = true, maps: Boolean = true) = RenderingFixtures.makeEnvInputs().copy(
        previewsEnabled = previews, showMapPreviews = maps, isOffline = true, autoPlayGIFs = false,
    )

    @TestFactory
    fun builderCases(): List<DynamicTest> = listOf(
        native("inline image load state follows the Swift preview-state table") {
            val message = RenderingFixtures.makeMessage()
            val image = url("https://example.com/cat.gif")
            fun state(previewState: PreviewLoadState, link: WebURL?, ref: Boolean = false): InlineImage.LoadState {
                val item = MessageFragmentBuilder.makeItem(message, inputs(message, previewState, link, true, ref), env())
                val fragment = assertIs<MessageFragment.InlineImage>(item.content[1]).image
                assertFalse(fragment.autoPlayGIFs)
                assertEquals(1.5, fragment.cachedAspect)
                return fragment.state
            }
            val blank = InlineImage.LoadState.Failed(WebURL.BLANK)
            assertEquals(InlineImage.LoadState.Loaded(ImageReference(message.id, ImageReference.Role.INLINE), true), state(PreviewLoadState.LOADED, image, ref = true))
            assertEquals(InlineImage.LoadState.Loading(image), state(PreviewLoadState.LOADED, image))
            assertEquals(blank, state(PreviewLoadState.LOADED, null))
            assertEquals(InlineImage.LoadState.Loading(image), state(PreviewLoadState.LOADING, image))
            assertEquals(blank, state(PreviewLoadState.LOADING, null))
            assertEquals(InlineImage.LoadState.Failed(image), state(PreviewLoadState.NO_PREVIEW, image))
            assertEquals(InlineImage.LoadState.Disabled(image), state(PreviewLoadState.DISABLED, image))
            assertEquals(blank, state(PreviewLoadState.DISABLED, null))
            assertEquals(InlineImage.LoadState.Idle(image), state(PreviewLoadState.IDLE, image))
            assertEquals(blank, state(PreviewLoadState.MALWARE_WARNING, null)) // malware without a URL falls through
            assertEquals("about:blank", WebURL.BLANK.absoluteString)
        },
        native("link preview mode follows the Swift preview-state table") {
            val page = url("https://example.com/page")
            val preview = LinkPreviewDataDTO(url = "https://example.com/page", title = "T")
            fun mode(previewState: PreviewLoadState, link: WebURL?, loaded: LinkPreviewDataDTO? = null, legacy: String? = null): LinkPreviewFragmentState.Mode {
                val message = RenderingFixtures.makeMessage().copy(linkPreviewURL = legacy, linkPreviewTitle = legacy?.let { "Legacy" })
                val item = MessageFragmentBuilder.makeItem(message, inputs(message, previewState, link, loadedPreview = loaded), env())
                return assertIs<MessageFragment.LinkPreview>(item.content[1]).state.mode
            }
            val message = RenderingFixtures.makeMessage()
            val refs = ImageReference(message.id, ImageReference.Role.LINK_PREVIEW_IMAGE) to ImageReference(message.id, ImageReference.Role.LINK_PREVIEW_ICON)
            val loadedMode = assertIs<LinkPreviewFragmentState.Mode.Loaded>(mode(PreviewLoadState.LOADED, page, preview))
            assertEquals(preview, loadedMode.preview)
            assertEquals(ImageReference.Role.LINK_PREVIEW_IMAGE, loadedMode.image?.role)
            assertEquals(ImageReference.Role.LINK_PREVIEW_ICON, loadedMode.icon?.role)
            assertEquals(LinkPreviewFragmentState.Mode.NoPreview, mode(PreviewLoadState.LOADED, page))
            assertEquals(LinkPreviewFragmentState.Mode.Loading(page), mode(PreviewLoadState.LOADING, page))
            assertEquals(LinkPreviewFragmentState.Mode.Idle, mode(PreviewLoadState.LOADING, null))
            assertEquals(LinkPreviewFragmentState.Mode.NoPreview, mode(PreviewLoadState.NO_PREVIEW, page))
            assertEquals(LinkPreviewFragmentState.Mode.Disabled(page), mode(PreviewLoadState.DISABLED, page))
            assertEquals(LinkPreviewFragmentState.Mode.Idle, mode(PreviewLoadState.DISABLED, null))
            assertEquals(LinkPreviewFragmentState.Mode.Loading(page), mode(PreviewLoadState.IDLE, page))
            assertEquals(LinkPreviewFragmentState.Mode.Idle, mode(PreviewLoadState.IDLE, null))
            assertEquals(LinkPreviewFragmentState.Mode.Idle, mode(PreviewLoadState.IDLE, null, legacy = ""))
            val legacy = assertIs<LinkPreviewFragmentState.Mode.Legacy>(mode(PreviewLoadState.IDLE, page, legacy = "https://legacy.example"))
            assertEquals("https://legacy.example", legacy.url.absoluteString)
            assertEquals("Legacy", legacy.title)
            assertEquals(refs.second.role, legacy.icon?.role)
            assertEquals(LinkPreviewFragmentState.Mode.Idle, mode(PreviewLoadState.MALWARE_WARNING, null))
        },
        native("map preview follows any card, shows on malware rows and is skipped when thumbnails are off") {
            val message = RenderingFixtures.makeMessage()
            val coordinate = 1.0 to -2.0
            val withCard = MessageFragmentBuilder.makeItem(message, inputs(message, cachedURL = url("https://x.example"), mapPreview = coordinate), env())
            assertEquals(listOf("Text", "LinkPreview", "MapPreview"), withCard.content.map { it::class.simpleName })
            val map = assertIs<MessageFragment.MapPreview>(withCard.content.last()).state
            assertTrue(map.isOffline)
            assertEquals(1.0, map.coordinate.latitude)
            val malware = MessageFragmentBuilder.makeItem(
                message, inputs(message, PreviewLoadState.MALWARE_WARNING, url("https://bad.example"), mapPreview = coordinate), env(),
            )
            assertEquals(listOf("Text", "MalwareWarning", "MapPreview"), malware.content.map { it::class.simpleName })
            val hidden = MessageFragmentBuilder.makeItem(message, inputs(message, mapPreview = coordinate), env(previews = false, maps = false))
            assertEquals(listOf("Text"), hidden.content.map { it::class.simpleName })
            val latitudeOnly = MessageFragmentBuilder.makeItem(message, inputs(message).let {
                MessageBuildInputs(
                    messageID = it.messageID, previewState = it.previewState, loadedPreview = null, cachedURL = null,
                    isInlineImageURL = false, hasInlineImageRef = false, hasPreviewImageRef = false, hasPreviewIconRef = false,
                    imageIsGIF = false, mapPreviewLatitude = 1.0, formattedText = null, baseColor = BaseColorSlot.INCOMING,
                    formattedPath = null, senderResolution = it.senderResolution, showTimestamp = false, showDirectionGap = false,
                    showSenderName = false, showNewMessagesDivider = false,
                )
            }, env(previews = false))
            assertEquals(1, latitudeOnly.content.size)
        },
        native("footer gates hop, heard-count and region chips exactly like Swift") {
            val showAll = env().copy(showIncomingHopCount = true, showIncomingHeardCount = true, showIncomingRegion = true)
            val incomingFlood = RenderingFixtures.makeMessage(heardRepeats = 2).copy(
                direction = MessageDirection.INCOMING, channelIndex = 3u, pathLength = 2u,
                regionScopeMatches = SnapshotList.of("beta", "alpha"),
            )
            val footer = MessageFragmentBuilder.makeItem(incomingFlood, inputs(incomingFlood, formattedPath = "AB → CD"), showAll).footer
            assertTrue(footer.showHop)
            assertEquals(2L, footer.hopCount)
            assertTrue(footer.showHeardCount)
            assertEquals("alpha / beta", footer.regionToShow)
            assertEquals(listOf("alpha", "beta"), footer.regionMatchNames)
            assertTrue(footer.regionIsAmbiguous)
            assertEquals("AB → CD", footer.formattedPath)
            assertFalse(footer.showStatusRow)
            assertTrue(footer.isChannelMessage)

            val outgoing = incomingFlood.copy(direction = MessageDirection.OUTGOING)
            val outgoingFooter = MessageFragmentBuilder.makeItem(outgoing, inputs(outgoing), showAll).footer
            assertFalse(outgoingFooter.showHop)
            assertFalse(outgoingFooter.showHeardCount)
            assertTrue(outgoingFooter.showStatusRow)

            val direct = incomingFlood.copy(channelIndex = null, pathLength = 255u, routeType = null)
            val directFooter = MessageFragmentBuilder.makeItem(direct, inputs(direct), showAll).footer
            assertFalse(directFooter.showHop)
            assertNull(directFooter.regionToShow)
            assertEquals(emptyList(), directFooter.regionMatchNames)

            val single = incomingFlood.copy(regionScope = " west ", regionScopeMatches = SnapshotList.empty())
            val singleFooter = MessageFragmentBuilder.makeItem(single, inputs(single), showAll).footer
            assertEquals("west", singleFooter.regionToShow)
            assertFalse(singleFooter.regionIsAmbiguous)
            val chipOff = MessageFragmentBuilder.makeItem(single, inputs(single), showAll.copy(showIncomingRegion = false)).footer
            assertNull(chipOff.regionToShow)
        },
        native("envelope, grouping and text payload carry every input field") {
            val message = RenderingFixtures.makeMessage(status = MessageStatus.FAILED).copy(text = "body")
            val avatar = IncomingAvatarIdentity("Sender", UUID.randomUUID(), 7UL)
            val base = inputs(message)
            val withAvatar = MessageBuildInputs(
                messageID = base.messageID, previewState = base.previewState, loadedPreview = null, cachedURL = null,
                isInlineImageURL = false, hasInlineImageRef = false, hasPreviewImageRef = false, hasPreviewIconRef = false,
                imageIsGIF = false, formattedText = null, baseColor = BaseColorSlot.OUTGOING, formattedPath = null,
                senderResolution = base.senderResolution, showTimestamp = true, showDirectionGap = true, showSenderName = false,
                showNewMessagesDivider = true, showDayDivider = true, incomingAvatar = avatar,
            )
            val item = MessageFragmentBuilder.makeItem(message, withAvatar, RenderingFixtures.makeEnvInputs().copy(currentUserName = "Me"))
            assertEquals("Sender", item.envelope.senderName)
            assertEquals(NodeNameMatchKind.FALLBACK, item.envelope.senderResolution.matchKind)
            assertTrue(item.envelope.hasFailed)
            assertSame(avatar, item.envelope.incomingAvatar)
            assertEquals(GroupingFlags(true, true, false, true, true), item.grouping)
            val text = assertIs<MessageFragment.Text>(item.content[0]).payload
            assertEquals(MessageTextPayload("body", null, BaseColorSlot.OUTGOING, true, "Me", null), text)
            assertNull(item.translation)
        },
        native("same-id DTO changes to sendCount and text flip item equality") {
            val id = UUID.randomUUID()
            val inputs = RenderingFixtures.makeInputs(id)
            val env = RenderingFixtures.makeEnvInputs()
            val base = MessageFragmentBuilder.makeItem(RenderingFixtures.makeMessage(id = id), inputs, env)
            assertNotEquals(base, MessageFragmentBuilder.makeItem(RenderingFixtures.makeMessage(id = id, sendCount = 2), inputs, env))
            assertNotEquals(base, MessageFragmentBuilder.makeItem(RenderingFixtures.makeMessage(id = id, text = "other"), inputs, env))
            assertEquals(base, MessageFragmentBuilder.makeItem(RenderingFixtures.makeMessage(id = id), inputs, env))
        },
        native("empty reaction summary emits no fragment") {
            val message = RenderingFixtures.makeMessage().copy(reactionSummary = "")
            assertEquals(1, MessageFragmentBuilder.makeFragments(message, RenderingFixtures.makeInputs(message.id), env(previews = false)).size)
        },
    )

    @TestFactory
    fun modelCases(): List<DynamicTest> = listOf(
        native("SNRQuality thresholds, bar levels and labels match Swift") {
            assertEquals(SNRQuality.UNKNOWN, SNRQuality.of(null))
            assertEquals(SNRQuality.EXCELLENT, SNRQuality.of(6.0001))
            assertEquals(SNRQuality.GOOD, SNRQuality.of(6.0))
            assertEquals(SNRQuality.GOOD, SNRQuality.of(0.01))
            assertEquals(SNRQuality.FAIR, SNRQuality.of(0.0))
            assertEquals(SNRQuality.FAIR, SNRQuality.of(-5.99))
            assertEquals(SNRQuality.POOR, SNRQuality.of(-6.0))
            assertEquals(SNRQuality.POOR, SNRQuality.of(Double.NaN))
            assertEquals(listOf(1.0, 0.75, 0.5, 0.25, 0.0), SNRQuality.entries.map { it.barLevel })
            assertEquals(listOf("Excellent", "Good", "Fair", "Weak", "Unknown"), SNRQuality.entries.map { it.qualityLabel })
        },
        native("IncomingAvatarIdentity.resolve trims, lowercases and matches canonically, else initials") {
            val photo = IncomingAvatarIdentity("José", UUID.randomUUID(), 3UL)
            val table = mapOf("josé" to photo)
            assertSame(photo, IncomingAvatarIdentity.resolve("  JOSÉ​ ", "Display", table))
            assertEquals(IncomingAvatarIdentity.initials("Display"), IncomingAvatarIdentity.resolve(" \n", "Display", table))
            assertEquals(IncomingAvatarIdentity.initials("Display"), IncomingAvatarIdentity.resolve(null, "Display", table))
            assertEquals(IncomingAvatarIdentity("Display", null, null), IncomingAvatarIdentity.resolve("other", "Display", table))
            val sigma = IncomingAvatarIdentity("s", null, null)
            assertSame(sigma, IncomingAvatarIdentity.resolve("ΣΟΣ", "x", mapOf("σοσ" to sigma)))
        },
        native("IncomingAvatarIdentity.revision is null for missing or empty data and content-sensitive otherwise") {
            assertNull(IncomingAvatarIdentity.revision(null))
            assertNull(IncomingAvatarIdentity.revision(Bytes(ByteArray(0))))
            val a = IncomingAvatarIdentity.revision(Bytes(byteArrayOf(1, 2, 3)))
            assertEquals(a, IncomingAvatarIdentity.revision(Bytes(byteArrayOf(1, 2, 3))))
            assertNotEquals(a, IncomingAvatarIdentity.revision(Bytes(byteArrayOf(1, 2, 4))))
        },
        native("LinkPreviewFragmentState.primaryURL resolves loaded and legacy cards only") {
            val page = url("https://example.com/page")
            assertEquals(page, LinkPreviewFragmentState(LinkPreviewFragmentState.Mode.Loaded(LinkPreviewDataDTO("https://example.com/page"), null, null)).primaryURL)
            assertNull(LinkPreviewFragmentState(LinkPreviewFragmentState.Mode.Loaded(LinkPreviewDataDTO("not a url"), null, null)).primaryURL)
            assertEquals(page, LinkPreviewFragmentState(LinkPreviewFragmentState.Mode.Legacy(page, null, null, null)).primaryURL)
            listOf(LinkPreviewFragmentState.Mode.Idle, LinkPreviewFragmentState.Mode.Loading(page), LinkPreviewFragmentState.Mode.NoPreview,
                LinkPreviewFragmentState.Mode.Disabled(page)).forEach { assertNull(LinkPreviewFragmentState(it).primaryURL) }
        },
        native("Double-carrying values use Swift equality: signed zero equal, NaN unequal") {
            assertEquals(MapPreviewFragmentState(0.0, 1.0, false, false, true), MapPreviewFragmentState(-0.0, 1.0, false, false, true))
            assertEquals(MapPreviewFragmentState(0.0, 1.0, false, false, true).hashCode(), MapPreviewFragmentState(-0.0, 1.0, false, false, true).hashCode())
            assertNotEquals(MapPreviewFragmentState(Double.NaN, 1.0, false, false, true), MapPreviewFragmentState(Double.NaN, 1.0, false, false, true))
            val idle = InlineImage.LoadState.Idle(url("https://a.example"))
            assertEquals(InlineImage(idle, true, 0.0), InlineImage(idle, true, -0.0))
            assertNotEquals(InlineImage(idle, true, Double.NaN), InlineImage(idle, true, Double.NaN))
            assertEquals(LinkPreviewFragmentState(LinkPreviewFragmentState.Mode.Idle, 0.0), LinkPreviewFragmentState(LinkPreviewFragmentState.Mode.Idle, -0.0))
            val message = RenderingFixtures.makeMessage()
            assertEquals(RenderingFixtures.makeInputs(message.id), RenderingFixtures.makeInputs(message.id))
            assertEquals(RenderingFixtures.makeInputs(message.id).hashCode(), RenderingFixtures.makeInputs(message.id).hashCode())
        },
        native("WebURL keeps the string verbatim and rejects what java.net.URI rejects") {
            assertNull(WebURL.parse(""))
            assertNull(WebURL.parse("https://ex ample.com/a b"))
            assertNull(WebURL.parse("http://[::1"))
            assertEquals("HTTPS://Example.COM/A", WebURL.parse("HTTPS://Example.COM/A")?.absoluteString)
            assertNotEquals(WebURL.parse("https://example.com/A"), WebURL.parse("https://EXAMPLE.com/A"))
            assertEquals("mailto:x@y.z", WebURL.parse("mailto:x@y.z")?.toString())
        },
        native("footer, item and resolution helpers") {
            val message = RenderingFixtures.makeMessage(status = MessageStatus.PENDING)
            val item = MessageFragmentBuilder.makeItem(message, RenderingFixtures.makeInputs(message.id), RenderingFixtures.makeEnvInputs())
            val flipped = item.with(envelope = item.envelope.withStatus(MessageStatus.FAILED), footer = item.footer.with(MessageStatus.FAILED))
            assertTrue(flipped.envelope.hasFailed)
            assertEquals(MessageStatus.FAILED, flipped.footer.status)
            assertEquals(item.content, flipped.content)
            assertEquals(item.grouping, flipped.grouping)
            assertEquals(item, item.with())
            assertTrue(NodeNameResolution("a", NodeNameMatchKind.FALLBACK).isFallback)
            assertFalse(NodeNameResolution("a", NodeNameMatchKind.EXACT).isFallback)
            val chrome = MessageTranslationChrome(MessageTranslationChrome.Phase.InProgress, "fr")
            val translated = item.copy(content = SnapshotList.of(MessageFragment.ReactionSummary("x"), MessageFragment.Text(
                MessageTextPayload("t", null, BaseColorSlot.INCOMING, false, "", chrome),
            )))
            assertEquals(chrome, translated.translation)
        },
    )

    private fun native(name: String, body: () -> Unit) = DynamicTest.dynamicTest("WP-213::$name", body)
}
