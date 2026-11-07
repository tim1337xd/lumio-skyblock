package dev.tim1337.lumio.skyblock.slayer;

import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.MainSettings;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.util.Logs;
import dev.tim1337.lumio.api.experimental.util.Sight;
import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.skyblock.data.DropMessage;
import dev.tim1337.lumio.skyblock.data.DropPriceIds;
import dev.tim1337.lumio.skyblock.data.ItemGains;
import dev.tim1337.lumio.skyblock.data.SkyBlock;
import dev.tim1337.lumio.skyblock.data.SkyBlockArea;
import dev.tim1337.lumio.skyblock.world.WorldLabel;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The drops on the ground ({@link DropRecognizer}): they get an outline and a soft glow in their rarity color until they are picked up or gone, and the rare ones their name above them, only while the player sees
 * them. New items are looked at for a while and again whenever their item, the chat or the boss's death changes, because their real name and the chat line arrive at any moment; the few kept ones are checked a
 * few times a second. No entity scan.
 */
final class BossDropGlow {
	/** How long a new item is looked at, in ticks: its real name, the chat line and the boss's death all come after it, in any order. */
	private static final int LOOK_TICKS = 500;
	private static final int SIGHT_CHECK_TICKS = 5;
	private static final int MOST_LOOKED_AT = 96;
	private static final int MOST_KEPT = 48;
	/** The rarities' colors, as the mod's item lists show them, with common as gray. */
	private static final int COMMON_GRAY = 0xAAAAAA;
	private static final int COMMON = 0;
	/** The label above a drop and the glow around it: how high above the item, in blocks, and in GUI pixels. */
	private static final double LABEL_ABOVE = 0.75;
	private static final float GLOW_RADIUS = 11f;
	private static final float GLOW_OPACITY = 0.4f;
	private static final float LABEL_PAD = 5f;
	private static final float LABEL_HEIGHT = 12f;
	private static final float LABEL_OPACITY = 0.85f;
	/** A line without a name color is plain white. */
	private static final int NO_COLOR = -1;

	/** An item that is being looked at: where it appeared, and what was last seen of it. */
	private static final class New {
		final ItemEntity item;
		final int tick;
		final long millis;
		final double x;
		final double y;
		final double z;
		ItemStack seenStack;
		String name = "";
		String id;
		int seenChanges = -1;
		long seenDeath = -1L;
		DropRecognizer.Verdict verdict = DropRecognizer.Verdict.NOT_A_DROP;

		New(ItemEntity item, int tick, long millis) {
			this.item = item;
			this.tick = tick;
			this.millis = millis;
			x = item.getX();
			y = item.getY();
			z = item.getZ();
		}
	}

	private record Kept(ItemEntity item, int rgb, String label) {
	}

	private final MinibossFeature owner;
	private final Map<Integer, New> looking = new LinkedHashMap<>();
	private final Map<Integer, Kept> kept = new LinkedHashMap<>();
	private final DropRecognizer recognizer = new DropRecognizer();
	private final List<Integer> inSight = new ArrayList<>();

	private final float[] screen = new float[WorldLabel.PLACE_VALUES];
	private Object level;
	private int tick;

	BossDropGlow(MinibossFeature owner) {
		this.owner = owner;
	}

