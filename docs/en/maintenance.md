# Repository maintenance

Written for whoever maintains this repository next (including your future self): where files go, who owns a given fact, what to sync when you change something, and how to release.

## Directory layout

```
MC-The-Fourth-Frequency/
├── README.md / README.en.md      Player-facing overview (bilingual)
├── LICENSE                       All Rights Reserved
├── build.gradle                  Loom, source sets, unitTest, client-suite parameters
├── gradle.properties             The single source of truth for versions and dependencies
├── settings.gradle
├── docs/
│   ├── README.md                 Bilingual index and fact ownership
│   ├── zh/                       Chinese documents (source of truth)
│   ├── en/                       English documents (synchronised translation)
│   └── art/                      Reference art and manifests — paths are referenced by tests, do not move
├── src/
│   ├── main/java/…               common: authoritative gameplay, persistence, protocol, policies
│   ├── main/resources/           fabric.mod.json, mixin manifest, resources, lang, data, shaders
│   ├── client/java/…             Rendering, UI, sound scheduling, mixins, local sequences
│   ├── test/java/…               Pure JUnit, resource and cross-file contracts
│   └── gametest/java/…           Server and client Minecraft runtime acceptance
├── tools/                        The reproducible asset pipeline (Python + Pillow)
│   └── assets/                   Master source material
└── archive/                      Local archive, not tracked by Git
```

`bin/`, `build/`, `run/`, `logs/`, `.gradle/`, `.planning/` and `.claude/` are all excluded by `.gitignore` as machine artefacts or agent state.

## Hard constraints

Check these before changing anything. Every one of them fails as "compiles fine, explodes at runtime or at build time":

| Constraint | Consequence of violating it |
|---|---|
| Paths and filenames under `docs/art/**` | `ResourceContractTest`, `PostFilterContractTest`, `WorldInterfaceAudioManifestTest`, `WorldInterfaceSummonTimelineTest` and several `tools/*.py` read them at fixed paths; renaming turns tests red |
| `src/main/resources/thefourthfrequency.mixins.json` uses `defaultRequire: 1` | Adding, removing or renaming a mixin without updating the manifest crashes at load |
| A mixin's `@At` target must carry an owner, and `@Shadow` fields must be declared on the target class itself | All four test layers run in a named environment and pass together; only a real remapped JAR crashes at bootstrap |
| `zh_cn.json` and `en_us.json` key sets must be fully symmetric | The contract test asserts symmetry in both directions; one missing key fails |
| The `import static` lines at the top of `TerminalScreen.java` | `ResourceContractTest` asserts palette references as source text, and an IDE's "optimize imports" silently breaks it |
| Protocol payloads may only **append** fields at the end | Decoding is positional; a boolean inserted in the middle silently misaligns every varint after it |

## Fact ownership

Each fact has exactly one owner. Summaries may appear elsewhere, but **numbers are maintained only at the owner**. Conflicts are resolved in this order:

1. The authoritative policies, state machines, schemas, protocols and resource contracts in the current source;
2. Test evidence that **actually ran** against the current workspace;
3. The topic document that owns the area (`docs/zh/*`);
4. The summary in the README;
5. Historical plans, `archive/` and old build artefacts — clues only, never overriding current fact.

Do not treat an older JAR in `build/libs`, a stale test number or a historical record in a document as evidence of the current implementation.

| Topic | Owner |
|---|---|
| Versions, dependencies, artefact names | `gradle.properties` · `src/main/resources/fabric.mod.json` |
| schema / protocol numbers | `PersistenceSchema.CURRENT_VERSION`, each `*Payload.CURRENT_PROTOCOL_VERSION`, `WorldInterfaceState.FORMAT_VERSION`, `WorldInterfaceProtocol.VERSION` |
| World-level mainline, files, discoveries, compatibility migration | `FrequencyWorldData` |
| Finale state | The separate `world_interface` persistence root (format v1) |
| Anomaly, Corrector and mirror rules | `docs/zh/anomalies-and-pursuits.md` |
| Terminal appearance, layout, animation, onboarding | `docs/zh/terminal-ui.md` |
| Finale numbers, actions, ending contracts | `docs/zh/world-interface.md` |
| Music situations and seams | `docs/zh/audio.md` |
| Asset generation, UV and emissive contracts | `docs/zh/art-pipeline.md` |
| Test results and release artefacts | `docs/zh/testing.md` |

