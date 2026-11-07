package ovh.gabrielhuav.pow.domain.multiplayer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Casos del PR #167 y regresiones de sus limites de API y UTF-16. */
class PlayerNameRulesTest {
    private val fallback = { "Jugador_1234" }

    @Test
    fun normalNameIsPreserved() {
        assertEquals("Diego Ray", PlayerNameRules.sanitizeInput("Diego Ray"))
    }

    @Test
    fun pastedLongNameStopsAtSixteen() {
        assertEquals("NoTieneLimiteNoT", PlayerNameRules.sanitizeInput("NoTieneLimiteNoTieneLimite"))
    }

    @Test
    fun exactlySixteenCharactersArePreserved() {
        assertEquals("YaEstaLimitadoYa", PlayerNameRules.sanitizeInput("YaEstaLimitadoYa"))
    }

    @Test
    fun pastedNewlinesAndTabsBecomeOneLine() {
        assertEquals("Ana Luisa Perez", PlayerNameRules.sanitizeInput("Ana\nLuisa\tPerez"))
    }

    @Test
    fun leadingAndRepeatedSpacesAreRemoved() {
        assertEquals("Ana Luisa", PlayerNameRules.sanitizeInput("   Ana    Luisa"))
    }

    @Test
    fun trailingSpaceAllowsTypingAnotherWord() {
        assertEquals("Ana ", PlayerNameRules.sanitizeInput("Ana "))
    }

    @Test
    fun invisibleControlsAreRemoved() {
        assertEquals("AnaLuisa", PlayerNameRules.sanitizeInput("Ana\u0000Luisa\u0007"))
    }

    @Test
    fun blankInputAllowsGeneratedFallback() {
        assertNull(PlayerNameRules.validate(""))
        assertNull(PlayerNameRules.validate("   "))
    }

    @Test
    fun precomposedAccentsDigitsAndAllowedSymbolsAreValid() {
        assertNull(PlayerNameRules.validate("Peña_07"))
        assertNull(PlayerNameRules.validate("José-Luis.ESCOM"))
        assertNull(PlayerNameRules.validate("Ana 3"))
    }

    @Test
    fun threeCharactersAreValid() {
        assertNull(PlayerNameRules.validate("Leo"))
    }

    @Test
    fun shorterThanThreeCharactersIsInvalid() {
        assertEquals(PlayerNameRules.Problem.TOO_SHORT, PlayerNameRules.validate("Al"))
        assertEquals(PlayerNameRules.Problem.TOO_SHORT, PlayerNameRules.validate(" A "))
    }

    @Test
    fun unsupportedSymbolsAndEmojiAreInvalid() {
        for (name in listOf("Ana<script>", "Hola!!", "Ana 😀")) {
            assertEquals(PlayerNameRules.Problem.INVALID_CHARS, PlayerNameRules.validate(name))
        }
    }

    @Test
    fun finalNameDropsTrailingSpace() {
        assertEquals("Ana", PlayerNameRules.finalName("Ana ", fallback))
    }

    @Test
    fun emptyFinalNameUsesFallback() {
        assertEquals("Jugador_1234", PlayerNameRules.finalName("", fallback))
        assertEquals("Jugador_1234", PlayerNameRules.finalName("  \n ", fallback))
    }

    @Test
    fun oldSavedLongNameIsCappedBeforeSending() {
        assertEquals("NoTieneLimiteNoT", PlayerNameRules.finalName("NoTieneLimiteNoTieneLimite", fallback))
    }

    @Test
    fun generatedFallbackMeetsTheRules() {
        assertNull(PlayerNameRules.validate(fallback()))
    }

    @Test
    fun shortRemoteNameIsPreserved() {
        assertEquals("Julio", PlayerNameRules.forDisplay("Julio", "Player_ab12"))
    }

    @Test
    fun longRemoteNameEndsWithEllipsisWithinLimit() {
        val display = PlayerNameRules.forDisplay("NoTieneLimiteNoTieneLimite", "Player_ab12")
        assertEquals("NoTieneLimiteNo…", display)
        assertEquals(PlayerNameRules.MAX_LENGTH, display.length)
    }

    @Test
    fun emptyOrMissingRemoteNameUsesFallback() {
        assertEquals("Player_ab12", PlayerNameRules.forDisplay(null, "Player_ab12"))
        assertEquals("Player_ab12", PlayerNameRules.forDisplay(" \n ", "Player_ab12"))
    }

