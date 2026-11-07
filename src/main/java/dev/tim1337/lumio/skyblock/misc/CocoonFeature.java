package dev.tim1337.lumio.skyblock.misc;

import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.MainSettings;
import dev.tim1337.lumio.api.experimental.draw.PanelLook;
import dev.tim1337.lumio.api.experimental.hud.StyledFeature;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.util.Sight;
import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.api.setting.AddonSetting;
import dev.tim1337.lumio.api.setting.Choice;
import dev.tim1337.lumio.api.setting.MultiToggle;
import dev.tim1337.lumio.api.setting.Slider;
import dev.tim1337.lumio.api.setting.Toggle;
import dev.tim1337.lumio.skyblock.SkyBlockFeatures;
import dev.tim1337.lumio.skyblock.data.EquipmentTracker;
import dev.tim1337.lumio.skyblock.data.SkullTextures;
import dev.tim1337.lumio.skyblock.slayer.SlayerBosses;
import dev.tim1337.lumio.skyblock.data.SkyBlock;
import dev.tim1337.lumio.skyblock.data.SkyBlockArea;
import dev.tim1337.lumio.skyblock.world.WorldLabel;
import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A small label at each cocoon in sight: the cocooned mob's name and a bar that runs down until it hatches, with the seconds left. What is a cocoon is strict ({@link CocoonCheck}): an armor
 * stand with exactly the larva's head, where a mob just died, no part of a boss such as a Tarantula's egg sac; the chat line names the mob. The hatch time starts from a known value and follows what is measured. Reads only what the client
 * already gets; new armor stands are looked at when they load, and the few cocoons a few times a second. The idea is SkyHanni's Cocoon Overlay.
 */
public class CocoonFeature extends AddonFeature implements StyledFeature {
	/** How far around a stand others with its head make a group, in blocks. */
	private static final double GROUP_ACROSS = 6.0;
	/** The end of the larva head's texture address, which identifies a cocoon. */
	private static final String COCOON_TEXTURE = "4ceb0ed8fc2272b3d3d820676d52a38e7b2e8da8c687a233e0dabaa16c0e96df";
	private static final int LOOK_TICKS = 40;
	private static final int SIGHT_CHECK_TICKS = 5;
	private static final int MOST_LOOKED_AT = 128;
	private static final int MOST_COCOONS = 16;
	private static final double NANOS = 1_000_000_000.0;
	/** How near a mob's death has to be to a cocoon, and an egg sac's name, in blocks. */
	private static final double DEATH_ACROSS = 4.0;
	private static final double DEATH_UP = 5.0;
	private static final double LABEL_ACROSS = 2.5;
	private static final double LABEL_UP = 4.0;
	private static final int MOST_DEATHS = 32;
	/** The stands of one cocoon stand on one spot: within this distance across, in blocks. */
	private static final double SAME_SPOT = 0.5;
	/** How long the chat's name waits for its cocoon and the other way around, and how long a label stays after its time ran out, in seconds. */
	private static final double NAME_WAIT_SECONDS = 3.0;
	private static final double STAYS_AFTER_SECONDS = 3.0;
	private static final double FADE_SECONDS = 0.3;
	/** The belt's place in the equipment. */
	private static final int BELT = 2;
	/** The label, above the cocoon by default, in blocks, and how far that can be set. */
	private static final float ABOVE = 2.2f;
	private static final float LEAST_ABOVE = 0.5f;
	private static final float MOST_ABOVE = 4f;
	private static final float LEAST_SIZE = 0.5f;
	private static final float MOST_SIZE = 2f;
	// its parts, in its GUI pixels
	private static final float PAD = 6f;
	private static final float TEXT_TOP = 4.5f;
	private static final float LINE = 10f;
	private static final float LEAST_WIDTH = 70f;
	private static final float BAR_HEIGHT = 2f;
	private static final float BAR_GAP = 2.5f;
	private static final float TRACK_OPACITY = 0.14f;
	private static final float SHADOW_OPACITY = 0.6f;
	private static final float SHADOW_OFFSET = 0.75f;
	private static final float SHADOW_REACH = 10f;
	private static final float SOFT_SHADOW_OPACITY = 0.28f;
	private static final String UNNAMED = "Cocoon";
	// the preview: a sample with its time and how much is left, its label's scale in the card, and the cocoon behind it
	private static final int PREVIEW_HEIGHT = 96;
	private static final String SAMPLE_NAME = "Enderman";
	private static final double SAMPLE_SECONDS = 4.2;
	private static final float SAMPLE_LEFT = 0.6f;
	private static final float PREVIEW_SCALE = 1.1f;
	private static final int SAMPLE_WIDTH = 34;
	private static final int SAMPLE_HEIGHT = 26;
	private static final int SAMPLE_COLOR = 0xFFB9A58A;
	private static final int SAMPLE_BAND = 0x33000000;
	private static final int SAMPLE_BOTTOM = 8;

