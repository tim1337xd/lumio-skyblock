# Credits

## Credits

What this add-on took from them: ideas, formats, rules and data.

| Who | What it was | License |
| --- | --- | --- |
| [SkyHanni](https://github.com/hannibal002/SkyHanni) | The ideas behind the SkyBlock panels and trackers, and the rules of the Chronomatron and Ultrasequencer detection (the Experimentation Table helper and tracker, the dragon tracker, garden pests and visitors, sea creatures, Mineshaft pity, corpses, custom scoreboard, egg sacs, the Cocoon Overlay, the enchant colors, the Mouse Sensitivity Reducer behind Mouse lock), Pet Candy Used and Explosions Hider, and how pet icons tell pets apart (type, rarity, skin, its variant and the pet's UUID). The corpse sharing format, which this add-on reads and writes so both mods understand each other | LGPL-2.1 |
| [SkySoft](https://modrinth.com/mod/skysoft) | The ideas behind Keep Terrain Loaded, the storage overlay and its layout, and the item list | LGPL-3.0 |
| [NotEnoughUpdates](https://github.com/NotEnoughUpdates/NotEnoughUpdates) | The idea of the item list with its recipes | LGPL-3.0 |
| [Bobby](https://github.com/Johni0702/bobby) | How the world is cached for Keep Terrain Loaded | GPL-3.0 |
| [NoammAddons](https://github.com/Noamm9/NoammAddons) and [Odin](https://github.com/odtheking/Odin) | The rules for where an etherwarp lands | CC0-1.0, BSD-3-Clause |
| [NoFrills](https://github.com/WhatYouThing/NoFrills) | The idea of Break Reset Fix, and how it is done | GPL-3.0 |
| [Ping Offset Miner](https://github.com/Revvilon/PingOffsetMiner) | The idea of tick glide, and the default value of its extra mining speed on gemstones | CC0-1.0 |
| [Skyblocker](https://github.com/SkyblockerMod/Skyblocker) | The idea of item protection, and which menus and consumables it covers | LGPL-3.0 |
| [NotEnoughUpdates repository](https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO) | Every item and its recipes for the item list. Downloaded while playing, not bundled (see below) | MIT |

The jar bundles no libraries and no fonts. It needs [Lumio](https://github.com/tim1337xd/Lumio), which brings its API and its fonts.

No textures or other assets of Mojang or Hypixel are in this repository. Item names, warps and chat messages are read from the game and written
here only as far as the add-on has to recognize them.

## Third-party services

The add-on asks these services only for the features below. Each one sees your IP address, as with any web request. Your Minecraft account and
session are never read or sent, no API key is used, and every request uses HTTPS.

| Service | Used for | Feature | What is sent | When | How to turn it off | Links |
| --- | --- | --- | --- | --- | --- | --- |
| Hypixel API (`/v2/skyblock/bazaar`, `/v2/resources/skyblock/items`) | Bazaar prices and item values | Profit trackers, Item list | Nothing but the request | Bazaar every 2 minutes and items every 10 minutes, only while a tracker runs on SkyBlock or the Item list is open | Turn off the profit trackers and the Item list | [Policies](https://developer.hypixel.net/policies/), [privacy](https://hypixel.net/privacy) |
| Hypixel API (`/v2/resources/skyblock/election`) | The mayor and minister with their perks | Info bar's Mayor segment, Custom scoreboard's Mayor line, Slayer profit tracker | Nothing but the request | At most every 20 minutes, while one of them needs it | Hide the segment and the line, and turn off the slayer tracker | As above |
| Moulberry's auction data (`moulberry.codes/lowestbin.json`) | Lowest auction prices | Profit trackers, Item list | Nothing but the request | Every 10 minutes, under the same conditions as bazaar prices | As for bazaar prices | [NotEnoughUpdates](https://github.com/NotEnoughUpdates/NotEnoughUpdates) |
| Coflnet (`sky.coflnet.com/api/prices/neu`) | The same list when Moulberry's doesn't answer | Profit trackers, Item list | Nothing but the request | Only as that fallback | As for bazaar prices | [Site](https://sky.coflnet.com) (I found no separate terms page) |
| Coflnet (`sky.coflnet.com/api/item/price/...`) | The price of a single auction item, and its recent median | Item windows, Profit trackers | The item's SkyBlock id | For items on display that aren't on the bazaar: one at a time, at most once a second, kept for 10 minutes | Close the item window, or turn off the Item list and the trackers | As above |
| Coflnet (`sky.coflnet.com/api/item/filters`, `/api/item/price/...`, `/api/mod/pricing/breakdown`) | What an item is worth with its enchants, stars, recombobulator, gems and so on: lowest BIN and median of items like it, and its craft cost | Item tooltips | Only the item's own data, without its UUID and date; never your name or profile | When you keep the mouse on such an item for a moment; kept for a few minutes | Turn off Lowest BIN, Median and Craft cost in the tooltip lines | [Coflnet](https://sky.coflnet.com) |
| GitHub (NotEnoughUpdates repository) | Every item and its recipes, and the pets' heads and skins | Item list, Command suggestions, Pet panel | Nothing but the request | At most once a day, when one of them first needs the list. It stays in `config/lumio/` | Turn off the Item list, Command suggestions and the Pet panel | [Repository](https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO), [GitHub privacy](https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement) |

An item window's wiki link comes from that repository and opens in your browser only after you click it and confirm.

### Local connections

The add-on makes none of its own. Prices it remembers for the price line stay in `config/lumio/`.

### Hypixel

The add-on sends nothing to Hypixel of its own except what "Using it on Hypixel" in the [README](README.md) lists.

## Not affiliated

Lumio: Hypixel SkyBlock isn't affiliated with or endorsed by any of these projects or services.
