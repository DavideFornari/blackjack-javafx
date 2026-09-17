# CLAUDE.md

Guidance for Claude Code sessions working in this repository.

## What this is

A graphical, single-player Blackjack table built with JavaFX — a modernization of
[DavideFornari/BlackJack](https://github.com/DavideFornari/BlackJack), a terminal Java
university project from 2019 (*Programmazione con Laboratorio*, University of Verona).
The original was a console app with no build file, Italian class names (`Carta`,
`Mazzo`, `Mano`, `Giocatore`, `Banco`, `Partita`), and — as documented below — a real
scoring bug. This rewrite keeps the feature set (multi-deck shoe, betting, hit/stand/
double/split, four card-counting systems) but fixes the bug, gives it a Maven build,
and replaces `Scanner`-driven I/O with a JavaFX GUI.

## Tech stack

- **JDK 21** (LTS) + **JavaFX 21.0.7** via Maven (`org.openjfx`, `win` classifier —
  this project currently targets Windows only, see roadmap)
- **Maven** for the build; **JUnit 5** for tests
- No FXML — the scene graph is wired directly in `GameController` (a deliberate choice
  for a UI this size: every binding is grep-able in one file instead of split across
  XML + a controller class)
- No external card image assets — cards render as styled text/shapes, avoiding both a
  licensing question and a binary-asset pipeline

## Build & run

```bash
mvn test          # engine unit tests — no JavaFX runtime needed
mvn javafx:run     # launches the app
```

**Verified 2026-09-14**: `mvn test` passes 23/23 (0 failures), and `mvn javafx:run`
launches a working window — confirmed visually by the project owner, not just by log
output (a `BUILD SUCCESS` from this plugin isn't on its own proof the window rendered,
since it also exits cleanly if the JavaFX toolkit can't attach to a display). JDK 21,
Maven, and GitHub CLI were installed via winget/Chocolatey as part of this setup.

**Verified again 2026-09-16**, after the chip-image/wager-stack/banner/window-sizing
work below: still 23/23, and the running window (chip stacks, split, double, the
win/lose banner, and the window auto-fit through several launches) was confirmed
visually by the project owner across multiple rounds of feedback, not just by a
successful compile.

**Verified again 2026-09-17/18**, after the insurance side bet and the window icon
below: `mvn test` passes 28/28 (five new insurance tests), and the running window —
the insurance toggle and overlay (with its delayed pop-in and hand preview), the
combined "INSURANCE WIN" banner, the now-always-shown round banner (including
"PUSH +0"), and the new Ace/Jack window icon — was confirmed visually by the project
owner across many rounds of feedback, including two rounds where the icon *looked*
right in a screenshot but was actually still broken (see item 9 below) — a reminder
that "no crash" and "the Image object has the right pixels" are both necessary but not
sufficient; only an actual look at the running window (or, in that case, a live
screenshot checked pixel-by-pixel) settles it.

## Architecture

```
src/main/java/io/github/davidefornari/blackjack/
  engine/   pure Java, zero JavaFX dependency, fully unit-tested
  ui/       JavaFX, depends on engine
src/test/java/.../engine/
```

`engine/`: `Card` / `Rank` / `Suit` (model) → `Hand` (cards + scoring — the soft-Ace
fix lives here) → `Shoe` (multi-deck, shuffle, penetration-based reshuffle, running
counts for all four counting systems at once) → `Dealer` / `Player` → `GameRules`
(configurable house rules, a record) → `BlackjackTable` (orchestrates one round: deal →
optional insurance decision → player turns → dealer turn → settle) → `RoundOutcome` /
`Settlement` / `InsuranceSettlement`.

`ui/`: `BlackjackApp` (entry point, also loads the window icon via `AppIcon`) →
`GameController` (owns the `BlackjackTable`, builds and refreshes the whole scene
graph, all button wiring) → `CardView` / `HandPane` / `ChipView` (reusable view
components) / `AppIcon` (loads the window/taskbar icon image).

The engine has no knowledge of JavaFX and no knowledge of Scanner/console I/O either —
keep it that way. It's what makes `BlackjackTableTest` possible without a display, and
would let a second UI (CLI, web, whatever) reuse it unchanged.

## Naming & file conventions

Written down after a session where an uploaded reference image, its processed build
artifact, and a pile of ad-hoc debug screenshots all needed somewhere to live — follow
these rather than improvising per-file.

**Java code**
- Packages: `io.github.davidefornari.blackjack.{engine,ui}` only — nothing deeper.
  `engine` has zero JavaFX dependency; `ui` depends on `engine`, never the reverse.
- Classes: `PascalCase`, one public class per file, filename matches the class name
  exactly.
- A reusable JavaFX scene-graph component gets a `View`/`Pane` suffix naming what it
  *is* (`CardView`, `HandPane`, `ChipView`) — not what it's *for*. A class that isn't
  itself a Node (a static loader/factory, a plain data holder) doesn't take that
  suffix (`AppIcon`, `GameRules`).
- Immutable data carriers are Java `record`s named for the concept itself — no
  `Impl`/`Data`/`DTO`/`Bean` suffixes (`Settlement`, `RoundOutcome`,
  `InsuranceSettlement`, `GameRules`).
- Methods: `camelCase` verbs for actions (`startRound`, `takeInsurance`,
  `playDealerTurn`); `isX`/`hasX`/`canX` for booleans (`isPlayerTurnComplete`,
  `hasBlackjack`, `canSplit`).

**Tests**
- Test class = `<ClassUnderTest>Test` (`BlackjackTableTest`, `HandTest`), same package,
  under `src/test/java/...`.
- Test methods: one long descriptive `camelCase` sentence stating the scenario *and*
  the expected outcome — no `test` prefix, no numbering, no abbreviations
  (`dealerBlackjackEndsTheRoundBeforeThePlayerCanAct`,
  `takingInsuranceAgainstADealerBlackjackPaysTwoToOne`). If the sentence needs "and" to
  cover two behaviors, it's usually two tests instead.

**CSS classes** (`blackjack.css`)
- `kebab-case`, always. A class that belongs to one specific reusable component is
  prefixed with that component's name: `setup-*` (setup screen and every in-theme
  overlay), `hand-*` (`HandPane`), `card-*` (`CardView`), `chip-*` (chip rail/buttons),
  `win-lose-banner*` (the outcome banner). A class used globally — generic labels,
  buttons, top-level layout regions (`primary-button`, `error-label`, `message-label`,
  `status-bar`, `controls-area`, `table-layout`, `count-badge`, `bet-total-badge`) —
  stays unprefixed; don't force one of the component prefixes onto something that
  genuinely isn't scoped to that component.
- A state/variant modifier appends a suffix to the base class rather than inventing an
  unrelated name — `win-lose-banner-win` / `-lose` / `-push`, `hand-active`.
- Every style class a Java `getStyleClass().add(...)` call applies should have a
  matching rule in `blackjack.css` (even an empty/minimal one) — `HandPane`'s
  `hand-wager-stack` class currently has no CSS rule at all, which isn't a naming
  violation (the name itself is fine) but is exactly the kind of orphaned cross-reference
  worth fixing next time that file's touched, rather than assuming a class with no rule
  was left there on purpose.

**Image/resource assets actually loaded by the app** (`src/main/resources/.../ui/`)
- Lowercase, `kebab-case`, named for what the asset visually *is*, not its source or
  where it came from — `app-icon.png`, `chips/white.png` (named for the chip's color,
  which is what `ChipView`/CSS key off, not its denomination or the file it was cropped
  from).
- Only group assets into a subfolder when there's a real family of them (`chips/`, five
  files); a one-off single asset sits directly under `ui/` next to `blackjack.css`
  (`app-icon.png`) — no folder-of-one.
