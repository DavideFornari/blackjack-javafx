# CLAUDE.md

Guidance for Claude Code sessions working in this repository.

Read **Invariants & gotchas** before changing anything — it encodes decisions that cost
real debugging time to learn. **Known issues** is the current defect list; **Improvement
backlog** is prioritised work. Both are graded *Impact* × *Effort (S/M/L)*.

## What this is

A graphical, single-player Blackjack table in JavaFX — a modernization of
[DavideFornari/BlackJack](https://github.com/DavideFornari/BlackJack), a terminal Java
university project from 2019 (*Programmazione con Laboratorio*, University of Verona).
The original was a console app with no build file, Italian class names (`Carta`, `Mazzo`,
`Mano`, `Giocatore`, `Banco`, `Partita`) and a real scoring bug. This rewrite keeps the
feature set (multi-deck shoe, betting, hit/stand/double/split, four card-counting systems),
fixes the bugs documented below, adds a Maven build, and replaces `Scanner` I/O with a GUI.

**Stack:** JDK 21 + JavaFX 21.0.7 (`org.openjfx`, `win` classifier — Windows-only for now),
Maven, JUnit 5. No FXML (the scene graph is wired in `GameController`, so every binding is
grep-able in one place). Table cards are styled text/shapes, not image assets — only chips
and the window icon are real images.

## Build & run

```bash
mvn test           # engine unit tests — no JavaFX runtime needed
mvn javafx:run     # launches the app
```

**Verification standard:** a `BUILD SUCCESS` from `javafx:run` is *not* proof the app works
— the plugin also exits cleanly when the toolkit can't attach to a display. Neither is "no
exception thrown" proof that a visual change landed: the window icon work below passed both
checks for two rounds while still being visibly broken. UI work is done when the project
owner has *looked at the running window*, or when a screenshot has been checked pixel-by-pixel.

**Last verified 2026-09-18:** `mvn test` 28/28 green; window confirmed visually by the owner
across the insurance flow, the outcome banners and the new title-bar icon.

## Architecture

```
src/main/java/io/github/davidefornari/blackjack/
  engine/   pure Java, zero JavaFX dependency, unit-tested headless
  ui/       JavaFX, depends on engine
src/test/java/.../engine/
```

`engine/`: `Card`/`Rank`/`Suit` (model) → `Hand` (cards + soft-Ace scoring) → `Shoe`
(multi-deck, shuffle, penetration reshuffle, running counts for all four systems at once) →
`Dealer`/`Player` → `GameRules` (house rules, a record) → `BlackjackTable` (one round: deal →
optional insurance decision → player turns → dealer turn → settle) → `RoundOutcome`/
`Settlement`/`InsuranceSettlement`.

`ui/`: `BlackjackApp` (entry point, loads the window icon via `AppIcon`) → `GameController`
(owns the `BlackjackTable`, builds and refreshes the whole scene graph, all button wiring) →
`CardView`/`HandPane`/`ChipView` (reusable view components).

## Naming & file conventions

**Java** — packages `io.github.davidefornari.blackjack.{engine,ui}` only, nothing deeper.
`PascalCase` classes, one public class per file, filename matches. A reusable scene-graph
component takes a `View`/`Pane` suffix naming what it *is* (`CardView`, `HandPane`,
`ChipView`); a non-Node helper or data holder doesn't (`AppIcon`, `GameRules`). Immutable
data carriers are `record`s named for the concept — no `Impl`/`Data`/`DTO` suffixes.
Methods: `camelCase` verbs for actions (`startRound`, `takeInsurance`), `isX`/`hasX`/`canX`
for booleans.

**Tests** — `<ClassUnderTest>Test`, same package. Method names are one descriptive
`camelCase` sentence covering scenario *and* expected outcome, no `test` prefix
(`takingInsuranceAgainstADealerBlackjackPaysTwoToOne`). Needing "and" to describe it usually
means it's two tests.

**CSS** (`blackjack.css`) — `kebab-case`. A class scoped to one component carries that
component's prefix: `setup-*` (setup screen and every in-theme overlay), `hand-*`, `card-*`,
`chip-*`, `win-lose-banner*`. Genuinely global classes stay unprefixed (`primary-button`,
`error-label`, `message-label`, `status-bar`, `controls-area`, `table-layout`, `count-badge`,
`bet-total-badge`) — don't force a component prefix onto something that isn't scoped to one.
Variants suffix the base class (`win-lose-banner-win`/`-lose`/`-push`, `hand-active`). Every
class a `getStyleClass().add(...)` applies should have a matching rule.

**Runtime image assets** (`src/main/resources/.../ui/`) — lowercase `kebab-case`, named for
what the asset *is*, not where it came from (`app-icon.png`, `chips/white.png`). Subfolder
only for a real family (`chips/`, five files); a one-off sits directly under `ui/`. Every one
is a *processed* artifact (cropped, background removed, resized) — never the raw source.

**`docs/` reference assets** — provenance only, never loaded at runtime:
`<subject>-reference.<ext>` (`chip-reference.png`, `icon-reference.png`), kept exactly as
obtained, watermark and all. Processing is a one-off script that is *not* checked in; the
steps belong in **Project history** below.

**Ad-hoc files** — screenshots and throwaway debug `main()` classes are never committed and
never left in the repo root. Delete them before committing; `.gitignore`'s `Screenshot*.png`
is a backstop, not permission.

**Git** — branches `kebab-case`, describing the change (`settings-panel-in-theme-style`).
Commits: imperative, capitalized, no trailing period, one line for *what*; body only when the
one-liner can't carry the *why*.

## Rules reference

Verified against [bicyclecards.com](https://bicyclecards.com/how-to-play/blackjack/). Only
the points that actually constrain this code are listed; general play is assumed.

- A **natural** is Ace + ten-value as the *original two cards*. A 21 assembled over more
  cards, or after a split, is just 21 (`Hand.isNaturalBlackjack()` enforces both).
- **Ace = 11 or 1**, whichever keeps the hand ≤ 21; "soft" while an Ace still counts 11.
- The dealer has **no choices**: draw below 17, stand at 17+. Whether a *soft* 17 is hit is
  the one house variant (H17/S17).
- **Split Aces** conventionally get exactly one card each and no further action.
- **Insurance** is offered *only* against an Ace up-card (never a ten), costs up to half the
  original bet and pays 2:1.

### Rule variants chosen (all configurable via `GameRules`; see `GameRules.standard()`)

| Rule | Default | Notes |
|---|---|---|
| Deck count | 6 | Original asked at runtime hinting "2 is standard"; 6 is the common real-shoe size. |
| Dealer soft 17 | Stands (S17) | The original never modeled soft hands, so this is a new explicit default, not a port. |
| Blackjack payout | 3:2 | Matches the original's `puntata * 5/2`. 6:5 selectable. |
| Double after split | Allowed | Matches the original (no restriction there). |
| Split eligibility | Equal *blackjack value*, not rank | Matches the original — K+J is splittable. |
| Double down | Any first two cards | Matches the original. |
| Shoe penetration | 50% | Matches the original's `cards/2 < pointer` check. |
| Insurance | Off | New; absent from the original entirely. Ace up-card only. |
| Insurance amount | Fixed at half the bet | The casino maximum, not adjustable — avoids a bespoke bet-amount widget. |

## What was wrong in the original, and how it's fixed

Verifying the old code against the rules above — not just against itself — was the point of
reviewing it before writing anything.

1. **No soft-Ace handling (the critical bug).** `Carta` hardcoded every Ace to 11 with no
   downgrade and `Mano` summed raw values, so two Aces scored 22 — an immediate *incorrect*
   bust — and A+5 could never be played as soft 16. **Fixed:** `Hand.total()`/`isSoft()`
   count every Ace as 11, then downgrade Aces one at a time while the total exceeds 21.
   **Proven by:** `HandTest.twoAcesScoreAsTwelveNotABust`,
   `HandTest.aceDowngradesToAvoidBustWithMultipleCards`.
2. **A dealer blackjack was structurally undetectable.** `Partita.go()` dealt the dealer one
   card; the second only arrived in the draw-to-17 loop, so there was no hole card and no
   peek — players could hit, double or split into a hand already lost to a natural.
   **Fixed:** the dealer gets two cards up front and the hole card is peeked whenever the
   up-card is an Ace or ten (the only two-card-21 cases); a dealer natural ends the round at
   once — player natural pushes, anything else loses. **Proven by:**
   `BlackjackTableTest.dealerBlackjackEndsTheRoundBeforeThePlayerCanAct`,
   `...dealerBlackjackAgainstPlayerBlackjackIsAPush`.
3. **Split Aces could be hit.** `Partita.play()` offered the same menu regardless of the
   split pair. **Fixed:** `BlackjackTable.split()` deals exactly one card to each and forces
   a stand for Aces (`Hand.markSplitAces()`). **Proven by:**
   `...splitAcesGetExactlyOneCardEachAndCannotBeHitAgain` — which deliberately uses a soft 20,
   a hand the generic "21 locks" rule wouldn't catch.
4. **No split-vs-natural distinction.** The check was `mano.getValue() == 21` with no split
   guard. **Fixed:** `Hand.isNaturalBlackjack()` requires exactly two cards *and* no split
   ancestry. **Proven by:** `HandTest.twentyOneAfterASplitIsNotANaturalBlackjack`.

**Already correct, kept as-is:** all four counting systems' per-card tags (ported to the
`CountingSystem` enum instead of four parallel int fields on `Mazzo`); the 3:2 payout
arithmetic; 50% penetration; the dealer's hard-17 stopping point.

## Invariants & gotchas

Break these and something subtle fails, usually silently.

**Engine**
- The engine package has **zero JavaFX and zero console I/O dependency**. That's what makes
  `BlackjackTableTest` runnable headless and would let a second UI reuse it unchanged.
- `BlackjackTable.startRound()` always draws **player, dealer, player, dealer**. Every
  fixed-order test `Shoe` depends on that exact order — change it and every test's card list
  needs re-deriving, not patching.
- `Shoe`'s package-private `Shoe(List<Card>)` is a deterministic-testing seam only. Don't
  make it public; production code uses `new Shoe(deckCount, penetrationPercent)`.
- Insurance defers the dealer peek: on an Ace up-card with the rule on, `startRound()` stops
  before peeking and the round only resolves once `takeInsurance()`/`declineInsurance()` is
  called. Any caller must check `isInsurancePending()` *before* `isPlayerTurnComplete()` —
  during the pending window the latter reports `true` while nothing is actually settled.

**JavaFX**
- **Prefer a custom in-theme overlay to `Dialog`/`Alert`.** Both stock dialogs this project
  tried were rejected: near-invisible default header text, a system look that broke the felt
  table, and OS-locale button captions (`ButtonType.YES`/`CANCEL` rendered "Sì"/"Annulla" on
  an Italian machine). All four popups are now plain `VBox`es reusing `setup-overlay`/
  `setup-card`, toggled with `setVisible`/`setManaged` on `root`'s children.
- `CheckBox` and `RadioButton` captions are **not** `Label` nodes — a global `Label` text-fill
  rule misses them. They need their own `.setup-card .check-box` / `.radio-button` rules.
- A `Region` dropped into a `StackPane` stretches to fill it (`maxSize` defaults to
  `Double.MAX_VALUE`). The outcome banner needed `setMaxHeight(Region.USE_PREF_SIZE)` to read
  as a band instead of covering the window. Applies to any future overlay on `root`.
- **The window has no fixed size.** `BlackjackApp` builds `new Scene(controller.getRoot())`
  with no dimensions and `growToFitContent()` (end of every `refresh()`) grows the stage,
  *never shrinks* it, to fit dealt cards, wager stacks and split hands. This replaced a fixed
  1040x720 scene that started cropping content; a hardcoded size will go stale again. Call
  `attachStage()` if `GameController` is ever built outside `BlackjackApp`.
- Don't call `Stage.sizeToScene()` *before* `show()` when `minWidth`/`minHeight` are set — it
  lays out at the pre-clamp size, then the min constraint enlarges the window without a
  relayout, pinning content top-left in a mostly blank window. `show()` first, then
  `centerOnScreen()`.
- **`Node.snapshot()`'s `Image` silently fails as a `Stage` icon on Windows** — no exception,
  correct pixels, OS default icon shown anyway. A manually built `WritableImage` with
  identical pixels works. If a snapshot ever needs to be an icon again, copy its pixels into a
  fresh `WritableImage` via `PixelReader`/`PixelWriter` first. Two neighbours of that bug:
  Windows `.ico` hard-caps at **256×256** (bigger fails the same silent way), and JavaFX
  auto-applies the `.root` style class to whatever node is a `Scene`'s root — so snapshotting
  a scene root picks up `blackjack.css`'s felt-green background. Wrap content in a bare
  `Group` as the scene root instead. Dormant while `AppIcon` just decodes a PNG.
- The status line moved from a top bar to `.status-bar` inside `buildControlsArea()` on
  request (2026-09-16); there is no `layout.setTop(...)`. Deliberate, not an oversight.

**Assets**
- Chip denominations map **white=5, red=10, blue=25, green=50, black=100** (the reference
  sheet has no orange chip, which shifted the original plan one colour down).
- Every chip stack renders via `ChipView.stack(amount)`: a greedy breakdown into the fewest
  chips, biggest at the bottom. It represents an *amount*, not the click history — 5+10+5+25
  renders as 25+10+10.
- Per-hand wager stacks fall out of the engine for free: `Hand.wager()` already doubles on
  `doubleDown()` and is copied to both hands on `split()`, so no `Map<Hand, List<Long>>`
  bookkeeping is needed.

## Known issues

Nothing here is a hypothetical — each was confirmed by reading the code on 2026-09-18.

### High impact, small effort — fix first

1. **No outcome banner when the round ends at the deal.** `GameController.onDeal()` calls
   `settleRound()` and discards its return value, so `showRoundOutcomeBanner(...)` never
   fires. Every round decided before the player acts settles silently: a dealt natural (~4.8%
   of hands) and a dealer natural peeked on a ten up-card. Regression from the 2026-09-18
   banner refactor, which moved banner duties from `settleRound()` to its callers and updated
   `afterPlayerAction()` and `afterInsuranceDecision()` but missed this third call site.
   *Fix:* `showRoundOutcomeBanner(settleRound());` — one line.
2. **"Take Insurance" throws when the bet consumed the whole bankroll.** `onTakeInsurance()`
   passes `maxInsuranceBet()` straight to `BlackjackTable.takeInsurance()`, which throws
   `IllegalArgumentException` when the amount exceeds the bankroll. Bet everything, get an Ace
   up-card with insurance enabled, and the button throws inside the FX handler: stack trace to
   stderr, overlay stays open, the click appears to do nothing. *Fix:* disable the take button
   (or skip the offer) when `maxInsuranceBet() > player.bankroll()`.
3. **Soft-lock when the bankroll falls to 1-4.** `Player.isBankrupt()` is `bankroll <= 0`, so
   1-4 chips counts as a live game — but every chip button disables (would exceed bankroll),
   Deal stays disabled, and "New Game" is only visible in ROUND_OVER/GAME_OVER. BETTING then
   has no exit at all short of killing the window. Reachable via issue 4. *Fix:* end the
   session when the bankroll can't cover the smallest chip, or always show New Game.

### Medium impact, small-to-medium effort

4. **3:2 rounds in the player's favour and knocks the bankroll off the chip lattice.**
   `Math.round(wager * (1 + ratio))` on an odd multiple of 5 overpays half a chip (wager 15 →
   37.5 → 38) and leaves a bankroll that can never be fully bet in 5-chips — the root cause of
   issue 3. *Fix:* express the payout as a rational (3/2, 6/5) and floor, or keep money off
   `double` entirely.
5. **`RoundOutcome.WIN` is asserted nowhere.** The ordinary "player beats the dealer, paid
   2×" path — and the dealer-bust route that most often produces it — has zero test coverage,
   despite being the single most common winning outcome.
6. **User-selectable rules are untested.** 6:5 payout, `doubleAfterSplitAllowed = false` and
   `maxSplitHands` (re-splitting up to the cap) are all exposed in Game Settings and none is
   covered by a test.
7. **No `ShoeTest`.** Penetration maths, reshuffle resetting the counts, running-count
   accumulation and exhaustion behaviour are all untested, and `Shoe` is where the subtlest
   engine arithmetic lives.
8. **`refresh()` re-animates every card on every call.** `renderDealer()`/`renderPlayerHands()`
   rebuild their panes and call `animateIn()` unconditionally, so hitting re-fades the whole
   hand and even a chip click re-runs the table's fade-ins. Needs the render pass to animate
   only genuinely new cards.

### Low impact

9. **Red Seven's running count starts at 0, not its conventional IRC.** Unbalanced systems
   normally start at −2 per deck so the published thresholds line up; starting at 0 leaves the
   displayed count offset by +2 × decks. The per-card *tags* are correct (as claimed above) —
   it's the initial count convention that isn't. The enum's doc also says a full *shoe*
   finishes at +2; it's +2 *per deck*.
10. **A round can outrun a nearly-spent shoe.** `needsShuffle()` is only consulted between
    rounds, so 1 deck at 80% penetration (~10 cards left) plus a max-split round with several
    hits can exhaust it mid-hand, hitting `draw()`'s defensive reshuffle — running counts reset
    and cards already face-up on the table become dealable again.
11. **The fixed test shoe explodes instead of failing clearly.** `Shoe(List<Card>)` sets
    `deckCount = 0`, so `draw()`'s defensive `shuffle()` rebuilds an *empty* shoe and throws
    `IndexOutOfBoundsException`. An under-provisioned test gets an opaque IOOBE instead of
    "test shoe exhausted".
12. **`Hand.cardsToString()` is dead code** — zero callers, a leftover from the console
    original. Delete it.
13. **`ChipView.breakdown()` silently drops what it can't represent** (7 → a single 5-chip).
    Safe today because every wager is a sum of chips, but it will under-render the first time
    an arbitrary amount reaches it. It's also `public` though only `stack()` uses it.
14. **Missing-resource failures are opaque.** `ChipView.load()` and `AppIcon.load()` hand a
    possibly-null stream to `new Image(...)`; a renamed asset surfaces as an NPE — an
    `ExceptionInInitializerError` for `ChipView`'s static map — rather than naming the file.
15. **`hand-wager-stack` has no CSS rule** despite being applied in `HandPane`.
16. **No `.gitattributes`** — every commit warns `LF will be replaced by CRLF`, and line
    endings depend on who checked out. `* text=auto` fixes it.
17. **`GameController` is 986 lines — 46% of the 2,161-line main source tree.** Every overlay
    builder, render pass, animation and phase transition lives in one class. Splitting it is
    Large effort, hence its placement in the backlog rather than here.

## Improvement backlog

Ordered by value per unit of effort. Items marked ⟵ are pulled from the old roadmap.

### High impact, small effort
- Fix known issues 1-3 above (banner regression, insurance affordability, soft-lock).
- **Keyboard shortcuts** — H/S/D for hit, stand, double; Enter to deal. ⟵
- **Remember the last bet** as the next round's default instead of clearing the chip pile. ⟵
- **Let the player leave the table voluntarily** after any settled round, not only when
  bankrupt — the original asked "vuoi giocare ancora?" after every hand. Also resolves the
  escape half of issue 3. ⟵
- **Show the true count**, not just the running count — a running count alone isn't
  actionable on a 6-deck shoe. Pairs with fixing issue 9.

### High impact, medium effort
- **Close the test gaps** from issues 5-7: a plain `WIN`/dealer-bust test, the three
  configurable rules, and a `ShoeTest`.
- **Animate only new cards** (issue 8) — the single most visible piece of UI polish available,
  since today every action re-fades the whole table.
- **Visible deck + deal-from-deck animation.** ⟵ The highest-risk item on this list:
  coordinate-heavy and unverifiable from tests. Render a face-down stack (3-5 overlapping
  `CardView.faceDown()` nodes, small offsets) at a fixed table position; on deal, animate a
  temporary node via `TranslateTransition` between `Node.localToScene()` positions, then swap
  in the real `CardView`. Iterate with the owner watching the window, not from code review.
- **Insurance visibility during play** — once taken, nothing on the table shows it until the
  round-over message. A small chip stack or badge would close the loop.

### Medium impact, medium effort
- **Hole-card flip animation** (`RotateTransition` on the Y-axis, swapping textures at 90°)
  instead of the current fade-in. ⟵
- **Stagger the dealer's draw** — `playDealerTurn()` resolves the whole sequence instantly and
  only the *reveal* is staggered in the UI. ⟵
- **Hover/press feedback** on chips and buttons. ⟵
- **Recent-rounds history strip** (green/red/grey dots). ⟵
- **Sound effects** (deal, flip, chip, win/lose). ⟵
- **Table felt / card-back theme picker.** ⟵
- **Session statistics** — hands played, win rate, biggest win. ⟵
- **Cross-platform packaging** — drop the hardcoded `win` classifier for `os-maven-plugin` or
  per-OS profiles. ⟵
- **Maven Wrapper** (`mvn -N wrapper:wrapper`) so contributors don't need Maven installed. ⟵

### Rules & gameplay depth (medium-to-large effort)
- **Late surrender** ⟵ — deliberately out of the original scope ("fix the bugs + add a GUI",
  not invent variants). Needs a `Hand` `SURRENDERED` status, a `BlackjackTable.surrender()`
  gated by a new `GameRules` flag, and settlement handling.
- **Rule-configurable re-split limits** (e.g. no re-splitting Aces) — currently any pair,
  Aces included, re-splits up to `maxSplitHands()`. ⟵
- **Basic-strategy hint overlay** — optimal action for the current hand vs. the up-card; pairs
  naturally with the count display. ⟵
- **Multiplayer** ⟵ — several `Player` seats sharing one `Shoe`/`Dealer`. Shoe, dealer and
  settlement already generalize; `BlackjackTable`'s round orchestration and turn order would
  need to loop over players instead of assuming one.

### Large effort / structural
- **Split `GameController`** (issue 17) — extract the overlay builders and the render pass at
  minimum. Do this before the class grows again, not as a standalone refactor sprint.
- **Move to FXML + CSS** if the UI grows much further ⟵ — skipped so far because hand-authored
  `fx:id` wiring couldn't be verified without running it, and the single-file scene graph is
  still grep-able.
- **`jpackage` a native installer** so the app doesn't need Maven to launch. ⟵
- **Persist bankroll/stats across restarts.** ⟵
- **Externalize UI strings** — everything is hardcoded English today.
- **Headless UI tests** (TestFX) — the insurance flow, banners and overlays are verified only
  by eye, which is why the verification standard above exists.

## Project history

Compressed changelog. Decisions that still constrain future work live in **Invariants &
gotchas**; this is the "what happened when" record.

- **2026-09-14** — Initial port: engine + JavaFX UI + 23 tests, build verified end to end.
  Then: global `Label` text-fill fix for the unreadable "Bet:" label (fixing the class of bug,
  not the one label), card-counting toggle (default off), count display moved to its own
  bottom-right layer on `root`.
- **2026-09-15/16** — Chip-based betting: real chip photos cropped from
  `docs/chip-reference.png`, a horizontal rail (not the originally planned vertical one), and
  a per-hand wager stack under every hand. Game Settings exposing all of `GameRules`, held in
  `pendingRules` and applied on the next "Sit Down". Win/lose banner. Confirmation before "New
  Game". Status line moved to the bottom. Window auto-fit replacing the fixed 1040x720 scene.
  Both the settings panel and the confirmation were **first built with `Dialog`/`Alert` and
  rejected on visual review** — the origin of the overlay standard above.
- **2026-09-17/18** — Insurance side bet: `GameRules.insuranceAllowed` (off by default), a
  deferred dealer peek on Ace up-cards, `InsuranceSettlement`, and an overlay that pops 500ms
  after the deal (so the deal animation finishes) showing a 0.7-scaled preview of the hand.
  Banner behaviour changed with it: the round banner now *always* shows, including "PUSH +0",
  and a won insurance bet replaces it with a single combined "INSURANCE WIN +netProfit" that
  folds in the main hand's result (bet 10 + insurance 5 against a dealer natural = "+0"). Tests
  23 → 28.
- **2026-09-18** — Window/taskbar icon, after three attempts: rendering it from `CardView` via
  `Node.snapshot()` produced a correct image that Windows silently refused (see the gotcha
  above), and an over-256px render failed the same way. It now loads `app-icon.png`, processed
  from an owner-supplied image whose original sits in `docs/icon-reference.png`. That image
  arrived with a `shutterstock.com · <id>` watermark baked into the pixels; the owner confirmed
  usage rights before it was cropped out and committed. Same day: naming conventions written
  down, and this full audit.
