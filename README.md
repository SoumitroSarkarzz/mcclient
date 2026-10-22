# MC Client — Fabric 1.21.11

Client-side Minecraft mod for Java 21, Fabric Loader 0.19.5+, and Fabric API 0.141.6+1.21.11.

## Features

- Right Shift opens the module menu; Escape closes it.
- R toggles Kill Aura: attacks the nearest visible hostile mob at full attack cooldown. Player targeting is an explicit setting; teammates are excluded.
- G toggles Flight. Move with WASD, ascend with Space, descend with Sneak. Flight changes local velocity; server authority can correct or reject movement.
- Auto Sprint, toggleable HUD with FPS and module status, adjustable attack range and flight speed.
- Settings persist in `config/mcclient.json`. Active combat/movement toggles reset when leaving a world. Rebind keys through Minecraft Controls.

## Build and run

```sh
./gradlew build
./gradlew runClient
```

Install `build/libs/mcclient-1.1.0+mc1.21.11.jar` (not the sources jar) into a Minecraft 1.21.11 Fabric instance's `mods` folder alongside Fabric API. Use your normal authenticated launcher to join online servers. This project does not modify authentication or provide anti-cheat bypasses. Use modules only where permitted; server compatibility is not guaranteed.

## Validation

Compile with `./gradlew build`. No automated in-game tests are included. In a disposable single-player world, verify the menu and key rebindings, toggle the HUD, change and reload settings, spawn a hostile mob and check cooldown attacks, verify Flight directions and disabling, and reconnect to confirm active modules reset. Multiplayer enforcement and graphical behavior require testing in a running game; a successful build alone does not verify these.

## Install on your computer

Close Minecraft first. After building, run `python tools/install.py` on the computer that has Minecraft installed. For Prism/Modrinth/CurseForge or another custom instance, use `python tools/install.py --mods-dir "/path/to/instance/mods"`. To install a downloaded Actions artifact, unzip it and pass `--jar "/path/to/mcclient-1.1.0+mc1.21.11.jar"`. The installer copies the JAR, preserves the build output, and refuses to overwrite existing versions. It does not install Fabric Loader or Fabric API.

GitHub Actions builds uploaded commits and stores the mod under the `mcclient-fabric-1.21.11` artifact on a successful workflow run.

### macOS / Apple Silicon / TLauncher

The mod JAR is platform independent. Select a **Minecraft 1.21.11 Fabric** profile and Java 21 appropriate for Apple Silicon in your launcher. A vanilla or Forge profile cannot load this mod. Check TLauncher's configured game directory; the default macOS location is `~/Library/Application Support/minecraft`. Copy the built JAR and Fabric API into that directory's `mods` folder, or run:

```sh
python3 tools/install.py --mods-dir "$HOME/Library/Application Support/minecraft/mods"
```

For a custom TLauncher game directory, replace that path with the actual directory's `mods` subfolder. Installation must run on your Mac; the cloud development workspace cannot access its filesystem. Compatibility with `play.blockfun.gg` is unverified. No server-specific anti-cheat bypass is included.

A prebuilt JAR from the verified compile is included at `releases/mcclient-1.1.0+mc1.21.11.jar` with its SHA-256 in `releases/SHA256SUMS`. It has not been tested in a running Minecraft game. To install that file on macOS from a downloaded repository checkout, run:

```sh
python3 tools/install.py --jar releases/mcclient-1.1.0+mc1.21.11.jar --mods-dir "$HOME/Library/Application Support/minecraft/mods"
```

## Upgrading from the 1.21.1 client

Remove `mcclient-1.0.0.jar` from your mods folder before installing this release. Use Minecraft **1.21.11**, Fabric Loader **0.19.5+**, and Fabric API **0.141.6+1.21.11**. The previous 1.21.1 build is incompatible with 1.21.11. Existing `config/mcclient.json` settings remain compatible.