	private static final class Cocoon {
		final ArmorStand stand;
		final double x;
		final double y;
		final double z;
		final long since;
		String name;
		boolean inSight;
		long goneAt;

		Cocoon(ArmorStand stand, long since, String name) {
			this.stand = stand;
			this.x = stand.getX();
			this.y = stand.getY();
			this.z = stand.getZ();
			this.since = since;
			this.name = name;
		}
	}

	private final Choice<CocoonLook.Style> style = add(make().choice("look", Component.literal("Style"), CocoonLook.Style.CARD, CocoonLook.Style::displayName), "Look");
	private final Choice<PanelLook> theme = add(make().choice("panel_theme", Component.literal("Theme"), PanelLook.HUD, PanelLook::displayName, Choice.Style.DROPDOWN), "Look");
	private final Slider size = add(make().slider("size", Component.literal("Size"), 1f, LEAST_SIZE, MOST_SIZE, 2).percent(), "Look");
	private final Slider above = add(make().slider("height", Component.literal("Height"), ABOVE, LEAST_ABOVE, MOST_ABOVE, 1), "Look");
	private final Toggle showName = make().toggle("show_name", Component.literal("Mob name"), true, Toggle.Style.CHECKBOX);
	private final Toggle showTime = make().toggle("show_time", Component.literal("Time left"), true, Toggle.Style.CHECKBOX);
	private final Toggle showBar = make().toggle("show_bar", Component.literal("Bar"), true, Toggle.Style.CHECKBOX);
	private final MultiToggle shows = add(make().multi("shows", Component.literal("Shows"), List.of(showName, showTime, showBar)).asChips(), "Shows");
	private final AddonSetting<Void> previewRow = add(HudTheme.preview("preview", Component.literal("Preview"), PREVIEW_HEIGHT, this::drawPreview), "Look");
	private int shownBefore = CocoonLook.NAME | CocoonLook.TIME | CocoonLook.BAR;
	private final Toggle onlyWhenICan = add(make().toggle("only_when_i_can", Component.literal("Only when I can cocoon"), false), "Shows",
		"While you wear The Primordial or a belt with the Blood Shot reforge");
	private final Cocoons rules = new Cocoons();
	private final Map<Integer, ArmorStand> looking = new HashMap<>();
	private final Map<Integer, Integer> lookingSince = new HashMap<>();
	private final List<Cocoon> cocoons = new ArrayList<>();
	/** Stands with the larva's head that do not count yet, and when they were first seen. */
	private final List<ArmorStand> waiting = new ArrayList<>();
	private final List<Long> waitingSince = new ArrayList<>();
	/** Where and when mobs went away lately: x, y, z and the time. */
	private final ArrayDeque<double[]> deaths = new ArrayDeque<>();
	private final float[] screen = new float[WorldLabel.PLACE_VALUES];
	private final float[] previewPlace = new float[WorldLabel.PLACE_VALUES];
	private String chatName;
	private long chatNameAt;
	private Object level;
	private int tick;

