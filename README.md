# Adventure Book

An interactive adventure book: pick a story, read a section, choose what happens next, and
try to reach an ending before your health runs out.

Java 21 / Spring Boot backend, Angular frontend.

---

## Prerequisites

| Tool | Version | Notes |
| ---- | ------- | ----- |
| JDK | 21+ | `java -version` |
| Node.js | 20+ | `node -v` |

Maven is **not** required — the project ships the Maven wrapper.

## Run it

Two terminals.

**Backend** (http://localhost:8080):

```bash
cd backend && ./mvnw spring-boot:run
```

On Windows use `mvnw.cmd spring-boot:run`.

**Frontend** (http://localhost:4200):

```bash
cd frontend && npm install && npm start
```

Then open http://localhost:4200. The dev server proxies `/api` to the backend, so both need
to be running.

## Tests

```bash
cd backend && ./mvnw verify
```

```bash
cd frontend && npm test
```

129 backend tests and 58 frontend tests. Both suites run without a database, a browser or a
network connection.

End-to-end, in a real browser (starts both halves of the application itself):

```bash
cd frontend && npm run e2e:install && npm run e2e
```

14 specs covering the ground the unit suites structurally cannot reach: what is actually
painted, real browser history, and real layout. They found three defects that unit tests
had no way to see — a duplicated ending passage, choices offered to a dead player, and a
scroll position that jumped when the result set shrank. The backend runs against an
in-memory database for these, so a run never inherits saved games from the last one.

---

## What the books look like

Books are JSON files in [`books/`](books/). The directory is configurable via
`adventure.books-dir` (see [`application.yml`](backend/src/main/resources/application.yml)).

```json
{
  "title": "The Prisoner",
  "author": "Daniel El Fuego",
  "difficulty": "HARD",
  "sections": [
    {
      "id": 1,
      "text": "You wake up in what seems to be a dark prison cell.",
      "type": "BEGIN",
      "options": [
        { "description": "You try to open the door", "gotoId": 500 },
        {
          "description": "You look under the bed",
          "gotoId": 20,
          "consequence": { "type": "LOSE_HEALTH", "value": "6", "text": "You cut yourself on a rusty nail." }
        }
      ]
    },
    { "id": "500", "text": "The door is locked.", "type": "END" }
  ]
}
```

A book is **invalid**, and cannot be played, if any of these hold:

| Rule | Meaning |
| ---- | ------- |
| `UNIQUE_BEGIN` | not exactly one `BEGIN` section |
| `HAS_END` | no `END` section at all (several are fine) |
| `VALID_REFERENCES` | a `gotoId` names a section that does not exist |
| `NO_DEAD_ENDS` | a non-`END` section offers no choices |
| `UNIQUE_IDS` | two sections share an identifier |
| `UNREADABLE` | the file is not readable as a book |

The first four are the rules stated in the brief. The last two are explained under
[Decisions](#decisions).

---

## The books that came with the exercise

**All four of them are invalid**, each in a different way. That turned out to be the most
interesting part of the exercise, so the application surfaces exactly why rather than
hiding it, and
[`SuppliedBooksTest`](backend/src/test/java/com/adventurebook/book/SuppliedBooksTest.java)
pins the findings down:

| File | Problem |
| ---- | ------- |
| `dragon-quest.json` | the file is empty — it never becomes a book |
| `the-prisoner.json` | section `666` is a `NODE` with no way out |
| `crystal-caverns.json` | the same trap, and this one is reachable, from section `900` |
| `pirates-jade-sea.json` | **both** opening choices are broken: `gotoId: 999` was never written, and `gotoId: 666` is another dead end |

`the-prisoner.json` also mixes identifier types — it declares `"id": "500"` but reaches it
with `"gotoId": 500`. That is *not* a broken reference, and reporting it as one would have
been wrong, so identifiers are normalised to a canonical string before anything is compared.

Since none of the supplied books can be played, the library ships two written for this
exercise: **The Clockwork Lighthouse** (medium, two endings, a safe route, a lethal route
and a heal that tests the health ceiling) and **The Sunken Orchard** (easy, short).

---

## Objectives

| # | Objective | Where |
| - | --------- | ----- |
| 1 | Home page listing all books, with search and filter | `features/library/` + `GET /api/books` |
| 2 | Start a game and navigate | `features/game/` + `POST /api/games`, `/choices` |
| 3 | Consequences, health, game end | `game/GameEngine.java` |
| 4 | Save progression | `save/` + `POST /api/games/{id}/save` |
| 5 | Add new books | `book/BookUploadService.java` + `POST /api/books` |

The git history follows the same order.

## API

| Method | Path | Purpose |
| ------ | ---- | ------- |
| `GET` | `/api/books?query=&difficulty=EASY,HARD` | list the library |
| `GET` | `/api/books/{slug}` | one book, with its validation report |
| `POST` | `/api/books` | add a book (multipart `file`) |
| `POST` | `/api/games` | start, or continue with `{"fromSave": true}` |
| `POST` | `/api/games/{id}/choices` | take a turn |
| `GET` | `/api/games/{id}` | current state |
| `POST` | `/api/games/{id}/save` | save progress |

Failures are [RFC 7807](https://datatracker.ietf.org/doc/html/rfc7807) problem documents.
A rejected upload carries the full list of validation issues in an `issues` property.

---

## Decisions

**The game is resolved on the server.** The client is told the section text, the words on
each button and the player's health — never where a choice leads or what it will cost. A
reader with the network tab open learns no more than a reader holding the paperback. It
also means one copy of the rules exists in the system rather than two that can disagree.

**Invalid books are listed, not hidden.** The brief asks the home page to list *all* books.
A reader is better served by a broken book with an explanation than by one that silently
vanished, so the card shows the reasons and disables the button.

**Every problem is reported at once.** Validation does not stop at the first failure. The
library explains everything wrong with a book in one place, and an upload returns the whole
list so a curator can fix it in one pass.

**Identifiers are strings.** The supplied books mix `"id": "500"` with `"gotoId": 500`.
Comparing raw JSON types would invent a broken reference that is not there.

**Unique identifiers are a rule, though the brief does not list one.** Without it a
`gotoId` could resolve to two different sections and a play-through would stop being
deterministic.

**Health is clamped to `[0, 10]`.** The brief fixes the starting value at 10 and says the
player dies at zero. Capping healing at the starting value keeps the ceiling meaningful;
both bounds are configurable through `adventure.starting-health`.

**A fatal choice is fatal even if it pointed at an ending.** The brief leaves this open.
Consequences settle before the player moves, so a choice that empties the bar kills them —
they do not survive to read the ending. The dying player also stays on the section that
killed them, which is how the game-over screen can name the blow.

**Sessions live in memory, saves live in H2.** A session is a browser tab someone has open;
losing it on restart costs nothing that cannot be rebuilt. Progress worth keeping is what
saving is for, so it goes to a file-backed database — and survives a restart, which is
worth checking by hand.

**One save slot per book.** A bookmark, not a history. Saving again overwrites, which keeps
the library card down to a single *Continue*.

**An uploaded file never names itself on disk.** The stored filename is derived from the
book's own title through an allow-list of characters. A client-supplied filename is
untrusted input and would be a path traversal waiting to happen.

**No Lombok, no NgRx, no component library.** Java 21 records and Angular signals cover
what those would have been used for, and the design in the brief is small enough to write
directly.

## Not included

- **Authentication.** Single player by design; saves are global.
- **A successful upload end-to-end.** There is no delete endpoint, so the happy path would
  leave a book behind in the working tree on every run. It is covered by `BookUploadTest`
  instead, which uses a scratch directory it can clean up.
- **Watching the books directory.** Reloading happens on startup and after an upload, which
  is predictable and avoids reacting to a half-written file.

## Layout

```
books/                     book JSON files, including the four supplied ones
backend/
  src/main/java/com/adventurebook/
    book/                  domain model, loading, validation, upload
    game/                  engine, sessions, rules
    save/                  persistent progress
    api/                   controllers, DTOs, error handling
    config/                properties, Jackson, CORS
frontend/
  e2e/                     Playwright specs, run against both halves for real
  src/app/
    core/                  API clients and typed models
    features/library/      objective 1 and 5
    features/game/         objectives 2, 3 and 4
```
