# Minecraft Version Bump Checklist

`gradle.properties` `minecraftVersion` is the single source of truth for the target Minecraft
version. Most build outputs derive from it. This document lists every edit required to move Iris
to a new Minecraft version, in order.

## Source of truth

`gradle.properties`:

- `minecraftVersion` — target MC version (e.g. `26.2`). Drives the Bukkit plugin `api-version`,
  `BuildConstants.MINECRAFT_VERSION`, the `com.mojang:minecraft` coordinate, all mod-metadata
  minecraft ranges, and every dist/jar artifact name.
- `fabricLoaderVersion` — Fabric Loader version.
- `forgeVersion` — Forge version (`<mc>-<forge>`).
- `neoForgeVersion` — NeoForge version.
- `irisVersion` — bump the trailing `-<mc>` suffix to match (e.g. `4.0.0-26.2` -> `4.0.0-27.0`).

## Ordered steps

1. Edit `gradle.properties`: update `minecraftVersion`, `fabricLoaderVersion`, `forgeVersion`,
   `neoForgeVersion`, and the `irisVersion` suffix.

2. Edit `gradle/libs.versions.toml`:
   - `spigot` — the Spigot/Paper API pin used to compile against (`<mc>-R0.1-SNAPSHOT`).
   - `fabricApi-*` — the six Fabric API module versions, if the new MC requires different
     Fabric API builds. Each module is versioned independently (`<version>+<build-hash>`).

3. Edit `core/src/main/java/art/arcane/iris/core/nms/datapack/DataVersion.java` (manual, structural):
   - Append a new enum constant `V<major>_<minor>("<mc>", <packFormat>, <DataFixer>::new)`.
   - `packFormat` comes from https://minecraft.wiki/w/Pack_format.
   - `getLatest()` returns the last enum constant, so append; do not reorder.
   - Add a matching `IDataFixer` implementation under `core/src/main/java/art/arcane/iris/core/nms/datapack/`
     if the datapack format changed.

4. Register the new Bukkit NMS binding module:
   - `settings.gradle` — add `include(':adapters:bukkit:nms:v<major>_<minor>_R<rev>')`.
   - `build.gradle` — add the binding to the `nmsBindings` map:
     `v<major>_<minor>_R<rev>: '<spigot-nms-build-version>'` (e.g. `'26.2.build.25-alpha'`).
   - Create the binding sources under `adapters/bukkit/nms/v<major>_<minor>_R<rev>/`.

5. Update loader version-range metadata (manual floors/ranges only; the `minecraft` ranges are
   templated from `minecraftVersion` and need no edit):
   - `adapters/fabric/src/main/resources/fabric.mod.json` — `minecraft` is `~${minecraftVersion}`
     (auto). Update the `fabricloader` floor (`>=0.19.0`) if the loader minimum changes, and the
     `jars` list if the bundled Fabric API modules change.
   - `adapters/forge/src/main/resources/META-INF/mods.toml` — `minecraft` versionRange is
     `[${minecraftVersion}]` (auto). Update `loaderVersion` (`[64,)`) and the `forge` dependency
     versionRange for the new Forge line.
   - `adapters/neoforge/src/main/resources/META-INF/neoforge.mods.toml` — `minecraft` versionRange
     is `[${minecraftVersion}]` (auto). Update `loaderVersion` and the `neoforge` dependency range
     if the loader minimum changes.

6. Build and verify:
   - `./gradlew :core:check`
   - `./gradlew buildBukkit`
   - `./gradlew buildFabric`
   - `./gradlew buildForge`
   - `./gradlew buildNeoforge`

## Derived automatically (do not hand-edit on a version bump)

- Bukkit plugin `api-version` — `adapters/bukkit/plugin/build.gradle` reads `minecraftVersion`.
- `BuildConstants.MINECRAFT_VERSION` — stamped by the `generateTemplates` task in
  `core/build.gradle` from `minecraftVersion`; consumed by `Tasks.supportedVersions`.
- Mod-metadata `minecraft` version ranges — templated from `minecraftVersion` at `processResources`.
- Dist/jar artifact names and the `com.mojang:minecraft` coordinate — composed from
  `minecraftVersion` in the build scripts.

## Notes

- `build.gradle`, the adapter `build.gradle` files, and `settings.gradle` carry `.getOrElse('26.2')`
  defensive defaults for the version properties. `gradle.properties` always overrides them, so a
  bump does not require touching those fallbacks; refresh them only if the checked-in default should
  track the current release.
- The Java literal `"26.2"` intentionally remains in `DataVersion.java` (structural enum constant),
  `core/src/test/java/art/arcane/iris/core/nms/MinecraftVersionTest.java`, and
  `core/src/test/java/art/arcane/iris/core/lifecycle/PaperLibBootstrapTest.java`. The test files use
  MC version strings as parser fixtures, not as a version source; update them only when the version
  string formats they exercise change.
