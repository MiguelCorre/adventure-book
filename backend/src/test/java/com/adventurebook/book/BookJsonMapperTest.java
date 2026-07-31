package com.adventurebook.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BookJsonMapperTest {

    private final BookJsonMapper mapper = new BookJsonMapper();

    private Book parse(String json) {
        return mapper.read(json.getBytes(StandardCharsets.UTF_8));
    }

    @Nested
    @DisplayName("reads the shape used by the supplied books")
    class HappyPath {

        @Test
        void mapsMetadataAndSections() {
            Book book = parse("""
                    {
                      "title": "The Prisoner",
                      "author": "Daniel El Fuego",
                      "difficulty": "HARD",
                      "sections": [
                        { "id": 1, "text": "You wake up.", "type": "BEGIN",
                          "options": [ { "description": "Open the door", "gotoId": 2 } ] },
                        { "id": 2, "text": "You are free.", "type": "END" }
                      ]
                    }
                    """);

            assertThat(book.title()).isEqualTo("The Prisoner");
            assertThat(book.author()).isEqualTo("Daniel El Fuego");
            assertThat(book.difficulty()).isEqualTo(Difficulty.HARD);
            assertThat(book.sections()).hasSize(2);
            assertThat(book.sections().getFirst().type()).isEqualTo(SectionType.BEGIN);
            assertThat(book.sections().getLast().isEnding()).isTrue();
        }

        @Test
        void readsConsequenceWithStringEncodedValue() {
            Book book = parse("""
                    {
                      "title": "Cuts", "sections": [
                        { "id": 1, "text": "Reach under the bed.", "type": "NODE",
                          "options": [ {
                            "description": "Scan with your hands", "gotoId": 2,
                            "consequence": { "type": "LOSE_HEALTH", "value": "6",
                                             "text": "A rusty nail cuts you." } } ] }
                      ]
                    }
                    """);

            Consequence consequence = book.sections().getFirst().options().getFirst().consequence();
            assertThat(consequence.type()).isEqualTo(ConsequenceType.LOSE_HEALTH);
            assertThat(consequence.value()).isEqualTo(6);
            assertThat(consequence.text()).isEqualTo("A rusty nail cuts you.");
        }

        @Test
        void toleratesUnknownPropertiesSoExtraMetadataDoesNotBreakABook() {
            Book book = parse("""
                    {
                      "title": "Extras", "coverArt": "dragon.png", "chapters": 12,
                      "sections": [ { "id": 1, "text": "End.", "type": "END", "mood": "grim" } ]
                    }
                    """);

            assertThat(book.title()).isEqualTo("Extras");
            assertThat(book.sections()).hasSize(1);
        }

        @Test
        void defaultsMissingCollectionsAndMetadataToSafeValues() {
            Book book = parse("""
                    { "title": "Bare", "sections": [ { "id": 1, "text": "End.", "type": "END" } ] }
                    """);

            assertThat(book.author()).isNull();
            assertThat(book.difficulty()).isNull();
            assertThat(book.sections().getFirst().options()).isEmpty();
            assertThat(book.sections().getFirst().hasOptions()).isFalse();
        }
    }

    @Nested
    @DisplayName("normalises section identifiers")
    class IdentifierNormalisation {

        /** The supplied the-prisoner.json declares "500" but references it as 500. */
        @Test
        void treatsNumericAndQuotedIdentifiersAsTheSameSection() {
            Book book = parse("""
                    {
                      "title": "Mixed ids", "sections": [
                        { "id": 1, "text": "Start.", "type": "BEGIN",
                          "options": [ { "description": "Try the door", "gotoId": 500 } ] },
                        { "id": "500", "text": "Locked.", "type": "END" }
                      ]
                    }
                    """);

            String reference = book.sections().getFirst().options().getFirst().gotoId();
            assertThat(reference).isEqualTo("500");
            assertThat(book.section(reference)).containsSame(book.sections().getLast());
        }

        @Test
        void trimsIncidentalWhitespaceAroundIdentifiers() {
            Book book = parse("""
                    {
                      "title": "Padded", "sections": [
                        { "id": " 42 ", "text": "Start.", "type": "BEGIN",
                          "options": [ { "description": "Onwards", "gotoId": "42 " } ] }
                      ]
                    }
                    """);

            Section section = book.sections().getFirst();
            assertThat(section.id()).isEqualTo("42");
            assertThat(section.options().getFirst().gotoId()).isEqualTo("42");
        }

        @Test
        void keepsFirstDeclarationWhenIdentifiersRepeat() {
            Book book = parse("""
                    {
                      "title": "Clashing", "sections": [
                        { "id": 1, "text": "First.", "type": "BEGIN",
                          "options": [ { "description": "On", "gotoId": 2 } ] },
                        { "id": 1, "text": "Second.", "type": "END" }
                      ]
                    }
                    """);

            assertThat(book.sectionsById()).hasSize(1);
            assertThat(book.sectionsById().get("1").text()).isEqualTo("First.");
        }
    }

    @Nested
    @DisplayName("reports unreadable files instead of crashing")
    class ParseFailures {

        @Test
        void rejectsAnEmptyFile() {
            assertThatThrownBy(() -> mapper.read(new byte[0]))
                    .isInstanceOf(BookParseException.class)
                    .hasMessageContaining("empty");
        }

        @Test
        void rejectsMalformedJson() {
            assertThatThrownBy(() -> parse("{ \"title\": \"Broken\", "))
                    .isInstanceOf(BookParseException.class);
        }

        @Test
        void rejectsNonNumericConsequenceValue() {
            assertThatThrownBy(() -> parse("""
                    {
                      "title": "Bad value", "sections": [
                        { "id": 1, "text": "Start.", "type": "NODE",
                          "options": [ { "description": "Go", "gotoId": 2,
                            "consequence": { "type": "LOSE_HEALTH", "value": "a lot",
                                             "text": "Ouch." } } ] }
                      ]
                    }
                    """))
                    .isInstanceOf(BookParseException.class);
        }

        @Test
        void rejectsAConsequenceWithoutAType() {
            assertThatThrownBy(() -> parse("""
                    { "title": "Missing type", "sections": [
                      { "id": 1, "text": "Start.", "type": "NODE", "options": [
                        { "description": "Go", "gotoId": 2,
                          "consequence": { "value": "1", "text": "Something happens." } }
                      ] }
                    ] }
                    """))
                    .isInstanceOf(BookParseException.class)
                    .hasMessageContaining("type is required");
        }

        @Test
        void rejectsAConsequenceWithNoEffect() {
            assertThatThrownBy(() -> parse("""
                    { "title": "No effect", "sections": [
                      { "id": 1, "text": "Start.", "type": "NODE", "options": [
                        { "description": "Go", "gotoId": 2,
                          "consequence": { "type": "GAIN_HEALTH", "value": "0", "text": "Nothing happens." } }
                      ] }
                    ] }
                    """))
                    .isInstanceOf(BookParseException.class)
                    .hasMessageContaining("value must be positive");
        }

        @Test
        void rejectsAConsequenceWithoutNarration() {
            assertThatThrownBy(() -> parse("""
                    { "title": "Missing narration", "sections": [
                      { "id": 1, "text": "Start.", "type": "NODE", "options": [
                        { "description": "Go", "gotoId": 2,
                          "consequence": { "type": "LOSE_HEALTH", "value": "1", "text": "   " } }
                      ] }
                    ] }
                    """))
                    .isInstanceOf(BookParseException.class)
                    .hasMessageContaining("text is required");
        }

        @Test
        void rejectsASectionWithoutText() {
            assertThatThrownBy(() -> parse("""
                    { "title": "Missing text", "sections": [
                      { "id": 1, "type": "END" }
                    ] }
                    """))
                    .isInstanceOf(BookParseException.class)
                    .hasMessageContaining("section text is required");
        }

        @Test
        void rejectsASectionWithoutAType() {
            assertThatThrownBy(() -> parse("""
                    { "title": "Missing type", "sections": [
                      { "id": 1, "text": "Where am I?" }
                    ] }
                    """))
                    .isInstanceOf(BookParseException.class)
                    .hasMessageContaining("section type is required");
        }

        @Test
        void rejectsAChoiceWithoutADescription() {
            assertThatThrownBy(() -> parse("""
                    { "title": "Missing choice text", "sections": [
                      { "id": 1, "text": "Choose.", "type": "BEGIN", "options": [
                        { "gotoId": 2 }
                      ] },
                      { "id": 2, "text": "Done.", "type": "END" }
                    ] }
                    """))
                    .isInstanceOf(BookParseException.class)
                    .hasMessageContaining("option description is required");
        }

        @Test
        void rejectsUnknownSectionType() {
            assertThatThrownBy(() -> parse("""
                    { "title": "Odd", "sections": [ { "id": 1, "text": "?", "type": "MIDDLE" } ] }
                    """))
                    .isInstanceOf(BookParseException.class);
        }
    }
}
