# Credits

## Credits

What this add-on uses of others: data, formats, techniques and services. Thanks to the SkyBlock modding community, whose mods showed what players need.

| Who | What is used | License |
| --- | --- | --- |
| [Odin](https://github.com/odtheking/Odin) | Data, bundled under `skyblock/dungeon/` with Odin's license file (`LICENSE-odin.txt`): the room data rooms are known by (`rooms.json`), the quiz answers, the Boulder, Creeper Beams, Ice Fill and Water Board solutions and the Arrow Align layouts. The skull texture values of the healer fairy, the Soul Weaver head and the tentacle head in the Visual Filter's dungeon rules. The chat lines its dungeon features read are the ones ours read | BSD-3-Clause |
| [NoammAddons](https://github.com/Noamm9/NoammAddons) | Data, bundled: the skull textures of the Watcher and his blood mobs (`blood-mob-textures.json`, from its BloodCamp.kt) | CC0-1.0 |
| [NoammAddons](https://github.com/Noamm9/NoammAddons) and [Odin](https://github.com/odtheking/Odin) | The rules for which blocks an etherwarp passes and where it lands | CC0-1.0, BSD-3-Clause |
| [NotEnoughUpdates repository](https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO) | Data, downloaded while playing, not bundled (see below): every item and its recipes, the pets' heads and skins, the sacks' items, the enchant levels (for Enchant colors) and the Abiphone contacts | MIT |
| [Skyblocker](https://github.com/SkyblockerMod/Skyblocker) | Two measured values of the Pickobulus throw (the height the pickaxe leaves the hand at, and the blocks the blast mines) | LGPL-3.0 |
| [Odin](https://github.com/odtheking/Odin) | Compatibility: its dungeon waypoint format is read and written, and the party texts "Mimic Killed!", "Prince Killed!" and "Bat Killed!" are its exact words, so other players' mods count them | BSD-3-Clause |
| [SkyHanni-REPO](https://github.com/hannibal002/SkyHanni-REPO) | Data, bundled unchanged under `skyblock/fishing/` with its license file (`LICENSE-skyhanni-repo.txt`): the list of sea creatures (`sea-creatures.json`, its `constants/SeaCreatures.json`): their names, rarities, the game's catch lines, which count as rare, and the names they had before | MIT, Copyright (c) 2022 hannibal2 |
| [SkyHanni](https://github.com/hannibal002/SkyHanni) | Compatibility: its corpse sharing format is read and written, and its party message of a rare catch is read | LGPL-2.1 |

The jar bundles no libraries and no fonts. It needs [Lumio](https://github.com/tim1337xd/Lumio), which brings its API and its fonts.

No textures or other assets of Mojang or Hypixel are in this repository. Item names, warps and chat messages are read from the game and written
here only as far as the add-on has to recognize them.

## Third-party services

The add-on asks these services only for the features below. Each one sees your IP address, as with any web request. Your Minecraft account and
session are never read or sent, no API key is used, and every request uses HTTPS.

| Service | Used for | Feature | What is sent | When | How to turn it off | Links |
| --- | --- | --- | --- | --- | --- | --- |
| Hypixel API (`/v2/skyblock/bazaar`, `/v2/resources/skyblock/items`) | Bazaar prices and item values | Profit trackers, Item list | Nothing but the request | Bazaar at most once a minute while prices are shown and items every 10 minutes, only while a tracker runs on SkyBlock or the Item list is open | Turn off the profit trackers and the Item list | [Policies](https://developer.hypixel.net/policies/), [privacy](https://hypixel.net/privacy) |
| Hypixel API (`/v2/resources/skyblock/election`) | The mayor and minister with their perks | Info bar's Mayor segment, Custom scoreboard's Mayor line, Slayer profit tracker | Nothing but the request | At most every 20 minutes, while one of them needs it | Hide the segment and the line, and turn off the slayer tracker | As above |
| SkyCofl (`sky.coflnet.com/api/prices/neu`) | The lowest auction price of every item, until an item's own numbers come | Profit trackers, Item list | Nothing but the request | Every 5 minutes while prices are shown, through the same pacing as below | As for bazaar prices | [Site](https://sky.coflnet.com) (I found no separate terms page) |
| SkyCofl (`sky.coflnet.com/api/item/price/{id}/bin`, `/analysis?days=7`) | The one source of an auction item's numbers for every view: Lowest BIN, Median 7 days, Volume 7 days (shown with "Prices: SkyCofl", as SkyCofl asks) | Item tooltips, Item windows, Item list, Profit trackers | The item's SkyBlock id | For items on display that aren't on the bazaar: one at a time, at most one request a second (under SkyCofl's limit of 100 a minute), kept for 7 minutes; a 429 pauses every request to SkyCofl for its Retry-After; without an answer the last numbers stay, with their age | Close the item window, or turn off the Item list and the trackers | As above |
| Coflnet (`sky.coflnet.com/api/item/filters`, `/api/item/price/...`, `/api/mod/pricing/breakdown`) | What an item is worth with its enchants, stars, recombobulator, gems and so on: lowest BIN and median of items like it, and its craft cost | Item tooltips | Only the item's own data, without its UUID and date; never your name or profile | When you keep the mouse on such an item for a moment; kept for a few minutes | Turn off Lowest BIN, Median and Craft cost in the tooltip lines | [Coflnet](https://sky.coflnet.com) |
| GitHub (NotEnoughUpdates repository) | Every item and its recipes, and the pets' heads and skins | Item list, Command suggestions, Pet panel | Nothing but the request | At most once a day, when one of them first needs the list. It stays in `config/lumio/` | Turn off the Item list, Command suggestions and the Pet panel | [Repository](https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO), [GitHub privacy](https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement) |

Links open in your browser only after you click them and confirm the game's "Open link?" prompt; the add-on fetches nothing from them. The item
window's Wiki button and the "Wiki page of the item" key open the item's page on the community [Hypixel SkyBlock Wiki](https://hypixelskyblock.minecraft.wiki/),
through the wiki's search made from the item's name (it goes straight to the page when one matches). "Prices: SkyCofl" opens the item's page on SkyCofl.

### Local connections

The add-on makes none of its own. Prices it remembers for the price line stay in `config/lumio/`.

### Hypixel

The add-on sends nothing to Hypixel of its own except what "Using it on Hypixel" in the [README](README.md) lists.

## Not affiliated

Lumio: Hypixel SkyBlock isn't affiliated with or endorsed by any of these projects or services.