	public CocoonFeature() {
		super("cocoons", "Cocoons", "A small label at a cocoon, with the cocooned mob's name and a bar that runs down until it hatches, while you can see it. It is an estimate: a cocoon can hatch a little earlier or later.", true);
		iconRgb(SystemColors.PURPLE_RGB);
		iconName("bug");
		summary("How long until a cocoon hatches");
	}

	@Override
	public PanelLook look() {
		return theme.get();
	}

	@Override
	public void takeLook(PanelLook look) {
		theme.set(look);
	}

	/** The phase at the boss is the same kind of label: it gets the style and the size too, so the two match. */
	@Override
	public void styleCopied() {
		SkyBlockFeatures.BOSS_LABEL.takeStyle(style.get(), size.get());
	}

	/** Takes the style and the size of the other label, whose style is copied. */
	public void takeStyle(CocoonLook.Style otherStyle, float otherSize) {
		style.set(otherStyle);
		size.set(otherSize);
	}

	/** Keeps at least one part of the label on: switching the last one off does nothing. */
	private void keepOnePartShown() {
		int now = (showName.get() ? CocoonLook.NAME : 0) | (showTime.get() ? CocoonLook.TIME : 0) | (showBar.get() ? CocoonLook.BAR : 0);
		int kept = CocoonLook.kept(shownBefore, now);
		if (kept != now) {
			showName.set((kept & CocoonLook.NAME) != 0);
			showTime.set((kept & CocoonLook.TIME) != 0);
			showBar.set((kept & CocoonLook.BAR) != 0);
		}
		shownBefore = kept;
	}