## Change sync matrix

| Change type | Sync at minimum |
|---|---|
| Player-visible rules/numbers | Authoritative policy, runtime consumers, HUD/terminal, unit tests, GameTests, both READMEs, acceptance, the owning topic document |
| Terminal / UI / copy | Server snapshot, client layout and exit paths, `zh_cn.json`, `en_us.json`, resource keys, UI tests, `terminal-ui.md` |
| schema / protocol / payload | Version constants, encode/decode, both consumers, migration and old-version rejection, contract tests, `architecture.md` |
| Anomalies / pursuits | Allowed and actual tiers, personal isolation, mirror-dimension exclusion, load covering, item refunds, disconnect/death/restart cleanup, multiplayer concurrency, `anomalies-and-pursuits.md` |
| World Interface / endings | Roster transaction, state machine, per-tick adjudication order, arena protection, client audio/visuals, poem and resource packs, the LAN branch, F8 recovery, `world-interface.md` |
| Client lifecycle / audio | World entry and exit, dimension changes, overlays, resource reloads, `MusicDirector`, the relevant mixins, stop and recovery paths, `audio.md` |
| Assets / resources | Registry or `sounds.json`, bilingual keys, the actual files, generators, the `docs/art/` manifests, resource contract tests, `art-pipeline.md` |

**Bilingual sync is part of the same change.** Chinese is the source of truth, but `docs/en/` must not be allowed to lag: the moment it does, the two trees start maintaining different facts.

## Verification

| Layer | Command |
|---|---|
| Compile common, client and resources | `.\gradlew.bat compileJava compileClientJava processResources --no-daemon` |
| Aggregate JUnit and resource/cross-file contracts | `.\gradlew.bat unitTest --no-daemon` |
| Server Minecraft runtime | `.\gradlew.bat runGameTest --no-daemon` |
| Client suites (default `all`) | `.\gradlew.bat runClientGameTest --no-daemon` |
| Targeted client suite | `.\gradlew.bat runClientGameTest -PtffClientTestSuite=world-interface --no-daemon` |
| Clean release build | `.\gradlew.bat clean build --no-daemon` |

- **A successful compile is not acceptance.** GameTests, client presentation and manual acceptance each cover their own layer.
- **Report only what actually ran this round.** Anything not run, timed out or disturbed by a concurrent build must be stated explicitly.
- `unitTest` enumerates every compiled test class explicitly via `--select-class` rather than scanning the classpath — scanning silently drops any class that fails to load during discovery. **Do not change it back to scanning.**

## Release process

1. Change `mod_version` in `gradle.properties`. That is the single source of truth; `fabric.mod.json` uses the `${version}` placeholder and `processResources` fills it in.
2. Search the whole repository for the old version string (both READMEs, `docs/zh`, `docs/en`) and confirm nothing is left behind.
3. `.\gradlew.bat clean build --no-daemon`. The task graph runs `unitTest` and then the server GameTests.
4. Run the client suites; run the unfiltered `all` at least once.
5. Complete everything automation cannot cover, per the [Manual acceptance checklist](acceptance.md).
6. Write this round's actual results, JAR byte sizes and SHA-256 values into the "Release artefacts" and "Current evidence" sections of [Testing and acceptance](testing.md).
7. Commit and tag.

### Local deployment

After a successful `build`, the remapped JAR is copied into a local Minecraft instance's `mods` folder. The default path is in `build.gradle` and can be overridden or disabled with a Gradle property:

```powershell
# Override the destination
.\gradlew.bat build -PtffDeployDir="D:\SomeLauncher\.minecraft\mods" --no-daemon

# Disable deployment
.\gradlew.bat build -PtffDeployDir= --no-daemon
```

If the destination's root does not exist, deployment is skipped with a notice rather than failing the build — so `build` works on a fresh clone on someone else's machine. **This step hangs off `build` rather than `remapJar`**: a compile or test failure must never replace the last known-good JAR.

## Archive rules

`archive/` is not tracked by Git (see `.gitignore`). When you delete a document, move it into `archive/superseded-docs/` and note in `archive/README.md` where its content went, rather than deleting it outright — the cost of losing a paragraph during a merge is far higher than keeping a local copy.

Nothing in `archive/` is a source of truth, and nothing in `docs/` should link to it.
