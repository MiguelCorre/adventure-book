package com.adventurebook.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.adventurebook.book.Book;
import com.adventurebook.book.ConsequenceType;
import com.adventurebook.book.LoadedBook;
import com.adventurebook.book.ValidationReport;
import com.adventurebook.book.ValidationRule;
import com.adventurebook.book.testsupport.Books;
import com.adventurebook.game.GameExceptions.BookNotPlayableException;
import com.adventurebook.game.GameExceptions.GameFinishedException;
import com.adventurebook.game.GameExceptions.InvalidChoiceException;
import com.adventurebook.game.GameExceptions.SectionNotFoundException;

class GameEngineTest {

    private static final int FULL_HEALTH = 10;

    private final GameEngine engine = new GameEngine(FULL_HEALTH);

    private LoadedBook playable(Book book) {
        return new LoadedBook("test-book", book, ValidationReport.valid());
    }

    @Nested
    @DisplayName("starting a game")
    class Starting {

        @Test
        void opensAtTheBeginningWithAFullHealthBar() {
            LoadedBook book = playable(Books.of(Books.begin("1", Books.goTo("On", "2")), Books.end("2")));

            GameSession session = engine.start(book);

            assertThat(session.sectionId()).isEqualTo("1");
            assertThat(session.health()).isEqualTo(FULL_HEALTH);
            assertThat(session.status()).isEqualTo(GameStatus.IN_PROGRESS);
            assertThat(session.lastConsequence()).isNull();
            assertThat(session.bookSlug()).isEqualTo("test-book");
            assertThat(session.id()).isNotNull();
        }

        @Test
        void givesEveryGameItsOwnHandle() {
            LoadedBook book = playable(Books.of(Books.begin("1", Books.goTo("On", "2")), Books.end("2")));

            assertThat(engine.start(book).id()).isNotEqualTo(engine.start(book).id());
        }

        @Test
        void refusesToStartABookThatFailedValidation() {
            LoadedBook broken = new LoadedBook("broken", null, ValidationReport.unreadable("empty file"));

            assertThatThrownBy(() -> engine.start(broken))
                    .isInstanceOf(BookNotPlayableException.class)
                    .hasMessageContaining("broken");
        }
    }

    @Nested
    @DisplayName("walking through a book")
    class Navigation {

        @Test
        void followsTheChosenOptionToItsSection() {
            LoadedBook book = playable(Books.of(
                    Books.begin("1", Books.goTo("Left", "2"), Books.goTo("Right", "3")),
                    Books.node("2", Books.goTo("On", "3")),
                    Books.end("3")));

            GameSession afterChoice = engine.choose(book, engine.start(book), 0);

            assertThat(afterChoice.sectionId()).isEqualTo("2");
            assertThat(afterChoice.status()).isEqualTo(GameStatus.IN_PROGRESS);
            assertThat(afterChoice.health()).isEqualTo(FULL_HEALTH);
        }

        @Test
        void keepsTheOriginalSessionUntouched() {
            LoadedBook book = playable(Books.of(
                    Books.begin("1", Books.hurts("Ouch", "2", 3)), Books.end("2")));
            GameSession started = engine.start(book);

            engine.choose(book, started, 0);

            assertThat(started.sectionId()).isEqualTo("1");
            assertThat(started.health()).isEqualTo(FULL_HEALTH);
        }

        @Test
        void rejectsAnOptionIndexThatDoesNotExist() {
            LoadedBook book = playable(Books.of(Books.begin("1", Books.goTo("Only", "2")), Books.end("2")));
            GameSession session = engine.start(book);

            assertThatThrownBy(() -> engine.choose(book, session, 1)).isInstanceOf(InvalidChoiceException.class);
            assertThatThrownBy(() -> engine.choose(book, session, -1)).isInstanceOf(InvalidChoiceException.class);
        }