	void register() {
		ClientEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (entity instanceof ItemEntity item && owner.dropsOn() && SkyBlock.allowed()) {
				// many items around: the oldest are given up, never the new one
				if (looking.size() >= MOST_LOOKED_AT) {
					Iterator<Integer> oldest = looking.keySet().iterator();
					oldest.next();
					oldest.remove();
				}
				looking.put(item.getId(), new New(item, tick, System.currentTimeMillis()));
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
	}

	void onMessage(Component message) {
		if (!owner.dropsOn()) {
			return;
		}
		DropMessage drop = DropMessage.parse(message.getString());
		if (drop != null) {
			recognizer.announce(drop, System.currentTimeMillis());
		}
	}

	private void tick(Minecraft client) {
		tick++;
		if (client.level != level) {
			level = client.level;
			forget();
		}
		if (!owner.dropsOn() || client.player == null) {
			forget();
			return;
		}
		if (!looking.isEmpty()) {
			look();
		}
		if (tick % SIGHT_CHECK_TICKS == 0) {
			checkSight(client);
		}
	}

	/** Decides about the items again only where something changed: their item, the chat, or the boss's death. */
	private void look() {
		long death = SlayerBosses.deathMillis();
		int changes = recognizer.changes();
		Iterator<Map.Entry<Integer, New>> it = looking.entrySet().iterator();
		while (it.hasNext()) {
			New item = it.next().getValue();
			boolean gone = item.item.isRemoved();
			if (gone || tick - item.tick > LOOK_TICKS) {
				if (owner.dropDebug()) {
					Logs.info("Drop check: \"{}\" ({}): not highlighted, {}{}", item.name, item.id, item.verdict.why, gone ? " (picked up or gone)" : " (no longer looked at)");
				}
				it.remove();
				continue;
			}
			ItemStack stack = item.item.getItem();
			if (stack.isEmpty()) {
				continue;
			}
			boolean newStack = stack != item.seenStack;
			if (newStack) {
				item.seenStack = stack;
				item.name = SkyBlockArea.plainLine(stack.getHoverName().getString());
				item.id = ItemGains.idOf(stack);
			}
			if (!newStack && item.seenChanges == changes && item.seenDeath == death) {
				continue;
			}
			item.seenChanges = changes;
			item.seenDeath = death;
			item.verdict = recognizer.judge(item.name, item.id, item.millis, SlayerBosses.fromDeath(item.x, item.y, item.z), death);
			if (item.verdict.drop) {
				keep(item, stack);
				it.remove();
			}
		}
	}

	private void keep(New item, ItemStack stack) {
		if (kept.size() >= MOST_KEPT) {
			Iterator<Integer> oldest = kept.keySet().iterator();
			oldest.next();
			oldest.remove();
		}
		Component shown = stack.getHoverName();
		int color = colorOf(shown);
		int rarity = color == NO_COLOR ? BossDrops.UNKNOWN_RARITY : DropPriceIds.rarity(0xFF000000 | color);
		kept.put(item.item.getId(), new Kept(item.item, rarity == COMMON || color == NO_COLOR ? COMMON_GRAY : color, BossDrops.labeled(rarity) ? item.name : null));
		if (owner.dropDebug()) {
			Logs.info("Drop check: \"{}\" ({}): highlighted, {}", item.name, item.id, item.verdict.why);
		}
	}

	/** @return the color of a name's first letters as 0xRRGGBB, or none */
	private static int colorOf(Component name) {
		int[] color = {NO_COLOR};
		name.getVisualOrderText().accept((index, style, codePoint) -> {
			if (Character.isWhitespace(codePoint)) {
				return true;
			}
			TextColor text = style.getColor();
			color[0] = text == null ? NO_COLOR : text.getValue();
			return false;
		});
		return color[0];
	}

	private void checkSight(Minecraft client) {
		inSight.clear();
		kept.values().removeIf(drop -> drop.item().isRemoved());
		for (Kept drop : kept.values()) {
			if (Sight.visible(client, drop.item())) {
				inSight.add(drop.item().getId());
			}
		}
	}

	private void forget() {
		looking.clear();
		kept.clear();
		inSight.clear();
		recognizer.forget();
	}

	/** @return whether a drop is outlined now; called per entity and frame */
	boolean glows(int id) {
		return !inSight.isEmpty() && inSight.contains(id);
	}

	/** @return the outline color of a kept drop as 0xRRGGBB, or -1 */
	int color(int id) {
		Kept drop = inSight.isEmpty() ? null : kept.get(id);
		return drop == null ? -1 : drop.rgb();
	}

	/** Draws the soft glow around the drops in sight, and the names of the rare ones. */
	void draw(GuiGraphicsExtractor graphics) {
		if (inSight.isEmpty()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		Canvas ui = null;
		for (int id : inSight) {
			Kept drop = kept.get(id);
			if (drop == null || client.player == null) {
				continue;
			}
			double x = drop.item().getX();
			double y = drop.item().getY();
			double z = drop.item().getZ();
			if (!WorldLabel.project(client, x, y + LABEL_ABOVE / 2.0, z, screen)) {
				continue;
			}
			if (ui == null) {
				ui = Draw.hudCanvas(graphics, 0xFF000000 | MainSettings.accentRgb(), 0f, true);
			}
			Canvas canvas = ui;
			float size = screen[WorldLabel.SIZE];
			float light = GLOW_OPACITY * screen[WorldLabel.FADE];
			ui.softLight(screen[WorldLabel.X], screen[WorldLabel.Y], GLOW_RADIUS * size, GLOW_RADIUS * size, Draw.withOpacity(0xFF000000 | drop.rgb(), light));
			String label = drop.label();
			if (label != null && WorldLabel.project(client, x, y + LABEL_ABOVE, z, screen)) {
				float width = ui.boldWidthScaled(label, TextScale.SMALL) + 2f * LABEL_PAD;
				WorldLabel.draw(graphics, screen, width, LABEL_HEIGHT, 1f, () -> {
					canvas.roundedSmooth(0f, 0f, width, LABEL_HEIGHT, Radius.pill(LABEL_HEIGHT), Draw.withOpacity(0xFF0A0A10, LABEL_OPACITY));
					canvas.textAtScaledBold(label, LABEL_PAD, 3f, TextScale.SMALL, HudTheme.readable(0xFF000000 | drop.rgb()));
				});
			}
		}
	}
}
