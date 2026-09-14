# Blackjack JavaFX

A graphical, single-player Blackjack table built with JavaFX — a modernized rewrite of
[DavideFornari/BlackJack](https://github.com/DavideFornari/BlackJack), a terminal Java
university project (2019).

See [CLAUDE.md](CLAUDE.md) for the rules reference, what was fixed from the original,
and the upgrade roadmap.

## Running it

Requires JDK 21+ and Maven.

```bash
mvn javafx:run
```

## Running the tests

```bash
mvn test
```

## Project structure

```
src/main/java/io/github/davidefornari/blackjack/
  engine/   pure Java game logic (no JavaFX dependency, fully unit-tested)
  ui/       JavaFX scene graph, wired directly in Java (no FXML)
src/test/java/.../engine/   JUnit 5 tests
```
