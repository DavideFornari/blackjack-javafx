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
player turns → dealer turn → settle) → `RoundOutcome` / `Settlement`.

`ui/`: `BlackjackApp` (entry point) → `GameController` (owns the `BlackjackTable`,
builds and refreshes the whole scene graph, all button wiring) → `CardView` / `HandPane`
(reusable view components).

The engine has no knowledge of JavaFX and no knowledge of Scanner/console I/O either —
keep it that way. It's what makes `BlackjackTableTest` possible without a display, and
would let a second UI (CLI, web, whatever) reuse it unchanged.

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
- **No insurance or surrender** side bets.
- **`GameRules` isn't exposed in the UI yet** — the setup screen always uses
  `GameRules.standard()`, even though every rule is already a constructor parameter.
- **Windows-only packaging** — `pom.xml` hardcodes the `win` JavaFX classifier.

## Roadmap: possible upgrades & features

### Near-term / low effort
- [ ] Add the Maven Wrapper (`mvn -N wrapper:wrapper`, once Maven is available) so
      contributors don't need Maven pre-installed — skipped in this pass because a
      hand-authored wrapper script couldn't be verified without running it.
- [ ] Expose `GameRules` on the setup screen: deck count, H17/S17 toggle, DAS on/off.
- [ ] Let the player leave the table voluntarily after any settled round, not only when
      bankrupt (the original asked "vuoi giocare ancora?" — do you want to keep
      playing? — after every hand).

### Rules & gameplay depth
- [ ] **Late surrender** — forfeit half the bet before acting. Deliberately left out of
      this pass to keep the initial scope to "fix the original's bugs + add a GUI"
      rather than inventing rule variants the original never had; `Hand` would need a
      `SURRENDERED` status and `BlackjackTable` a `surrender()` action gated by a new
      `GameRules.surrenderAllowed` flag.
- [ ] **Insurance** side-bet when the dealer shows an Ace.
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
- [ ] Real chip-stack visuals instead of a bet number.
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
