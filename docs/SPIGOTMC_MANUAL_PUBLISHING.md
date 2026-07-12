# SpigotMC Manual Publishing Checklist

This file is for the manual SpigotMC upload step. The resource should be published as a free resource.

## Resource Decision

- Resource type: Free
- Price: 0
- License: MIT
- Reason: CrossTrade depends on the public Paper/Vault/Floodgate ecosystem, requires server-side configuration and benefits from a low-friction install path for administrator feedback.

## Upload Package

- Upload file: `target/CrossTrade-2.2.1.jar`
- GitHub release: `https://github.com/yangzijian52/CrossTrade/releases/tag/v2.2.1`
- Source code: `https://github.com/yangzijian52/CrossTrade`
- License file: `LICENSE`
- Changelog: `CHANGELOG.md`
- BBCode description: `docs/SPIGOTMC_BBCODE.txt`

## Suggested SpigotMC Fields

- Resource name: CrossTrade
- Tagline: Secure direct trading and player marketplace for Paper 26.2
- Category: Bukkit / Economy or Mechanics, depending on the available SpigotMC category list
- Native Minecraft version: Paper 26.2 target environment
- Tested server software: Paper 26.2
- Java version: Java 25
- Dependencies: Vault and an economy plugin are required for money transactions
- Optional dependencies: Floodgate for Bedrock forms
- Price: Free
- Source available: Yes

## Important Warnings

- Do not claim Spigot or CraftBukkit runtime support unless you test it. CrossTrade is built for Paper 26.2.
- Do not claim NMS compatibility. CrossTrade does not use NMS.
- The SpigotMC resource page and support are English-only. Chinese-language support is not provided on SpigotMC.
- Vault is soft-loaded, but money features require Vault and a working economy plugin.
- Floodgate is optional. Without Floodgate, Java players can still use the plugin.
- Server owners should back up `plugins/CrossTrade` before upgrading.

## Manual Publish Steps

1. Build the jar with `mvn clean package`.
2. Confirm `target/CrossTrade-2.2.1.jar` exists.
3. Open the GitHub release and copy the download link.
4. Create or update the SpigotMC resource manually.
5. Paste `docs/SPIGOTMC_BBCODE.txt` into the SpigotMC description field.
6. Upload `target/CrossTrade-2.2.1.jar`.
7. Select free resource pricing.
8. Add the GitHub source and license links.
9. After publishing, verify the public page shows the English-only support notice.