	public void register() {
		ClientEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (entity instanceof ArmorStand stand && isEnabled() && looking.size() < MOST_LOOKED_AT) {
				looking.put(stand.getId(), stand);
				lookingSince.put(stand.getId(), tick);
			}
		});
		ClientEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
			if (isEnabled() && isMob(entity)) {
				rememberDeath(entity.getX(), entity.getY(), entity.getZ(), System.nanoTime());
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
	}

	private static boolean isMob(Entity entity) {
		return entity instanceof LivingEntity && !(entity instanceof ArmorStand) && !(entity instanceof Player);
	}

	private void rememberDeath(double x, double y, double z, long nanos) {
		if (deaths.size() >= MOST_DEATHS) {
			deaths.removeFirst();
		}
		deaths.addLast(new double[]{x, y, z, nanos});
	}

	/** @return whether a stand counts as a cocoon: the Tarantula's helper does not ask for what is one already */
	public boolean claims(Entity entity) {
		for (Cocoon cocoon : cocoons) {
			if (cocoon.stand == entity) {
				return true;
			}
		}
		return false;
	}

	/** Reads the chat line that names the cocooned mob. */
	public void onMessage(Component message) {
		if (!isEnabled()) {
			return;
		}
		String mob = Cocoons.mobOf(SkyBlockArea.plainLine(message.getString()));
		if (mob == null) {
			return;
		}
		long now = System.nanoTime();
		// the cocoon may be there already
		for (int i = cocoons.size() - 1; i >= 0; i--) {
			Cocoon cocoon = cocoons.get(i);
			if (cocoon.name == null && (now - cocoon.since) / NANOS <= NAME_WAIT_SECONDS) {
				cocoon.name = mob;
				return;
			}
		}
		chatName = mob;
		chatNameAt = now;
	}

	private void tick(Minecraft client) {
		tick++;
		keepOnePartShown();
		if (client.level != level) {
			level = client.level;
			looking.clear();
			lookingSince.clear();
			cocoons.clear();
			waiting.clear();
			waitingSince.clear();
			deaths.clear();
			chatName = null;
		}
		if (!isEnabled() || client.player == null || !SkyBlock.allowed() || Cocoons.inRift(SkyBlockArea.island())) {
			looking.clear();
			lookingSince.clear();
			cocoons.clear();
			waiting.clear();
			waitingSince.clear();
			return;
		}
		lookAtNewStands();
		judgeWaiting(client);
		if (tick % SIGHT_CHECK_TICKS == 0) {
			checkCocoons(client);
		}
	}

	private void lookAtNewStands() {
		Iterator<Map.Entry<Integer, ArmorStand>> it = looking.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Integer, ArmorStand> entry = it.next();
			ArmorStand stand = entry.getValue();
			Integer since = lookingSince.get(entry.getKey());
			if (stand.isRemoved() || since == null || tick - since > LOOK_TICKS) {
				lookingSince.remove(entry.getKey());
				it.remove();
				continue;
			}
			ItemStack head = stand.getItemBySlot(EquipmentSlot.HEAD);
			if (head.isEmpty()) {
				continue;
			}
			lookingSince.remove(entry.getKey());
			it.remove();
			if (SkullTextures.is(head, COCOON_TEXTURE) && !known(stand) && waiting.size() < MOST_COCOONS) {
				waiting.add(stand);
				waitingSince.add(System.nanoTime());
			}
		}
	}

	/** Looks at the stands with the larva's head, for the moment it takes to tell what they are. There are hardly ever any, so what is around them can be looked for. */
	private void judgeWaiting(Minecraft client) {
		long now = System.nanoTime();
		for (int i = waiting.size() - 1; i >= 0; i--) {
			ArmorStand stand = waiting.get(i);
			long since = waitingSince.get(i);
			CocoonCheck.Verdict verdict = stand.isRemoved() || known(stand) ? CocoonCheck.Verdict.NOT : CocoonCheck.judge(sighting(client, stand, now), COCOON_TEXTURE, (now - since) / NANOS);
			if (verdict == CocoonCheck.Verdict.WAIT) {
				continue;
			}
			waiting.remove(i);
			waitingSince.remove(i);
			if (verdict == CocoonCheck.Verdict.COCOON && cocoons.size() < MOST_COCOONS) {
				boolean named = chatName != null && (now - chatNameAt) / NANOS <= NAME_WAIT_SECONDS;
				cocoons.add(new Cocoon(stand, since, named ? chatName : null));
				if (named) {
					chatName = null;
				}
			}
		}
	}

	private CocoonCheck.Sighting sighting(Minecraft client, ArmorStand stand, long now) {
		double fromChat = chatName == null ? CocoonCheck.NEVER : (now - chatNameAt) / NANOS;
		return new CocoonCheck.Sighting(SkullTextures.of(stand.getItemBySlot(EquipmentSlot.HEAD)), SlayerBosses.isEggPart(stand), helperLabelAt(client, stand), fromChat, fromDeath(client, stand, now), sameHeadsAt(client, stand), SlayerBosses.tarantulaHere());
	}

	/** @return how long ago a mob died at a stand's place, in seconds: one that is dying there now just did, and one that went away is remembered */
	private double fromDeath(Minecraft client, ArmorStand stand, long now) {
		for (Entity near : client.level.getEntities(stand, stand.getBoundingBox().inflate(DEATH_ACROSS, DEATH_UP, DEATH_ACROSS), CocoonFeature::isMob)) {
			if (near instanceof LivingEntity mob && mob.isDeadOrDying()) {
				return 0.0;
			}
		}
		double best = CocoonCheck.NEVER;
		for (double[] death : deaths) {
			if (Math.abs(death[0] - stand.getX()) <= DEATH_ACROSS && Math.abs(death[1] - stand.getY()) <= DEATH_UP && Math.abs(death[2] - stand.getZ()) <= DEATH_ACROSS) {
				best = Math.min(best, (now - (long) death[3]) / NANOS);
			}
		}
		return best;
	}

	/** @return how many other stands near a stand wear the larva's head */
	private static int sameHeadsAt(Minecraft client, ArmorStand stand) {
		int count = 0;
		for (Entity near : client.level.getEntities(stand, stand.getBoundingBox().inflate(GROUP_ACROSS), entity -> entity instanceof ArmorStand)) {
			if (COCOON_TEXTURE.equals(SkullTextures.of(((ArmorStand) near).getItemBySlot(EquipmentSlot.HEAD)))) {
				count++;
			}
		}
		return count;
	}

	/** @return whether an egg sac's name or timer stands right at a stand */
	private static boolean helperLabelAt(Minecraft client, ArmorStand stand) {
		for (Entity near : client.level.getEntities(stand, stand.getBoundingBox().inflate(LABEL_ACROSS, LABEL_UP, LABEL_ACROSS), entity -> entity instanceof ArmorStand other && other.hasCustomName())) {
			if (CocoonCheck.isHelperLabel(SkyBlockArea.plainLine(near.getCustomName().getString()))) {
				return true;
			}
		}
		return false;
	}

	private boolean known(ArmorStand stand) {
		for (Cocoon cocoon : cocoons) {
			double dx = cocoon.x - stand.getX();
			double dz = cocoon.z - stand.getZ();
			if (cocoon.goneAt == 0 && dx * dx + dz * dz < SAME_SPOT * SAME_SPOT) {
				return true;
			}
		}
		return false;
	}

	private void checkCocoons(Minecraft client) {
		long now = System.nanoTime();
		Iterator<Cocoon> it = cocoons.iterator();
		while (it.hasNext()) {
			Cocoon cocoon = it.next();
			double age = (now - cocoon.since) / NANOS;
			if (cocoon.goneAt != 0) {
				if ((now - cocoon.goneAt) / NANOS > FADE_SECONDS) {
					it.remove();
				}
			} else if (cocoon.stand.isRemoved()) {
				// the mob hatched: this is how long it took
				rules.measured(age);
				cocoon.goneAt = now;
			} else if (age > rules.hatchSeconds() + STAYS_AFTER_SECONDS || SlayerBosses.isEggPart(cocoon.stand) || helperLabelAt(client, cocoon.stand)) {
				// also when it turns out to be a boss's: its labels can come a moment after it
				it.remove();
			} else {
				cocoon.inSight = Sight.visible(client, cocoon.stand);
			}
		}
	}

	/** @return whether the labels are wanted now: always, or only with a belt that can cocoon */
	private boolean wanted() {
		if (!onlyWhenICan.get()) {
			return true;
		}
		ItemStack belt = EquipmentTracker.item(BELT);
		return belt != null && !belt.isEmpty() && Cocoons.beltCanCocoon(belt.getHoverName().getString());
	}

	public void render(GuiGraphicsExtractor graphics, DeltaTracker tracker) {
		if (cocoons.isEmpty() || !isEnabled() || !wanted() || !SlayerBosses.showsHere()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		long now = System.nanoTime();
		Canvas ui = null;
		for (Cocoon cocoon : cocoons) {
			if (!cocoon.inSight || !WorldLabel.project(client, cocoon.x, cocoon.y + above.get(), cocoon.z, size.get(), screen)) {
				continue;
			}
			if (ui == null) {
				ui = Draw.hudCanvas(graphics, 0xFF000000 | MainSettings.accentRgb(), 0f, true);
			}
			float fade = cocoon.goneAt == 0 ? 1f : (float) Math.max(0.0, 1.0 - (now - cocoon.goneAt) / NANOS / FADE_SECONDS);
			draw(graphics, ui, cocoon, (now - cocoon.since) / NANOS, fade);
		}
	}

	private void draw(GuiGraphicsExtractor graphics, Canvas ui, Cocoon cocoon, double age, float fade) {
		String name = cocoon.name == null ? UNNAMED : cocoon.name;
		double left = rules.secondsLeft(age);
		label(graphics, ui, screen, name, CocoonLook.decimal(left) + " s", rules.left(age), left, fade);
	}

	/** Draws the label on a place, in its style and theme. */
	private void label(GuiGraphicsExtractor graphics, Canvas ui, float[] place, String name, String seconds, float barShare, double secondsLeft, float fade) {
		boolean names = showName.get();
		boolean times = showTime.get();
		boolean bars = showBar.get();
		boolean hasText = names || times;
		CocoonLook.Style chosen = style.get();
		int barColor = 0xFF000000 | CocoonLook.barRgb(MainSettings.accentRgb(), secondsLeft);
		String line = CocoonLook.line(name, seconds, names, times);
		boolean card = chosen == CocoonLook.Style.CARD;
		boolean filled = chosen != CocoonLook.Style.MINIMAL;
		float nameWidth = names ? ui.boldWidthScaled(name, TextScale.SMALL) : 0f;
		float timeWidth = times ? ui.widthScaled(seconds, TextScale.SMALL) : 0f;
		float textWidth = !hasText ? 0f : card ? nameWidth + timeWidth + (names && times ? PAD : 0f) : ui.widthScaled(line, TextScale.SMALL);
		float width = Math.max(card ? LEAST_WIDTH : LEAST_WIDTH / 2f, textWidth + 2f * PAD);
		float barY = TEXT_TOP + (hasText ? LINE : 0f) + (hasText ? 0f : BAR_GAP - TEXT_TOP);
		float height = bars ? barY + BAR_HEIGHT + TEXT_TOP - BAR_GAP : TEXT_TOP + LINE;
		if (filled) {
			graphics.nextStratum();
		}
		HudTheme.withLook(theme.get(), () -> WorldLabel.draw(graphics, place, width, height, fade, () -> {
			if (filled) {
				HudTheme.panel(ui, 0, 0, Math.round(width), Math.round(height), Radius.MD, HudTheme.opacity());
			}
			if (card) {
				if (names) {
					ui.textAtScaledBold(name, PAD, TEXT_TOP, TextScale.SMALL, HudTheme.text());
				}
				if (times) {
					ui.textAtScaled(seconds, width - PAD - timeWidth, TEXT_TOP, TextScale.SMALL, HudTheme.secondary());
				}
			} else if (hasText) {
				float lineX = (width - textWidth) / 2f;
				if (!filled) {
					ui.softLight(width / 2f, TEXT_TOP + LINE / 2f, width / 2f + SHADOW_REACH, LINE + SHADOW_REACH, Draw.withOpacity(0xFF000000, SOFT_SHADOW_OPACITY));
					ui.textAtScaled(line, lineX + SHADOW_OFFSET, TEXT_TOP + SHADOW_OFFSET, TextScale.SMALL, Draw.withOpacity(0xFF000000, SHADOW_OPACITY));
				}
				ui.textAtScaled(line, lineX, TEXT_TOP, TextScale.SMALL, filled ? HudTheme.text() : 0xFFFFFFFF);
			}
			if (bars) {
				float inner = width - 2f * PAD;
				ui.roundedSmooth(PAD, barY, inner, BAR_HEIGHT, BAR_HEIGHT / 2f, Draw.withOpacity(filled ? HudTheme.text() : 0xFFFFFFFF, TRACK_OPACITY));
				ui.roundedSmooth(PAD, barY, Math.max(BAR_HEIGHT, inner * barShare), BAR_HEIGHT, BAR_HEIGHT / 2f, barColor);
			}
		}));
	}

	/** The label as it is set, over a sample cocoon. */
	private void drawPreview(GuiGraphicsExtractor graphics, Canvas ui, int x, int y, int width, int height) {
		int cocoonX = x + (width - SAMPLE_WIDTH) / 2;
		int cocoonY = y + height - SAMPLE_BOTTOM - SAMPLE_HEIGHT;
		ui.rounded(cocoonX, cocoonY, SAMPLE_WIDTH, SAMPLE_HEIGHT, Radius.LG, SAMPLE_COLOR);
		for (int band = 1; band <= 2; band++) {
			ui.fill(cocoonX + 4, cocoonY + band * SAMPLE_HEIGHT / 3, SAMPLE_WIDTH - 8, 2, SAMPLE_BAND);
		}
		previewPlace[WorldLabel.X] = x + width / 2f;
		previewPlace[WorldLabel.Y] = cocoonY - above.get() * 2f;
		previewPlace[WorldLabel.SIZE] = size.get() * PREVIEW_SCALE;
		previewPlace[WorldLabel.FADE] = 1f;
		label(graphics, ui, previewPlace, SAMPLE_NAME, CocoonLook.decimal(SAMPLE_SECONDS) + " s", SAMPLE_LEFT, SAMPLE_SECONDS, 1f);
	}

}