- Every such asset is a *processed build artifact*: cropped, background-removed,
  resized as needed. It is never the raw file as originally sourced — that's what the
  `docs/` reference copy below is for.

**`docs/` reference assets** (provenance only, never loaded by the app)
- Pattern: `<subject>-reference.<ext>` — `chip-reference.png`, `icon-reference.png`.
- Always the raw, unprocessed source material, kept exactly as obtained (watermark and
  all, if it had one) so the provenance and the processing steps are both traceable —
  the steps themselves belong in this file's dated history (e.g. "Approved next batch"
  below), not in a checked-in script.
- Never referenced from Java code — if something under `docs/` is being loaded at
  runtime, it's in the wrong place.

**Ad-hoc files (screenshots, one-off debug scratch code, exploratory output)**
- Never committed, never left loose in the repo root. A screenshot requested mid-session
  for Claude to inspect belongs in the OS's own Pictures/Screenshots folder (or a scratch
  temp dir), not this repository — if one lands in the repo root anyway, delete it
  before committing rather than carving out a permanent home for it. `.gitignore` has a
  `Screenshot*.png` pattern as a safety net, but treat that as a backstop, not
  permission to leave them lying around.
- Same for any throwaway debug `main()` class written to isolate a bug (e.g. rendering
  something to a file to inspect it, or testing one JavaFX API in isolation) — delete it
  once the investigation is done, before committing. It never had a place in the
  project's actual structure to begin with.

