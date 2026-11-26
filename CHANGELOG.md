# Changelog

All notable changes to Lumio: Hypixel SkyBlock. The short list shown in game is `src/main/resources/assets/lumio-skyblock/changelog.txt`; this file has more detail and never contradicts it.

## 1.0.0-beta.2 (2026-11-25)

The second beta. Coming from Beta 1? Install this one by hand once: replace the old jar. From Beta 2 on, Lumio tells you about new versions itself. Needs Lumio 1.0.0-beta.2.

### New

- **Dungeons**: a page of their own. A map, the score with its rank, splits against your best run, waypoints per room (Odin's lists in and out), outlines for starred mobs, bats, mini bosses and teammates while you can see them, hints for every room puzzle, Livid, Thorn and blood camp helpers, chest profit, keys for the class ability and the ultimate, invincibility timers and party announcements. F7 and M7 (boss timers, terminal status, terminal and device solvers, an offline practice) are marked as work in progress.
- **Party Finder Helper**: which parties you can join and which classes are still free, at a glance. It never clicks or joins.
- **Fishing alerts and a Hotspot helper**: an alert for the sea creatures you choose and the rare catches of others; hotspots drawn on their water, and a waypoint where the Hotspot Radar's trail leads, from one use. Every sea creature is known (from SkyHanni's data, with its license).
- **Mining**: the Powder Helper with a powder tracker and the treasure chests of the Crystal Hollows, a Crystal Hollows map of your lobby, and a Precision Mining marker.
- **Inventory buttons**: your own commands on buttons around the inventory and chests, one command per click.
- **Party commands, slot binds, loadout keys, an item pickup log and Farming Keys.**
- **Auto Requeue**: the next run after a delay you can cancel, only while you lead and nobody asked for downtime.
- **Pest highlight**: a glow on the pests you can see.
- **Bite timer**: the countdown the game shows at your bobber, and its "!!!", large on the screen.

New modules are off at first.

### Changed

- **Item search**: calm panels moved by their grip, Pinned and Recent, the item's real tooltip on hover, and a new item window with Recipe, Market and Drops, the Bazaar or the Auction House in one click and a link to the wiki.
- **Item tooltips**: price lines show only when they have a number.
- **Settings**: pages with named groups, short texts and help on hover.
- **Keys**: none is set at first; pick the ones you want.
- **Corpses**: their value in chat after the loot, a corpse panel that shows only in a mineshaft, and chest profit in green or red.
- **Mouse lock** locks by itself while you farm on a plot, without flicker on jumps.
- **Sea creatures nearby** alert only once you can see them.
- **Updates** come through Lumio, checked with its release signature; nothing downloads without your click.
- **Shards** you charm, catch or get from a loot share count in the profit trackers, with their Bazaar value.
- **A fresh install** starts with new default settings; settings you already have stay as they are.

### Fixed

- Profit trackers count rare drops, loot shares and RNG meter drops once, also when the kill was not yours or the item went to the stash; an enchanted book counts under its enchantment ("Flash I"). They no longer count crafted or moved items, and items a Personal Compactor or Super Compactor turns into their enchanted form count once, as what was collected, also while picking up fast; a gain taken back leaves only the tracker that counted it. They keep their values in a file of their own per profile.
- Prices for Bazaar items (Bazaar buy and sell with the volume), enchanted books by their enchantment, pets by rarity, runes and shards, the same in tooltips, the item window and pinned items.
- Fishing trackers work on every island and count creatures they don't know yet.
- Item search: matches light up only in highlight mode.
- Smaller fixes for the pickaxe timer, storage, arrows, splits, enchant colors and the tab list.

## 1.0.0-beta.1 (2026-11-07)

The first public version. This is a beta: feedback is welcome, and things may still change. Needs [Lumio](https://github.com/tim1337xd/Lumio) 1.0.0-beta.1 or newer.

- **Panels**: your pet, equipment, deployables and arrows (a bar or a small panel, with a warning when they run low), and profit trackers for mining, farming, slayers, fishing and corpses, with a rich and a simple look, moved and sized in the HUD editor.
- **Items**: an item search with its own item window over the inventory (recipes, forge, NPC shops, trades, uses, what a mob drops and who drops an item, with chances), prices and favorites, and tooltips with dungeon quality, market or bazaar prices, craft cost, trend and NPC price.
- **Storage**: all your ender chest pages and backpacks at once, over the storage menu, with a search; a panel beside your inventory with a shortcut to every page; and on its own key, an overview of them as you last saw them, per profile, without asking the server.
- **Wardrobe**: your character wearing every armor set, all pages at once, with your own names and favorites; a click on a set is one click on its button, and "Edit" shows the game's own menu.
- **Experimentation Table**: a helper that keeps what the game showed you in sight (cards you flipped, the order of the round), a block for wrong clicks in Chronomatron and Ultrasequencer on your island, off at first (hold Shift to click anyway), alerts for an ULTRA-RARE book, the most clicks and a missing Guardian pet, and a profit panel with tables, books and the dry streak.
- **Dragons**: a profit panel with what you pick up from a fight, your Summoning Eyes as a cost, and your last three dragons beside it.
- **Grappling Hook**: its cooldown as a small timer and bar while you hold it. It starts when the hook really pulls you; using it too early changes nothing.
- **Speed**: your Speed as a status bar, filling up to the cap. Off at first; turn it on in Status bars.
- **Slayer**: the boss's phases, also right above it, a carry mode, highlights for minibosses, your boss and its drops while you can see them, shown only on the slayer's islands or with its boss near.
- **Item protection**: press V over an item (or `/lumio protect`) and it can't be dropped, sold, salvaged, traded, auctioned or put into a dungeon chest or an item frame by accident; a small lock shows it. It only holds back your own click or key, and sends nothing. Valuable consumables also wait until they have evolved.
- **Mining, fishing and farming**: Break Reset Fix (off at first: item updates from the server no longer reset your block breaking), commissions, Mineshaft pity, corpses, abilities, the HOTM tree, sea creatures, pests, visitors, and Mouse lock in the Garden.
- **More**: a SkyBlock tab in the chat, drop alerts, command suggestions, enchant colors, a custom scoreboard with short numbers, the SkyBlock parts of the info bar, status bars and tab list, Etherwarp and Keep Terrain Loaded.