        @Test
        void reportsASessionPointingAtAMissingSection() {
            LoadedBook book = playable(Books.of(Books.begin("1", Books.goTo("On", "2")), Books.end("2")));
            GameSession adrift = new GameSession(engine.start(book).id(), "test-book", "nowhere", FULL_HEALTH,
                    GameStatus.IN_PROGRESS, null);

            assertThatThrownBy(() -> engine.choose(book, adrift, 0)).isInstanceOf(SectionNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("health")
    class Health {

        @Test
        void subtractsDamageAndReportsWhatCausedIt() {
            LoadedBook book = playable(Books.of(
                    Books.begin("1", Books.hurts("Squeeze through", "2", 4)),
                    Books.node("2", Books.goTo("On", "3")),
                    Books.end("3")));

            GameSession session = engine.choose(book, engine.start(book), 0);

            assertThat(session.health()).isEqualTo(6);
            assertThat(session.lastConsequence().type()).isEqualTo(ConsequenceType.LOSE_HEALTH);
            assertThat(session.lastConsequence().value()).isEqualTo(4);
        }

        @Test
        void addsHealingUpToButNeverAboveTheStartingBar() {
            LoadedBook book = playable(Books.of(
                    Books.begin("1", Books.hurts("Get hurt", "2", 3)),
                    Books.node("2", Books.heals("Rest well", "3", 9)),
                    Books.node("3", Books.goTo("On", "4")),
                    Books.end("4")));

            GameSession wounded = engine.choose(book, engine.start(book), 0);
            GameSession healed = engine.choose(book, wounded, 0);

            assertThat(wounded.health()).isEqualTo(7);
            assertThat(healed.health()).as("7 + 9 is capped at the starting bar").isEqualTo(FULL_HEALTH);
        }

        @Test
        void leavesHealthAloneWhenAChoiceHasNoConsequence() {
            LoadedBook book = playable(Books.of(Books.begin("1", Books.goTo("On", "2")), Books.end("2")));

            GameSession session = engine.choose(book, engine.start(book), 0);

            assertThat(session.health()).isEqualTo(FULL_HEALTH);
            assertThat(session.lastConsequence()).isNull();
        }
    }

    @Nested
    @DisplayName("ending the adventure")
    class Endings {

        @Test
        void winsOnReachingAnEndSection() {
            LoadedBook book = playable(Books.of(Books.begin("1", Books.goTo("Escape", "2")), Books.end("2")));

            GameSession session = engine.choose(book, engine.start(book), 0);

            assertThat(session.status()).isEqualTo(GameStatus.WON);
            assertThat(session.sectionId()).isEqualTo("2");
        }

        @Test
        void diesWhenDamageEmptiesTheHealthBarExactly() {
            LoadedBook book = playable(Books.of(
                    Books.begin("1", Books.hurts("Fatal", "2", FULL_HEALTH)),
                    Books.node("2", Books.goTo("On", "3")),
                    Books.end("3")));

            GameSession session = engine.choose(book, engine.start(book), 0);

            assertThat(session.status()).isEqualTo(GameStatus.DEAD);
            assertThat(session.health()).isZero();
        }

        @Test
        void floorsHealthAtZeroRatherThanGoingNegative() {
            LoadedBook book = playable(Books.of(
                    Books.begin("1", Books.hurts("Very fatal", "2", 99)),
                    Books.node("2", Books.goTo("On", "3")),
                    Books.end("3")));

            assertThat(engine.choose(book, engine.start(book), 0).health()).isZero();
        }

        @Test
        void keepsTheDyingPlayerOnTheSectionTheyDiedOnSoTheReasonCanBeShown() {
            LoadedBook book = playable(Books.of(
                    Books.begin("1", Books.hurts("Fatal", "2", FULL_HEALTH)),
                    Books.node("2", Books.goTo("On", "3")),
                    Books.end("3")));

            GameSession session = engine.choose(book, engine.start(book), 0);

            assertThat(session.sectionId()).isEqualTo("1");
            assertThat(session.lastConsequence()).isNotNull();
        }

        @Test
        @DisplayName("a fatal choice is fatal even when it pointed at an ending")
        void deathTakesPrecedenceOverReachingAnEnding() {
            LoadedBook book = playable(Books.of(
                    Books.begin("1", Books.hurts("Pyrrhic victory", "2", FULL_HEALTH)),
                    Books.end("2")));

            GameSession session = engine.choose(book, engine.start(book), 0);

            assertThat(session.status()).isEqualTo(GameStatus.DEAD);
            assertThat(session.sectionId()).isEqualTo("1");
        }

        @Test
        void refusesFurtherChoicesOnceTheGameIsOver() {
            LoadedBook book = playable(Books.of(Books.begin("1", Books.goTo("Escape", "2")), Books.end("2")));
            GameSession won = engine.choose(book, engine.start(book), 0);

            assertThatThrownBy(() -> engine.choose(book, won, 0))
                    .isInstanceOf(GameFinishedException.class)
                    .hasMessageContaining("WON");
        }
    }

    @Nested
    @DisplayName("resuming saved progress")
    class Resuming {

        private final LoadedBook book = playable(Books.of(
                Books.begin("1", Books.goTo("On", "2")),
                Books.node("2", Books.goTo("On", "3")),
                Books.end("3")));

        @Test
        void restoresTheSavedSectionAndHealth() {
            GameSession resumed = engine.resume(book, "2", 4);

            assertThat(resumed.sectionId()).isEqualTo("2");
            assertThat(resumed.health()).isEqualTo(4);
            assertThat(resumed.status()).isEqualTo(GameStatus.IN_PROGRESS);
        }

        @Test
        void issuesAFreshHandleSoTheOldSessionIsNotReused() {
            assertThat(engine.resume(book, "2", 4).id()).isNotEqualTo(engine.resume(book, "2", 4).id());
        }

        @Test
        void refusesToResumeIntoASectionTheBookNoLongerHas() {
            assertThatThrownBy(() -> engine.resume(book, "removed", 5))
                    .isInstanceOf(SectionNotFoundException.class);
        }

        @Test
        void boundsSavedHealthToTheAllowedRange() {
            assertThat(engine.resume(book, "2", 999).health()).isEqualTo(FULL_HEALTH);
            assertThat(engine.resume(book, "2", -5).status()).isEqualTo(GameStatus.DEAD);
        }
    }

    @Test
    void reportsWhichRuleMadeABookUnplayable() {
        LoadedBook trapped = new LoadedBook("trapped", Books.of(Books.begin("1"), Books.end("2")),
                new ValidationReport(java.util.List.of(
                        com.adventurebook.book.ValidationIssue.at(ValidationRule.NO_DEAD_ENDS, "1", "trapped"))));

        assertThatThrownBy(() -> engine.start(trapped)).isInstanceOf(BookNotPlayableException.class);
    }
}