    @Test
    fun remoteNewlinesBecomeSingleSpaces() {
        assertEquals("Ana Luisa", PlayerNameRules.forDisplay("Ana\n\nLuisa", "Player_ab12"))
    }

    @Test
    fun validationAlsoRejectsOverlongDirectApiInput() {
        assertEquals(PlayerNameRules.Problem.TOO_LONG, PlayerNameRules.validate("A".repeat(17)))
        assertNull(PlayerNameRules.validate("A".repeat(16)))
    }

    @Test
    fun invalidFinalNameNeverEscapesToServerOrStorage() {
        assertNull(PlayerNameRules.finalName("Al", fallback))
        assertNull(PlayerNameRules.finalName("Ana!", fallback))
        assertNull(PlayerNameRules.finalName("Ana😀", fallback))
    }

    @Test
    fun fallbackIsNormalizedCappedAndValidated() {
        assertEquals("Ana Luisa", PlayerNameRules.finalName("") { "  Ana\nLuisa  " })
        assertEquals("A".repeat(16), PlayerNameRules.finalName("") { "A".repeat(30) })
        assertNull(PlayerNameRules.finalName("") { "!!" })
        assertNull(PlayerNameRules.finalName("") { "  \n " })
    }

    @Test
    fun validTypedNameDoesNotEvaluateFallback() {
        var fallbackCalled = false
        assertEquals("Ana", PlayerNameRules.finalName("Ana") { fallbackCalled = true; "Jugador_1234" })
        assertFalse(fallbackCalled)
    }

    @Test
    fun inputTruncationNeverCreatesAnUnpairedSurrogate() {
        val emoji = "\uD83D\uDE00"
        assertEquals("A".repeat(15), PlayerNameRules.sanitizeInput("A".repeat(15) + emoji))
        assertEquals("A".repeat(14) + emoji, PlayerNameRules.sanitizeInput("A".repeat(14) + emoji))
    }

    @Test
    fun remoteEllipsisNeverSplitsASurrogatePair() {
        val emoji = "\uD83D\uDE00"
        val display = PlayerNameRules.forDisplay("A".repeat(14) + emoji + "Z", "Player_ab12")
        assertEquals("A".repeat(14) + "…", display)
        assertTrue(display.length <= PlayerNameRules.MAX_LENGTH)
        assertFalse(display.any { it.isSurrogate() })
        assertEquals(emoji, PlayerNameRules.forDisplay(emoji, "Player_ab12"))
    }

    @Test
    fun malformedSurrogatesAreDiscardedWithoutLosingVisibleText() {
        assertEquals("Ana", PlayerNameRules.sanitizeInput("A\uD83Dn\uDE00a"))
        assertEquals("Ana", PlayerNameRules.forDisplay("A\uD83Dn\uDE00a", "Player_ab12"))
    }

    @Test
    fun bidiAndZeroWidthFormatCharactersAreRemoved() {
        assertEquals("Ana", PlayerNameRules.sanitizeInput("A\u202En\u200Ba\u2069"))
        assertEquals("Ana", PlayerNameRules.forDisplay("A\u202En\u200Ba\u2069", "Player_ab12"))
    }

    @Test
    fun remoteFallbackCannotBypassDisplayLimitOrBeBlank() {
        assertEquals("A".repeat(15) + "…", PlayerNameRules.forDisplay(null, "A".repeat(50)))
        assertEquals("Player", PlayerNameRules.forDisplay(null, " \u202E\n "))
    }

    @Test
    fun unicodeWhitespaceIsNormalizedLikePastedAsciiWhitespace() {
        assertEquals("Ana Luisa", PlayerNameRules.sanitizeInput("\u00A0Ana\u2003Luisa"))
    }

    @Test
    fun decomposedAccentsRemainOutsideTheOriginalPolicy() {
        assertNull(PlayerNameRules.validate("José"))
        assertEquals(PlayerNameRules.Problem.INVALID_CHARS, PlayerNameRules.validate("Jose\u0301"))
    }

    @Test
    fun normalizationIsIdempotentForSavedAndRemoteNames() {
        val raw = "  Ana\t\u202E Luisa  "
        val typed = PlayerNameRules.sanitizeInput(raw)
        assertEquals(typed, PlayerNameRules.sanitizeInput(typed))
        val remote = PlayerNameRules.forDisplay(raw, "Player_ab12")
        assertEquals(remote, PlayerNameRules.forDisplay(remote, "Player_ab12"))
    }
}
