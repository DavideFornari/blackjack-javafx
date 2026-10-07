# Blackjack JavaFX

A graphical, single-player Blackjack table built with JavaFX — a modernized rewrite of
[DavideFornari/BlackJack](https://github.com/DavideFornari/BlackJack), a terminal Java
university project (2019).

Multi-deck shoe, chip-based betting, hit/stand/double/split, insurance, and four
card-counting systems. The game logic lives in a JavaFX-free `engine` package that is
unit-tested headless.

See [CLAUDE.md](CLAUDE.md) for the rules reference, what was fixed from the original,
and the improvement backlog.

## Requirements

| | Version | Notes |
|---|---|---|
| JDK | 21 or newer | The build targets release 21 (`maven.compiler.release`). |
| Maven | 3.6 or newer | There is no Maven Wrapper yet, so Maven must be installed. |
| OS | Windows | The JavaFX native classifier is pinned to `win`. See [other platforms](#running-on-macos-or-linux). |

Verified with Maven 3.9.16 on Temurin JDK 21.0.12, Windows 11.

> **Check the JDK that Maven itself uses, not just `java`.** They can differ. `mvn -v`
> prints the one that matters:
>
> ```
> Apache Maven 3.9.16
> Java version: 21.0.12.1, vendor: Eclipse Adoptium
> ```
>
> On the machine this was developed on, `java -version` reports 25 while Maven runs on 21 —
> harmless, but it means `java -version` alone won't tell you whether the build will work.

### Installing the toolchain

**Windows** — [winget](https://learn.microsoft.com/windows/package-manager/) or
[Chocolatey](https://chocolatey.org/):

```powershell
winget install EclipseAdoptium.Temurin.21.JDK
winget install Apache.Maven
```

**macOS** — [Homebrew](https://brew.sh/):

```bash
brew install --cask temurin@21
brew install maven
```

**Linux** — Debian/Ubuntu:

```bash
sudo apt install openjdk-21-jdk maven
```

If `mvn -v` reports the wrong JDK afterwards, point `JAVA_HOME` at your 21+ install.

## Quick start

```bash
git clone https://github.com/DavideFornari/blackjack-javafx.git
cd blackjack-javafx
mvn javafx:run
```

That's the whole setup. There is **no separate dependency-install step** — Maven downloads
everything the first time you run any build command, so the first launch takes noticeably
longer than later ones.

## Dependencies

All declared in [pom.xml](pom.xml) and fetched automatically into your local Maven
repository (`~/.m2/repository`, or `%USERPROFILE%\.m2\repository` on Windows). Nothing is
vendored into the repo and nothing needs installing by hand.

| Dependency | Version | Scope |
|---|---|---|
| `org.openjfx:javafx-controls` | 21.0.7 | compile — pulls in `javafx-graphics` and `javafx-base` |
| `org.junit.jupiter:junit-jupiter` | 5.12.2 | test |
| `org.junit.platform:junit-platform-launcher` | 1.12.2 | test |

Build plugins: `maven-compiler-plugin` 3.15.0, `maven-surefire-plugin` 3.5.6, and
`javafx-maven-plugin` 0.0.8, which is what makes `mvn javafx:run` work without you having to
assemble a `--module-path` by hand.

To pre-download everything — useful before going offline, or to warm a CI cache:

```bash
mvn dependency:go-offline
```

## Running the app

```bash
mvn javafx:run
```

Launches `io.github.davidefornari.blackjack.ui.BlackjackApp`. The window has no fixed size;
it grows to fit dealt cards, wager stacks and split hands.

`mvn package` builds a jar, but **`java -jar` will not run it** — the manifest has no
`Main-Class`, and the JavaFX native modules aren't bundled either. `mvn javafx:run` is the
supported way to launch. A self-contained installer via `jpackage` is on the backlog.

### Keyboard shortcuts

| Key | Action |
|---|---|
| `Enter` | Deal; after a round, start the next one |
| `H` / `S` / `D` / `P` | Hit / Stand / Double / Split |

Your last bet is placed again automatically at the start of each round, so `Enter` repeats it.

## Running the tests

```bash
mvn test
```

42 JUnit 5 tests covering the engine. They need **no JavaFX runtime and no display**, because
the `engine` package has zero JavaFX dependency — so they run fine over SSH, in a container,
or in CI without a virtual framebuffer.

## Running on macOS or Linux

The JavaFX dependency carries a hardcoded `win` native classifier, so a default build is
Windows-only. Override the platform property to pull the right natives:

```bash
mvn javafx:run -Djavafx.platform=mac-aarch64
```

Valid values: `win`, `win-x86`, `mac`, `mac-aarch64`, `linux`, `linux-aarch64`.

The override resolves correctly — it propagates to `javafx-controls`, `javafx-graphics` and
`javafx-base` alike. On Linux the app has been launched and driven under a virtual display
(Xvfb), but nobody has played it on a real macOS or Linux desktop yet, so treat those as
lightly tested rather than broken. Replacing the hardcoded classifier with `os-maven-plugin` or
per-OS profiles, so this works with no flag, is on the backlog.

Note that `mvn test` needs no override on any platform, since the engine tests never touch
JavaFX.

## Project structure

```
src/main/java/io/github/davidefornari/blackjack/
  engine/   pure Java game logic (no JavaFX dependency, fully unit-tested)
  ui/       JavaFX scene graph, wired directly in Java (no FXML)
src/main/resources/.../ui/   blackjack.css, chip and window-icon images
src/test/java/.../engine/    JUnit 5 tests
```

## Troubleshooting

**`mvn javafx:run` prints `BUILD SUCCESS` but no window appears.** The JavaFX toolkit
couldn't attach to a display and exited cleanly. Expected over SSH or in a headless
container; on a desktop, check that the platform classifier matches your OS.

**`no main manifest attribute` or `Error: JavaFX runtime components are missing`.** You're
launching the built jar directly instead of through the plugin. Use `mvn javafx:run`.

**`release version 21 not supported`.** Maven is running on a JDK older than 21. Check with
`mvn -v` — not `java -version` — and set `JAVA_HOME` accordingly.

**`Could not resolve dependencies ... javafx-controls:jar:win`.** Usually a first-run network
problem rather than a real resolution failure. Retry, or `mvn -U dependency:go-offline` to
force a fresh fetch.

## License

[MIT](LICENSE)
