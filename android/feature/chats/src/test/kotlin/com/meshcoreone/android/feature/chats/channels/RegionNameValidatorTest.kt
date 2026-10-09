// PortedFrom: MC1Tests/Views/Chats/RegionNameValidatorTests.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.feature.chats.channels

import com.meshcoreone.android.core.model.ProtocolLimits
import com.meshcoreone.android.core.ui.RegionNameValidator
import com.meshcoreone.android.core.ui.RegionValidationError
import com.meshcoreone.android.feature.chats.list.support.OriginalCase
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

/** The add-region sheet of the channel info screen validates through the shared `core:ui` validator. */
class RegionNameValidatorTest {
    private fun valid(name: String, existing: Collection<String> = emptyList()) = RegionNameValidator.validate(name, existing) == null

    @Test @OriginalCase("RegionNameValidatorTests::accepts standard region names(name : String)")
    fun `accepts standard region names`() {
        listOf("Europe", "UK", "France", "sample-city", "region-1").forEach { assertTrue(valid(it), it) }
    }

    @Test @OriginalCase("RegionNameValidatorTests::rejects empty name()")
    fun `rejects empty name`() = assertEquals(RegionValidationError.Empty, RegionNameValidator.validate("", emptyList()))

    @Test @OriginalCase("RegionNameValidatorTests::rejects whitespace-only name()")
    fun `rejects whitespace-only name`() = assertEquals(RegionValidationError.Empty, RegionNameValidator.validate("   ", emptyList()))

    @Test @OriginalCase("RegionNameValidatorTests::rejects name with spaces()")
    fun `rejects name with spaces`() = assertEquals(RegionValidationError.InvalidCharacters, RegionNameValidator.validate("my region", emptyList()))

    @Test @OriginalCase("RegionNameValidatorTests::rejects unicode characters()")
    fun `rejects unicode characters`() =
        assertEquals(RegionValidationError.InvalidCharacters, RegionNameValidator.validate("Île-de-France", emptyList()))

    @Test @OriginalCase("RegionNameValidatorTests::rejects special characters(name : String)")
    fun `rejects special characters`() {
        listOf("hello!", "foo@bar", "a&b", "test.region", "#Europe", "\$secret").forEach {
            assertEquals(RegionValidationError.InvalidCharacters, RegionNameValidator.validate(it, emptyList()), it)
        }
    }

    @Test @OriginalCase("RegionNameValidatorTests::accepts names at the byte cap()")
    fun `accepts names at the byte cap`() {
        val name = "a".repeat(ProtocolLimits.MAX_DEFAULT_FLOOD_SCOPE_NAME_BYTES)
        assertEquals(ProtocolLimits.MAX_DEFAULT_FLOOD_SCOPE_NAME_BYTES, name.toByteArray().size)
        assertTrue(valid(name))
    }

    @Test @OriginalCase("RegionNameValidatorTests::rejects names one byte over the cap()")
    fun `rejects names one byte over the cap`() {
        val max = ProtocolLimits.MAX_DEFAULT_FLOOD_SCOPE_NAME_BYTES
        assertEquals(RegionValidationError.TooLong(max), RegionNameValidator.validate("a".repeat(max + 1), emptyList()))
    }

    @Test @OriginalCase("RegionNameValidatorTests::rejects duplicate region name()")
    fun `rejects duplicate region name`() = assertEquals(RegionValidationError.Duplicate, RegionNameValidator.validate("Europe", listOf("Europe")))

    @Test @OriginalCase("RegionNameValidatorTests::duplicate check is case-sensitive()")
    fun `duplicate check is case-sensitive`() = assertTrue(valid("europe", listOf("Europe")))

    @Test @OriginalCase("RegionNameValidatorTests::isValid returns true for valid name()")
    fun `isValid returns true for valid name`() = assertNull(RegionNameValidator.validate("Europe", emptyList()))

    @Test @OriginalCase("RegionNameValidatorTests::isValid returns false for invalid name()")
    fun `isValid returns false for invalid name`() = assertFalse(valid(""))
}
