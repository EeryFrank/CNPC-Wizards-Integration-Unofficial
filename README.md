# CNPC Wizards Integration (Unofficial)

> [!IMPORTANT]
> **项目状态：已归档 / Project Status: Archived**
>
> 这是给第三方甲方定制开发的项目。委托方未完成约定款项支付（俗称“逃单”），合作因此终止。现在公开的是开发于 **2026-08-13** 停止时已经完成的成果；自 **2026-08-27** 起，我将本仓库按现状归档，后续不再开发、维护、适配或提供定制支持。为保护隐私，不披露委托方身份、私人沟通或支付明细。

Fabric 1.21.1 compatibility layer that lets non-player NPC entities enter existing Spell Engine and Wizards casting flows. It is an independent, unofficial project and is not affiliated with Mojang, Fabric, CustomNPCs, Spell Engine, Wizards, or their authors.

## Snapshot

- Minecraft: `1.21.1`
- Platform: Fabric
- Java: `21`
- Version: `0.0.1-Demo`
- Author and copyright holder: `QiZhang`
- License: `GPL-3.0-only`
- Development cutoff: `2026-08-13`
- Public archive date: `2026-08-27`
- Support status: none; issues and feature requests are not accepted

This clean public snapshot deliberately excludes the former customer's client pack, server, worlds, configuration, logs, commercial documents, payment records, deployment evidence, and all third-party mod JARs.

## Included work

- One shared `NpcSpellCastingApi` / `CastSession` bridge for NPC spell requests.
- Target strategies, per-caster/per-spell cooldowns, recursion protection, cast-state protection, and structured trace events.
- Four phase-one spell routes through the same bridge:
  - `wizards:fireball`
  - `wizards:frost_blizzard`
  - `elemental_wizards_rpg:aqua_bubble_beam`
  - `elemental_wizards_rpg:terra_earthquake`
- CustomNPCs ECMAScript examples for remembered-target automatic casting and four-spell rotation.
- An initial visual skill-block editor with ordered blocks, bounded repeat loops, choice-based spell/effect/action parameters, server-side validation, and NPC binding.
- Unit and contract tests for bridge flow, routes, cooldowns, scripts, block validation, layout, choices, and loop limits.

## Known limits

- This is an unfinished demo snapshot, not a supported production release.
- The visual editor currently provides a sequential chain and bounded loops, not branches, variables, arbitrary expressions, or arbitrary Java/ECMAScript generation.
- The only block-program trigger implemented in this snapshot is “受到攻击时 / when damaged”.
- CustomNPCs UI injection was developed against a specific unofficial Fabric build and may not work with other builds.
- Third-party spell visuals, sounds, targeting, spawned entities, damage, healing, status effects, and cancellation behavior require the corresponding runtime mods.
- Automated tests and build success do not prove behavior in a real multiplayer integration pack. Gameplay acceptance remains `NEEDS_MANUAL_VALIDATION`.

## Build

Requirements: JDK 21 and an internet connection for Gradle dependency resolution.

Windows:

```powershell
.\gradlew.bat clean test build
```

Linux/macOS:

```bash
./gradlew clean test build
```

Artifacts are written to `build/libs/`. The build resolves Fabric, Spell Engine, and Spell Power for compilation; it does not download or bundle Wizards, Elemental Wizards, CustomNPCs, a Minecraft client, or a server pack.

## Usage

- The ECMAScript entry points and configuration fields are documented in [`examples/cnpc/README.md`](examples/cnpc/README.md).
- The visual editor workflow, available blocks, limits, and manual validation checklist are documented in [`docs/积木技能编辑器初版说明.md`](docs/积木技能编辑器初版说明.md).
- All spell calls ultimately enter `CnPcWizardsIntegration.castingApi()` and the shared `CastSession` flow.

## Third-party dependencies

No third-party mod source, assets, or mod JARs are included. Names and IDs are used only to describe compatibility. See [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) before redistributing a build; every runtime dependency remains under its own license and terms.

## License

Original project source and functional material in this repository are licensed under the [GNU General Public License v3.0 only](LICENSE). Project-owned art, audio, and branding are permission-required by default and are not covered by the GPL. Historical grants and third-party terms remain unchanged. See [`LICENSE_POLICY.md`](LICENSE_POLICY.md), [`ASSET_LICENSE.md`](ASSET_LICENSE.md), and [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) for the exact path and rights boundaries.

The software is provided **as is**, without warranty or support. See [`ARCHIVE_NOTICE.md`](ARCHIVE_NOTICE.md) for the full archive statement.
