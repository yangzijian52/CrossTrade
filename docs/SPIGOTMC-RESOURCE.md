[CENTER][SIZE=7][B]CrossTrade[/B][/SIZE]
[SIZE=4]Secure Direct Trading and Player Marketplace for Paper 26.2[/SIZE][/CENTER]

[COLOR=#ff4d4d][B]Language notice:[/B][/COLOR] The SpigotMC resource page, documentation and support channel are English-only. Chinese-language support is not provided on SpigotMC.

[SIZE=5][B]About CrossTrade[/B][/SIZE]
CrossTrade is a secure trading plugin for Paper 26.2 servers. It keeps classic player-to-player direct trading available while adding a persistent player marketplace backed by SQLite.

Players can trade face-to-face, send remote trade requests to online players, list items for sale, browse seller shops, buy partial quantities, receive expired or cancelled items through a safe mailbox and collect seller earnings through a protected payout ledger.

CrossTrade is designed for servers where item safety matters. Items are never intentionally dropped into the world as a recovery mechanism. If an inventory cannot receive an item, the item is saved into the player's mailbox.

[SIZE=5][B]Compatibility[/B][/SIZE]
[LIST]
[*][B]Server software:[/B] Paper 26.2 only
[*][B]Java:[/B] 25 or newer
[*][B]Required for money transactions:[/B] Vault plus a compatible economy plugin
[*][B]Optional dependency:[/B] Floodgate for Bedrock form support
[*][B]Storage:[/B] SQLite, bundled through the final jar
[*][B]Server testing:[/B] CrossTrade 2.2.0 was successfully tested on a live Paper 26.2 server; 2.2.1 only adds publishing documents and metadata
[*]Spigot and CraftBukkit compatibility has not been tested and is not claimed
[/LIST]

[SIZE=5][B]Free Resource[/B][/SIZE]
CrossTrade is published as a free resource under the MIT License. The plugin depends on the public Paper, Vault and Floodgate ecosystem, and server administrators should be able to test it without a purchase barrier before enabling money-based trading on production servers.

[SIZE=5][B]Main Features[/B][/SIZE]
[LIST]
[*]Secure direct player trading
[*]Remote online-player trade requests from the market center
[*]54-slot direct trade GUI with symmetric item areas
[*]Configurable money buttons: +1, +10, +100, +1000, -1, -10, -100 and -1000
[*]Trade request cooldowns and expiry validation
[*]Confirmation reset when either side changes items or money
[*]Final balance and inventory-space validation before completion
[*]Net settlement for two-way money trades
[*]Safe cancellation on close, quit, death, world change and shutdown
[*]Protection against shift-click, drag, hotbar swap, double-click, drop and offhand bypasses
[*]Player market plaza grouped by seller
[*]Seller shop pages with real ItemStack icons
[*]Item listing flow with name, quantity, unit price and 1-10 day duration
[*]Partial purchases
[*]Listing expiry, sold-out handling and player cancellation
[*]Restock and relist support for inactive listings
[*]Mailbox storage for returned or recovered items
[*]Seller payout ledger for earnings that cannot be paid immediately
[*]Player transaction history with clear-display support
[*]Admin inspection, forced removal, review, compensation and backup commands
[*]Separate gui.yml for configurable GUI titles, fillers and static buttons
[/LIST]

[SIZE=5][B]Market Safety[/B][/SIZE]
Market listings store the original Paper ItemStack byte data plus a SHA-256 fingerprint. Item quantity is tracked separately, and deliveries are split by the real max stack size.

Different durability, enchantments, names, lore, attributes, persistent data, custom model data or item components are treated as different items. Shulker boxes and other container items keep their contents as part of the item data, so two containers with different contents are not merged.

[SIZE=5][B]Transaction Safety[/B][/SIZE]
Purchases use a guarded transaction flow with inventory simulation, Vault balance checks, item delivery checks, seller payout records and review states for uncertain external failures.

If a buyer cannot receive all purchased items, the transaction fails instead of silently reducing the quantity. If a return cannot fit in an inventory, the item remains in the mailbox.

[SIZE=5][B]Bedrock Support[/B][/SIZE]
Floodgate is optional. Without Floodgate, Java players can still use the plugin. When Floodgate is installed, Bedrock players can use form-based market flows for listing and buying.

[SIZE=5][B]Links[/B][/SIZE]
[LIST]
[*][URL=https://github.com/yangzijian52/CrossTrade]Source Code[/URL]
[*][URL=https://github.com/yangzijian52/CrossTrade/releases]Downloads[/URL]
[*][URL=https://github.com/yangzijian52/CrossTrade/issues]Support and Issues[/URL]
[*][URL=https://github.com/yangzijian52/CrossTrade/blob/main/LICENSE]MIT License[/URL]
[/LIST]

[SIZE=5][B]Important Notes[/B][/SIZE]
[LIST]
[*]This resource targets Paper 26.2, not generic Spigot.
[*]Vault is soft-loaded, but money features require Vault and a working economy provider.
[*]Do not use /reload or third-party hot unload tools with this plugin.
[*]Back up plugins/CrossTrade before upgrading.
[*]The SpigotMC page and support are English-only. Chinese-language support is not provided on SpigotMC.
[/LIST]
