# Adventure Book

[![CI](https://github.com/MiguelCorre/adventure-book/actions/workflows/ci.yml/badge.svg)](https://github.com/MiguelCorre/adventure-book/actions/workflows/ci.yml)

An interactive adventure book: pick a story, read a section, choose what happens next, and
try to reach an ending before your health runs out.

Java 21 / Spring Boot 4.1.0 backend, Angular 22 frontend.

---

## Prerequisites

| Tool | Version | Notes |
| ---- | ------- | ----- |
| JDK | 21+ | `java -version` |
| Node.js | `^22.22.3 \|\| ^24.15.0 \|\| >=26.0.0` | `node -v` |

Maven is **not** required — the project ships the Maven wrapper. In the commands below,
Windows PowerShell users should replace `./mvnw` with `.\mvnw.cmd`.

## Build

Build and test the executable backend jar:

```bash
cd backend
./mvnw clean package
```

The jar is written to `backend/target/adventure-book-0.0.1-SNAPSHOT.jar`.

Install the locked frontend dependencies and create a production build:

```bash
cd frontend
npm ci
npm run build
```

The static frontend is written to `frontend/dist/frontend/`.

## Run it

From the repository root, start both development servers with one command:

```bash
npm start
```

The launcher installs the locked frontend dependencies, starts the backend on
http://localhost:8080 and the frontend on http://localhost:4200. Open
http://localhost:4200; the development server proxies `/api` to the backend. Press
`Ctrl+C` once to stop both processes.

To run either half independently, use two terminals instead.

**Backend** (http://localhost:8080):

```bash
cd backend
./mvnw spring-boot:run
```

**Frontend** (http://localhost:4200):

```bash
cd frontend
npm ci
npm start
```

When using this mode, both terminals must remain running while the application is in use.

## Tests

```bash
cd backend
./mvnw verify
```

```bash
cd frontend
npm test
```

150 backend tests and 75 frontend tests. Once dependencies are installed, both suites run
without an external database, a browser or a network connection; backend persistence tests
use embedded H2.

End-to-end, in a real browser (starts both halves of the application itself):

```bash
cd frontend
npm run e2e:install
npm run e2e
```

16 specs covering the ground the unit suites structurally cannot reach: what is actually
painted, real browser history, and real layout. They found three defects that unit tests
had no way to see — a duplicated ending passage, choices offered to a dead player, and a
scroll position that jumped when the result set shrank. The backend runs against an
in-memory database for these, so a run never inherits saved games from the last one. Its
book catalogue is a disposable copy under `backend/target/`, recreated before the suite and
removed afterwards, so the successful-upload scenario never changes the working tree.

---

## What the books look like

Books are JSON files in [`books/`](books/). The directory is configurable via
`adventure.books-dir` (see [`application.yml`](backend/src/main/resources/application.yml)).

The initial catalogue contains exactly the four files supplied with the assessment. They are
kept unchanged and intentionally remain unplayable, allowing the library to demonstrate its
validation diagnostics honestly. [`upload-samples/`](upload-samples/) contains two original,
valid books that are not loaded at startup; use **Add book** to upload either one during a demo
and immediately unlock gameplay.

```json
{
  "title": "The Brass Key",
  "author": "Example Author",
  "description": "A short escape through a silent observatory.",
  "tags": ["Mystery", "Short"],
  "difficulty": "EASY",
  "sections": [
    {
      "id": 1,
      "title": "The Locked Dome",
      "text": "The observatory door clicks shut behind you.",
      "type": "BEGIN",
      "options": [
        { "description": "Try the brass key", "gotoId": 2 }
      ]
    },
    { "id": 2, "title": "Under Open Sky", "text": "The key turns.", "type": "END" }
  ]
}
```

`description`, `tags` and each section's `title` are optional presentation metadata. Books
without them remain valid and render without empty placeholders; none of these fields changes
the story graph or its validation result. Reading time is derived from section prose at 200
words per minute (rounded up), so authors do not maintain duplicate metadata. Unknown extra
properties are also ignored so the loader can accept richer book files without weakening the
structural rules below.

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

Unreadable JSON is reported with its JSON path and line/column; catalogue failures also
name the source filename, and section invariants include the id when it was available.

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
| `DELETE` | `/api/books/{slug}/save` | discard saved progress (idempotent) |

Failures are [RFC 7807](https://datatracker.ietf.org/doc/html/rfc7807) problem documents.
A rejected upload carries the full list of validation issues in an `issues` property.

---

## Decisions

**Framework versions are explicit.** The brief requires Spring Boot and Angular but does
not pin their releases. This implementation uses Spring Boot 4.1.0 and Angular 22; the
Maven POM and npm lockfile are the source of truth, and this README mirrors them.

**Frontend contracts are compiled strictly.** TypeScript `strict` mode and Angular's
`strictTemplates` are both enabled. Template references preserve concrete DOM element
types, and status checks narrow the game-over state without opting out through `$any`.

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
worth checking by hand. The registry retains at most 1,000 sessions and evicts the oldest
when full, so abandoned tabs cannot make memory usage grow without limit.

**One save slot per book.** A bookmark, not a history. Saving again overwrites, and the
card offers a single *Continue* plus a way to discard the slot — the one destructive act
in the interface, and the only one that asks for confirmation first. Persistence uses
Spring Data JPA merge semantics rather than dialect-specific SQL, keeping this code portable
from embedded H2 to Oracle. Same-book saves are serialized per application instance before
the repository transaction begins, so simultaneous first saves cannot race. In a
horizontally scaled Oracle deployment, that boundary would move to the database through an
Oracle `MERGE` or a unique-constraint retry policy.

**An uploaded file never names itself on disk.** The stored filename is derived from the
book's own title through an allow-list of characters. A client-supplied filename is
untrusted input and would be a path traversal waiting to happen.

**No Lombok, no NgRx, no component library.** Java 21 records and Angular signals cover
what those would have been used for, and the design in the brief is small enough to write
directly.

## Not included

- **Authentication.** Single player by design; saves are global.
- **Watching the books directory.** Reloading happens on startup and after an upload, which
  is predictable and avoids reacting to a half-written file.

## Layout

```
books/                     the four supplied assessment JSON files (unchanged)
upload-samples/            two original valid books for demonstrating upload and gameplay
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