**Git**
- Branches: `kebab-case`, short, describing the change itself, not the ticket/date
  (`settings-panel-in-theme-style`).
- Commit messages: imperative mood, capitalized, no trailing period, one line
  summarizing *what* changed ("Add chip-based betting, per-hand wager stacks, win/lose
  banner, window auto-fit"). Add a body only when the one-liner can't carry the *why*.

## Rules reference

Verified against [bicyclecards.com/how-to-play/blackjack](https://bicyclecards.com/how-to-play/blackjack/)
and standard casino rules:

- Goal: beat the dealer by getting closer to 21 without going over.
- Card values: 2–10 as printed, face cards = 10, Ace = 11 **or** 1 (whichever keeps the
  hand ≤ 21 — "soft" while an Ace still counts as 11).
- Deal: two cards to each player, two to the dealer — one up, one concealed ("hole
  card").
- Natural blackjack: Ace + ten-value card as the first two cards, pays 3:2. Only
  possible as the *original* two cards — a 21 built up over more cards, or after a
  split, is just a 21.
- Player options: hit, stand, double down (double the bet, take exactly one more card,
  then stand), split (on a pair, into two hands with equal bets played independently).
  Splitting Aces conventionally gives each new hand exactly one more card and no
  further action.
- Dealer: fixed strategy, no choices — hits until at least 17. Whether a *soft* 17 is
  hit (H17) or stood on (S17) is a house-rule variant.
- Bust (>21) loses immediately. Matching totals push (stake returned).

### Rule variants this implementation chose

Configurable via `GameRules` (see `GameRules.standard()`), all previously either
hardcoded or entirely absent in the original:

| Rule | Chosen default | Notes |
|---|---|---|
| Deck count | 6 | Original asked the player at runtime, hinting "2 is standard"; 6 is the more common real-shoe size. Not yet exposed in the UI — see roadmap. |
| Dealer soft 17 | Stands (S17) | The original never modeled soft hands at all, so this isn't a strict port — it's a new, explicit, testable default. H17 available via `dealerHitsSoftSeventeen()`. |
| Blackjack payout | 3:2 | Matches the original's `puntata * 5/2`. |
| Double after split | Allowed | Matches the original (no restriction there). |
| Split eligibility | Equal *blackjack value*, not exact rank (`Hand.isPair()`) | Matches the original — a King and a Jack can be split together. |
| Double down | Allowed on any first two cards, no total restriction | Matches the original. |
| Shoe penetration | 50% | Matches the original's `cards/2 < pointer` reshuffle check. |
| Insurance | Off | New side bet (not in the original at all) offered only when the dealer's up-card is an Ace and `insuranceAllowed()` is on — see "Approved next batch" item 8 below. |

## Correctness audit: what was wrong in the original, and how it's fixed here

This was the point of reviewing the old code before writing any new code — verifying
the implementation against the rules above, not just against itself.

1. **No soft-Ace handling — the critical bug.** `Carta.java` hardcoded every Ace to
   value 11 with no downgrade path; `Mano.java` just summed raw card values. Two Aces
   scored as 22 — an immediate, *incorrect* bust — instead of a soft 12. A hand like
   Ace+5 could never be played as the soft 16 it actually is.
   **Fixed by:** `Hand.total()` / `Hand.isSoft()` count every Ace as 11, then downgrade
   Aces to 1 one at a time (subtracting 10) for as long as the total exceeds 21.
   **Proven by:** `HandTest.twoAcesScoreAsTwelveNotABust`,
   `HandTest.aceDowngradesToAvoidBustWithMultipleCards`.

2. **A dealer blackjack was structurally undetectable.** `Partita.go()` dealt the
   dealer (`Banco`) only *one* card at the start of a round; a second card only ever
   arrived via the post-player draw-to-17 loop. There was no hole card and no peek —
   players could hit, double, or split into a hand that, under real rules, would
   already have lost to a dealer natural before they were ever offered a choice.
   **Fixed by:** `Dealer` is dealt two cards up front (`BlackjackTable.startRound`),
   and the hole card is peeked immediately whenever the up-card is an Ace or ten-value
   card — the only case where a two-card 21 is mathematically possible. A dealer
   natural ends the round immediately: a player natural pushes, anything else loses.
   **Proven by:** `BlackjackTableTest.dealerBlackjackEndsTheRoundBeforeThePlayerCanAct`,
   `BlackjackTableTest.dealerBlackjackAgainstPlayerBlackjackIsAPush`.

3. **Split Aces could be hit like any other split hand.** `Partita.play()` offered the
   same hit/double/stand menu regardless of whether the split pair was Aces.
   **Fixed by:** `BlackjackTable.split()` deals exactly one more card to each hand and
   forces an immediate stand when the split pair was Aces (`Hand.markSplitAces()`).
   **Proven by:** `BlackjackTableTest.splitAcesGetExactlyOneCardEachAndCannotBeHitAgain`
   — a hand at soft 20 (which alone wouldn't trigger the generic "21 locks the hand"
   rule) is still correctly forced to stand.

4. **No split-vs-natural distinction.** The original's blackjack check was just
   `mano.getValue() == 21`, with no guard against a 21 built after a split.
   **Fixed by:** `Hand.isNaturalBlackjack()` requires exactly two cards *and* that the
   hand didn't come from a split. **Proven by:**
   `HandTest.twentyOneAfterASplitIsNotANaturalBlackjack`.

### What was already correct, and kept as-is

- All **four card-counting systems** (Hi-Lo, Omega II, Red Seven, Zen Count) — the
  original's per-card tag values were checked against each system's published chart
  and are correct. Ported to `CountingSystem`, an enum instead of four parallel int
  fields on `Mazzo`. See `CountingSystemTest`.
- The **3:2 blackjack payout** arithmetic.
- **50% shoe penetration** before reshuffling.
- **Dealer's hard-17 stopping point** (draws below 17, stops at 17+).

## Known limitations right now

- **Single seat only.** The original supported multiple players at one terminal table;
  `BlackjackTable` currently assumes exactly one `Player`.
- **No surrender** side bet. (Insurance *is* now supported — see "Approved next batch"
  item 8 below — off by default via `GameRules.insuranceAllowed()`.)
- **`GameRules` isn't exposed in the UI yet** — the setup screen always uses
  `GameRules.standard()`, even though every rule is already a constructor parameter.
- **Windows-only packaging** — `pom.xml` hardcodes the `win` JavaFX classifier.

## Approved next batch (2026-09-14)

Investigated and approved in session with the project owner; supersedes the more
general bullets below where they overlap (noted inline). Items 1-3 are quick and
independent — **done and visually confirmed 2026-09-14**; **4 and 5 are done and
visually confirmed 2026-09-16**, both evolved past their original spec through several
rounds of feedback (see each item itself); 6 is the highest-risk item on this whole
document, still open; 7 is a grab-bag to pick from opportunistically (one item done
2026-09-16, see below). Items 8 and 9 weren't part of the original approved batch —
each was requested and done in a later session (2026-09-17/18), added here rather than
as their own section since this list is where the project's "what happened and why"
history actually lives.

1. ~~**Fix: the "Bet:" label is unreadable**~~ **Done.** Added a global
   `Label { -fx-text-fill: #f0f0f0; }` in `blackjack.css` rather than patching just that
   one label, so the bug class can't recur as more labels get added. Follow-up found
   during verification: `CheckBox` captions aren't `Label` nodes in JavaFX, so the new
   "Show card count" checkbox needed its own `.check-box` text-fill rule — worth
   remembering for any future control type (`RadioButton`, `ToggleButton`, etc.) added
   to the setup form.

2. ~~**Card-counting toggle.**~~ **Done.** "Show card count" checkbox on the setup
   screen, default off. When off, `shoeInfoLabel` shows only cards-remaining.

3. ~~**Move the count display to bottom-right.**~~ **Done.** It's now its own layer on
   `GameController.root` (a `StackPane`), pinned via `Pos.BOTTOM_RIGHT`, independent of
   the top bar and bottom controls layout.

4. ~~**Chip-based betting**~~ **Done, evolved past the original plan below through
   several rounds of hands-on feedback (2026-09-15/16):**
   - Chips are real photos (`ChipView`), cropped from `docs/chip-reference.png` (kept in
     the repo for provenance) via a one-off Pillow script that wasn't checked in — the
     cropped, transparent-background PNGs under `chips/` are the actual build artifact,
     not CSS-drawn circles as originally sketched.
   - Denominations map to **white=5 / red=10 / blue=25 / green=50 / black=100** —
     shifted one color down from the original red/blue/green/orange/black plan because
     the reference sheet has no orange chip.
   - Layout is a **horizontal row** above the bet controls, not the originally-planned
     vertical rail — changed on request so chips sit closer to the felt.
   - Every hand gets its **own chip stack rendered right under its cards**
     (`HandPane.setWager()`), not just one pile in the controls area. This falls out of
     the engine for free: `Hand.wager()` already doubles on `doubleDown()` and copies to
     both new hands on `split()`, so the per-hand stack updates correctly with no extra
     bookkeeping — no `Map<Hand, List<Long>>` tracking was needed.
   - Every stack (the pre-deal pile and every per-hand one) renders via
     `ChipView.stack(amount)`: a greedy breakdown into the **fewest chips**, **biggest
     denomination at the bottom** — it does not preserve the literal sequence of chips
     clicked (e.g. 5+10+5+25 renders as 25+10+10).
   - `betField`/`betErrorLabel`/the four numeric chip buttons/"All In" are gone, replaced
     by the chip row + `betStackPane`, matching the original plan. The pre-deal
     `betStackPane` hides itself (`setVisible`/`setManaged`) once a hand exists, so the
     bet total is never shown twice.

5. ~~**Game settings popup**~~ **Done (2026-09-16).** Exposes what `GameRules` already
   supported but the UI didn't. "Game Settings" button on the setup screen opens a modal
   `Dialog<GameRules>` (`GameController.openGameSettingsDialog()`), reachable before
   "Sit Down", with a live summary line (`rulesSummaryLabel`) on the setup card showing
   the currently-configured rules so a change doesn't disappear into an unopened dialog:
   - Deck count: 1 / 2 / 4 / 6 / 8 — `GameRules.deckCount()`
   - Dealer soft 17: stand (S17, friendlier) vs. hit (H17, standard casino) —
     `GameRules.dealerHitsSoftSeventeen()`
   - Double after split: on/off — `GameRules.doubleAfterSplitAllowed()`
   - Blackjack payout: 3:2 (standard) vs. 6:5 (worse for the player) —
     `GameRules.blackjackPayoutRatio()`
   - Shoe penetration: a 40-80% slider — `GameRules.penetrationPercent()`
   - Max split hands: 2-4 — `GameRules.maxSplitHands()`
   All of these were already constructor parameters on `GameRules`, so this was purely a
   UI task, no engine changes. The chosen rules are held in a new `pendingRules` field
   and only take effect on the next "Sit Down" (`onSitDown()` now uses `pendingRules`
   instead of `GameRules.standard()`).
   **Update (2026-09-16, same day): the `Dialog<GameRules>` described above is gone.**
   It took several rounds of contrast-fix back-and-forth (a light-gray background with
   dark text, then a text-only-brightness tweak against the default background — see git
   history on `settings-panel-in-theme-style` if the details ever matter) before landing
   on the real fix: replace the `Dialog` entirely with an in-theme `VBox` overlay, same
   as `buildConfirmNewGameOverlay()`. `openGameSettingsDialog()` no longer exists —
   `buildGameSettingsOverlay()` / `showGameSettingsOverlay()` / `applyGameSettings()` /
   `hideGameSettingsOverlay()` do the same job. See the design-standard note under
   "Notes for the next session working here".

6. **Visible deck + deal-from-deck animation.** The highest-risk item here —
   coordinate-math-heavy and genuinely hard to verify without watching it run:
   - Render a face-down stack (3-5 overlapping `CardView.faceDown()` nodes, small
     offsets) at a fixed table position representing the shoe.
   - When a card is dealt, animate a temporary card node via `TranslateTransition` from
     the deck's screen position to the destination hand slot's position (via
     `Node.localToScene()` on both ends), then swap in the real `CardView` and remove
     the temporary node.
   - Iterate on this one with the user watching the actual window, not from code
     review alone — animation timing and smoothness aren't verifiable from logs or
     tests.

7. **Other improvements, pick opportunistically:**
   - An actual flip animation for the dealer's hole card reveal (`RotateTransition` on
     the Y-axis, swapping face-down/face-up textures at 90°) instead of the current
     fade-in.
   - ~~A win/lose banner animation~~ **Done (2026-09-16), as a horizontal band, not a
     popup.** `GameController.showRoundOutcomeBanner()` pops a scale+fade "WIN +N" /
     "LOST -N" ribbon (`winLoseBanner`) spanning the table width, centered vertically,
     driven by the round's total profit summed across every hand's `Settlement`
     (handles split rounds correctly). **Gotcha hit while building this:** `Region`'s
     default max size is `Double.MAX_VALUE`, so an unconstrained `VBox` dropped into a
     `StackPane` stretches to fill it completely — the first version covered the whole
     window. Fixed by `winLoseBanner.setMaxHeight(Region.USE_PREF_SIZE)`; remember this
     for any future overlay added to `root`. *(Original behavior showed nothing on a
     push — changed in item 8 below to always show, including "PUSH +0".)*
   - Hover/press visual feedback on chips and buttons (scale-up on hover, press-down on
     click) for tactile feel.
   - A small recent-rounds history strip (colored dots: green win, red loss, grey
     push).
   - Keyboard shortcuts: H = hit, S = stand, D = double, Enter = deal.
   - Remember the last bet as the next round's default instead of resetting to
     `DEFAULT_BET`.
   - ~~A confirmation before "New Game"~~ **Done (2026-09-16), not in the roadmap
     originally — added on request to stop a misclick from silently wiping the
     bankroll.** First built with `javafx.scene.control.Alert`; **explicitly rejected**
     after visual review — default Alert theming produced near-invisible header text
     (same contrast trap as the settings dialog), a generic system-dialog look totally
     out of place on the felt table, and locale-dependent button captions (`ButtonType.YES`/
     `CANCEL` render in the OS locale — showed up as "Si"/"Annulla" on an Italian system,
     jarring next to the rest of the English UI). **Replaced with a custom overlay**
     (`GameController.buildConfirmNewGameOverlay()`) reusing the existing
     `setup-overlay`/`setup-card` styles instead — same dark card, gold primary button,
     explicit English text, fully in our own CSS. See the design-standard note this
     established, under "Notes for the next session working here".

8. ~~**Insurance side bet**~~ **Done (2026-09-18).** A side bet, up to half the
   original wager, offered only when the dealer's up-card is an Ace, paying 2:1 if the
   dealer has blackjack — gated behind a new `GameRules.insuranceAllowed()` toggle
   ("Offer insurance against a dealer Ace" checkbox in Game Settings), off by default.
   - **Engine:** `BlackjackTable.startRound()` now defers the dealer-blackjack peek
     specifically when the up-card is an Ace and the rule is on — new
     `isInsurancePending()` / `maxInsuranceBet()` / `takeInsurance()` /
     `declineInsurance()` API, resolved through a private `resolveInsuranceDecision()`
     that folds back into the same `resolveDealerPeek()` the non-Ace / rule-off path
     already used. A new `InsuranceSettlement` record (amount wagered, won, payout) is
     exposed via `lastInsuranceSettlement()`, separate from `Settlement` since it
     resolves independently of the main hand. Five new tests in `BlackjackTableTest`
     (insurance off by default even on an Ace, only offered on an Ace not a ten, a win
     pays 2:1, a decline costs nothing, a loss when the dealer has no blackjack) — engine
     test count 23 → 28.
   - **UI:** a new in-theme overlay (`buildInsuranceOverlay()`, same
     `setup-overlay`/`setup-card` pattern as every other popup) appears 500ms after the
     deal — long enough for the dealt-card fade-in to finish first — and shows a shrunk
     (`scaleX`/`scaleY` 0.7) preview of the player's current hand via a second
     `HandPane` instance, so the decision doesn't require looking behind the popup. The
     insurance amount itself is fixed at the standard casino max (half the bet); no
     adjustable-amount input was built, to avoid a bespoke widget for a rarely-varied
     value.
   - **Banner behavior, evolved through several rounds of feedback:** the round-outcome
     banner (`showRoundOutcomeBanner()`) now *always* shows — including a "PUSH +0" case
     that was previously silent (see the correction on item 7's banner bullet above). A
     **won** insurance bet against a dealer blackjack shows one *combined*
     "INSURANCE WIN +netProfit" banner instead of the usual WIN/LOST/PUSH one — main
     hand profit and insurance profit summed together, e.g. bet 10 + insurance 5,
     dealer blackjack, otherwise-losing hand: -10 main hand + 10 insurance profit =
     "INSURANCE WIN +0". A **lost or declined** insurance bet gets no banner of its own
     — just the existing round message text, which already mentions the result (e.g.
     "Dealer wins — Insurance lost -5").

9. ~~**Window/taskbar icon**~~ **Done (2026-09-18).** Two overlapping spade cards. Went
   through three different implementations before landing on the current one — each
   failure looked like success until actually checked pixel-by-pixel in a live
   screenshot, which is why this entry is long:
   - **v1 — rendered from `CardView` via `Node.snapshot()`.** Looked fine exported to a
     PNG. As a real title bar icon: silent failure, OS default icon shown instead, no
     exception anywhere. Root-caused via a from-scratch minimal repro (a bare
     `Application` setting a manually-built `WritableImage` as `Stage.getIcons()`,
     nothing else) that a plain `WritableImage` — even with soft/partial alpha edges —
     works fine, but the exact same pixel values returned by `Node.snapshot()` don't.
     **The fix, if this pattern is ever needed again:** copy the snapshot's pixels into
     a fresh `WritableImage` via `PixelReader`/`PixelWriter` before handing it to
     `Stage.getIcons()` — never return a `Node.snapshot()` result as a Stage icon
     directly. Two more real gotchas surfaced along the way, worth remembering for any
     future in-app-rendered icon: (1) Windows' native `.ico` format hard-caps icon
     images at 256×256px — a bigger source silently fails the same way, no exception;
     (2) JavaFX auto-applies the `.root` style class to whatever node is the actual
     `Scene` root, so snapshotting a node that's also a scene root picks up
     `blackjack.css`'s `.root` felt-green background unexpectedly — wrap the real
     content in a bare `Group` as the Scene root instead.
   - **v2 — the same rendered cards, size- and background-fixed.** Superseded before
     shipping (see v3) — kept only as the "what actually root-caused the bug" story
     above.
   - **v3 — a real image, per explicit request.** The project owner uploaded a stock
     photo of an Ace and Jack of spades. It had a `shutterstock.com · <id>` watermark
     baked directly into the pixels near the bottom edge — confirmed, not inferred from
     the filename — meaning it was an unlicensed preview download. Flagged this
     explicitly and got confirmation of usage rights before proceeding (see "Naming &
     file conventions" for where the two resulting files live: `docs/icon-reference.png`
     is the untouched original, watermark included, for provenance;
     `src/main/resources/.../ui/app-icon.png` is the processed, transparent-background,
     watermark-cropped, 240×240 build artifact actually loaded). Processing was a
     one-off Pillow script (not checked in, per convention): strip the watermark band,
     chroma-key the flat gray background to transparent, crop to content, pad, resize.
     `AppIcon` no longer renders anything itself — `AppIcon.load()` just decodes the PNG
     resource, which sidesteps the whole `Node.snapshot()` problem above entirely (a
     directly-decoded image is not a snapshot result).

## Roadmap: possible upgrades & features

### Near-term / low effort
- [ ] Add the Maven Wrapper (`mvn -N wrapper:wrapper`, once Maven is available) so
      contributors don't need Maven pre-installed — skipped in this pass because a
      hand-authored wrapper script couldn't be verified without running it.
- [ ] Let the player leave the table voluntarily after any settled round, not only when
      bankrupt (the original asked "vuoi giocare ancora?" — do you want to keep
      playing? — after every hand).

### Rules & gameplay depth
- [ ] **Late surrender** — forfeit half the bet before acting. Deliberately left out of
      this pass to keep the initial scope to "fix the original's bugs + add a GUI"
      rather than inventing rule variants the original never had; `Hand` would need a
      `SURRENDERED` status and `BlackjackTable` a `surrender()` action gated by a new
      `GameRules.surrenderAllowed` flag.
- [x] ~~**Insurance** side-bet when the dealer shows an Ace.~~ **Done (2026-09-18)** —
      see "Approved next batch" item 8 above for the full story.
- [ ] Rule-configurable re-split limits (e.g. "no re-splitting Aces") — currently any
      pair, Aces included, can be re-split up to `GameRules.maxSplitHands()`.
- [ ] **Multiplayer** — multiple `Player` seats sharing one `Shoe`/`Dealer`. The
      shoe/dealer/settlement logic already generalizes; `BlackjackTable`'s round
      orchestration and turn order would need to loop over players instead of assuming
      one.
- [ ] Basic-strategy hint overlay (the mathematically optimal action for the current
      hand vs. the dealer's up-card) — pairs naturally with the existing running-count
      display.

### Visual / UX polish
- [ ] Animate the dealer's draw sequence with a visible pause between cards — currently
      `BlackjackTable.playDealerTurn()` resolves the whole draw sequence instantly and
      only the *reveal* of already-known cards is staggered in the UI.
- [ ] Sound effects (deal, flip, chip, win/lose).
- [ ] Move the UI to FXML + CSS if it grows much further — deliberately skipped for
      this pass since a hand-authored FXML file's `fx:id` wiring couldn't be verified
      without a compiler.
- [ ] Table felt / card-back theme picker.
- [ ] Cross-platform packaging (drop the hardcoded `win` JavaFX classifier in favor of
      the `os-maven-plugin` extension or per-OS Maven profiles).

### Meta / persistence
- [ ] Session statistics (hands played, win rate, biggest win) persisted locally.
- [ ] Save/resume a bankroll across app restarts.
- [ ] `jpackage` a native Windows installer so the app doesn't require Maven to launch.

## Notes for the next session working here

- The engine package has **zero JavaFX dependency** by design — keep it that way; it's
  what makes `BlackjackTableTest` runnable headless.
- `BlackjackTable.startRound()` always draws in the order **player, dealer, player,
  dealer**. Every fixed-order test `Shoe` in `BlackjackTableTest` depends on that exact
  order — if it ever changes, every test's card list needs re-deriving, not just
  patching.
- `Shoe`'s package-private `Shoe(List<Card>)` constructor exists purely as a
  deterministic-testing seam. Don't make it public; production code should only ever
  use `new Shoe(deckCount, penetrationPercent)`.
- The player name/bankroll status line moved from a top bar to a `.status-bar` inside
  `buildControlsArea()` (bottom of the window) on request (2026-09-16) — there is no
  more `layout.setTop(...)` in `buildTableLayout()`. Don't reintroduce a top bar without
  checking that's actually wanted; it was a deliberate move, not an oversight.
- **The window has no fixed size** — `BlackjackApp` creates `new Scene(controller.getRoot())`
  with no width/height, and `GameController.growToFitContent()` (called at the end of
  every `refresh()`) grows the stage — *never shrinks it* — to fit whatever is on screen
  right now: dealt cards, wager stacks, extra hands from a split. This replaced a fixed
  1040x720 `Scene` that started cropping content once the chip row, status bar, and
  per-hand wager stacks were added — a hardcoded size will go stale again the next time
  the table UI grows, so don't reintroduce one. Call `attachStage()` before relying on
  this if `GameController` is ever constructed somewhere other than `BlackjackApp`.
  **Gotcha hit while building this:** don't call `Stage.sizeToScene()` *before*
  `Stage.show()` when `minWidth`/`minHeight` are also set — it computes layout at the
  pre-clamp size, then the min-size constraint silently enlarges the window without
  re-laying-out root, leaving content pinned top-left in a mostly-blank window. Call
  `show()` first (its own auto-sizing already accounts for the min constraints), then
  `centerOnScreen()` if needed.
- **Design standard set 2026-09-16: prefer a custom in-theme overlay over
  `javafx.scene.control.Dialog`/`Alert` for anything user-facing.** Both popups this
  project has needed so far were first built with stock JavaFX dialogs and both had
  default-theming problems — the Game Settings dialog needed a RadioButton/CheckBox
  text-color override to be readable, and the "New Game" confirmation (built with
  `Alert`) was rejected outright for near-invisible header text, a generic system-dialog
  look that broke the felt-table visual identity, and locale-dependent button captions
  (`ButtonType.YES`/`CANCEL` render in the OS locale, not English). **Both are now plain
  `VBox`es reusing `setup-overlay`/`setup-card`** — `buildConfirmNewGameOverlay()` and
  `buildGameSettingsOverlay()` — giving full control over text, color, and language, and
  they actually look like part of this app. Follow this same pattern for any future
  modal: a `VBox` styled with `setup-overlay`/`setup-card`, added to `root`'s children,
  toggled via `setVisible`/`setManaged`, not a `Dialog`/`Alert`. One gotcha this
  surfaced: RadioButton captions, like CheckBox, aren't `Label` nodes and need their own
  `.setup-card .radio-button` rule — the same class of gap CLAUDE.md already flagged for
  CheckBox, now fixed for both together.
- **`javafx.scene.Node.snapshot()`'s returned `Image`, passed directly to
  `Stage.getIcons()`, silently fails to become the Windows title bar/taskbar icon** — no
  exception, the pixel data is provably correct, the OS just keeps showing its own
  default icon instead. If a `Node.snapshot()` result ever needs to become a Stage icon
  again, copy its pixels into a fresh `WritableImage` via `PixelReader`/`PixelWriter`
  first — see "Approved next batch" item 9 for the full investigation and two more
  related gotchas (the 256×256 `.ico` size cap, and `Scene` auto-applying `.root` CSS
  to whatever node is the actual scene root). `AppIcon` itself no longer uses
  `snapshot()` at all — it just loads a real PNG — so this is dormant unless something
  in this codebase renders a JavaFX node to an image again.
