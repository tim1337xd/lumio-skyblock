package dev.tim1337.lumio.skyblock.mining;

import dev.tim1337.lumio.api.LumioApi;
import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.BlockStyle;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.draw.WorldDraw;
import dev.tim1337.lumio.api.experimental.hud.HudEditor;
import dev.tim1337.lumio.api.feature.AddonPanel;
import dev.tim1337.lumio.api.feature.SettingGroup;
import dev.tim1337.lumio.api.setting.Choice;
import dev.tim1337.lumio.api.setting.ColorChoice;
import dev.tim1337.lumio.api.setting.Slider;
import dev.tim1337.lumio.api.setting.Toggle;

import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;
import dev.tim1337.lumio.api.experimental.draw.PanelKit;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.skyblock.hud.PanelMoment;
import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.api.experimental.ui.Toasts;
import dev.tim1337.lumio.skyblock.SkyBlockSimulation;
import dev.tim1337.lumio.skyblock.data.Activity;
import dev.tim1337.lumio.skyblock.data.ItemGains;
import dev.tim1337.lumio.skyblock.data.ProfitActivity;
import dev.tim1337.lumio.skyblock.data.SkyBlock;
import dev.tim1337.lumio.skyblock.data.SkyBlockArea;
import dev.tim1337.lumio.skyblock.hud.PanelLooks;

import com.google.gson.JsonObject;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Hypixel SkyBlock pickaxe ability (Mining Speed Boost, Pickobulus and others): while active, while cooling down, and when ready again. It is
 * read from three sources, most reliable first: the "Pickaxe Ability" player list widget, the chat messages on use, expiry and readiness, and the
 * cooldown in the held pickaxe's lore. Displayed as a slim bar (as wide as a hotbar slot), a horizontal bar, or a panel with name and seconds.
 */
public class MiningAbilityHudFeature extends AddonPanel {
	private static final Pattern USED = Pattern.compile("You used your (.+?) Pickaxe Ability!");
	private static final Pattern READY = Pattern.compile("^(.+?) is now available!");
	private static final Pattern EXPIRED = Pattern.compile("Your (.+?) has expired!");
	/** A widget line: the ability name and "Available", or the remaining time as "1m 5s", "65s" or "1:05". */
	private static final Pattern WIDGET_LINE = Pattern.compile("^(.+?):\\s*(Available|AVAILABLE|READY|Ready|(?:(\\d+)\\s*m)?\\s*(?:(\\d+)\\s*s)?|(\\d+):(\\d{2}))\\b.*$");
	private static final Pattern LORE_COOLDOWN = Pattern.compile("Cooldown:\\s*(\\d+)s");
	private static final Pattern LORE_ABILITY = Pattern.compile("^Ability:\\s*(.+?)\\s+(?:RIGHT CLICK|Right Click)", Pattern.CASE_INSENSITIVE);
	/** The ability's active phase in the lore: "... for 20s". */
	private static final Pattern LORE_DURATION = Pattern.compile("\\bfor\\s+(\\d+)s\\b");
	/** After a use the player list still shows the time from before for a moment: it is not read then. */
	private static final long TAB_LATE_MILLIS = 2000L;
	/** A share of white in the bar's color while the ability is active, so draining and filling look apart. */
	private static final float ACTIVE_LIGHTER = 0.4f;

	private final Choice<PanelLooks.Abilities> look = add(make().choice("look", Component.literal("Style"), PanelLooks.Abilities.RINGS,
		PanelLooks.Abilities::displayName, Choice.Style.SEGMENTED), "General");
	private final Toggle showName = add(make().toggle("show_name", Component.literal("Ability name"), true), "General");
	private final Toggle showIcon = add(make().toggle("show_icon", Component.literal("Icon"), true), "General");
	private final ColorChoice barColor = add(make().color("bar_color", Component.literal("Cooldown color"), SystemColors.ORANGE_RGB).withOpacity(1f).inRow("Colors"), "General");
	private final ColorChoice readyColor = add(make().color("ready_color", Component.literal("Ready color"), SystemColors.GREEN_RGB).withOpacity(1f).inRow("Colors"), "General");
	private final Toggle showSeconds = add(make().toggle("show_seconds", Component.literal("Time left"), true), "General");

	private final Toggle readyNotice = add(make().toggle("ready_notice", Component.literal("Ready notification"), true), "Ready alert");
	private final Toggle readySound = add(make().toggle("ready_sound", Component.literal("Sound"), true), "Ready alert");
	private final Slider readyVolume = add(make().slider("ready_volume", Component.literal("Volume"), 0.8f, 0.1f, 1f, 2).percent(), "Ready alert");
	{
		readySound.under(readyNotice);
		readyVolume.under(readySound);
	}

	private final Toggle pickobulus = add(make().toggle("pickobulus", Component.literal("Pickobulus preview"), false), "Pickobulus",
		"Shows the blocks its blast breaks where you aim");
	private final Toggle pickobulusReady = add(make().toggle("pickobulus_ready", Component.literal("Only when ready"), false), "Pickobulus");
	private final ColorChoice pickobulusColor = add(make().color("pickobulus_color", Component.literal("Color"), SystemColors.BLUE_RGB).followsMenu(), "Pickobulus");
	private final Toggle pickobulusFull = add(make().toggle("pickobulus_full", Component.literal("Full blocks"), false), "Pickobulus",
		"Off: only what is there, such as a slab or snow");
	{
		pickobulusFull.under(pickobulus);
		pickobulusReady.under(pickobulus);
		pickobulusColor.under(pickobulus);
	}

	/** How far Pickobulus is thrown before it lands. */
	private static final double THROW_RANGE = 20.0;
	/** Height above the eyes at which the pickaxe leaves the hand, as measured by the Skyblocker developers. */
	private static final double THROW_HEIGHT = 0.53625;

	private final Spring shownAmount = Spring.bouncy(Spring.GENTLE);
	private final Spring fill = Spring.smooth(Spring.DEFAULT);
	private String ability = "";
	/** Whether this use's ready notice was shown. */
	private boolean noticed;
	/** When the ability is ready again, or 0 if it is ready. */
	private long readyAt;

	/** @return whether the pickaxe ability can be used right now, as far as known */
	public boolean abilityReady() {
		return readyAt == 0 || System.currentTimeMillis() >= readyAt;
	}
	/** Cooldown duration: measured once it was used and became ready again; until then the default with Heart of the Mountain perks. */
	private long cooldownMillis = 60_000L;
	/** The cooldown of the running use, taken when it started: what the bar fills over. */
	private long totalMillis = 60_000L;
	/** How long the ability is active after a use, from the lore; 0 when unknown. */
	private long activeMillis;
	/** Whether the held item's lore states the cooldown (it includes the player's reductions). */
	private boolean loreCooldown;
	/** Whether this use's whole cooldown was taken from the player list already. */
	private boolean totalFromList;
	/** Whether the ability is currently active (between use and expiry). */
	private boolean active;
	private long activeSince;
	private boolean wasReady = true;
	private boolean knownFromWidget;
	/** Whether the cooldown was measured, from use to the ready message, or taken from the widget. */
	private boolean learnedCooldown;
	/** When the ability was used, for measuring the cooldown; 0 if not. */
	private long usedAt;

	public MiningAbilityHudFeature() {
		super("mining_ability", "Abilities", "The pickaxe ability: a ring that fills as its cooldown runs and turns green when it is ready, or one simple row.", true,
			Anchor.RIGHT, true, 12f, 60f);
		iconRgb(SystemColors.ORANGE_RGB);
		iconName("pickaxe");
		summary("The cooldown of your pickaxe ability");
		offerNoBackground();
		offerCardSize();
		offerModes(look);
		offerHeader();
	}

	private boolean allowedHere() {
		return SkyBlock.allowed();
	}

	private static boolean holdingPickaxe(Minecraft client) {
		if (client.player == null) {
			return false;
		}
		ItemStack held = client.player.getMainHandItem();
		String id = ItemGains.idOf(held);
		String text = (id != null ? id : held.getHoverName().getString()).toUpperCase(Locale.ROOT);
		return text.contains("PICKAXE") || text.contains("DRILL") || text.contains("GAUNTLET") || text.contains("PICKONIMBUS");
	}

	/** Handles a chat message: the ability was used, expired, or is ready. */
	public void onMessage(Component message) {
		String text = message.getString().strip();
		long now = System.currentTimeMillis();
		Matcher matcher = USED.matcher(text);
		if (matcher.find()) {
			// the held item's own cooldown, read now, as the use starts the timer at once
			readHeld(Minecraft.getInstance());
			ability = matcher.group(1).strip();
			active = true;
			activeSince = now;
			totalMillis = cooldownMillis;
			readyAt = now + totalMillis;
			wasReady = false;
			usedAt = now;
			totalFromList = false;
			noticed = false;
			ProfitActivity.pickaxeAbilityUsed();
			return;
		}
		matcher = EXPIRED.matcher(text);
		if (matcher.find() && (ability.isEmpty() || matcher.group(1).strip().equalsIgnoreCase(ability))) {
			active = false;
			return;
		}
		matcher = READY.matcher(text);
		if (matcher.find() && (!ability.isEmpty() && matcher.group(1).strip().equalsIgnoreCase(ability) || text.contains("Mining Speed Boost") || text.contains("Pickobulus"))) {
			ability = matcher.group(1).strip();
			// the time from use to this message is the cooldown including the player's perks: remember it for next time
			if (usedAt > 0 && now - usedAt > 5_000L && now - usedAt < 400_000L) {
				cooldownMillis = now - usedAt;
				learnedCooldown = true;
				LumioApi.saveSoon();
			}
			usedAt = 0;
			readyAt = 0;
			active = false;
		}
	}

	/** The lore {@link #readHeld} parsed last. */
	private ItemLore heldLoreRead;

	/** Reads the cooldown and ability name from the held pickaxe's lore. */
	private void readHeld(Minecraft client) {
		if (client.player == null) {
			return;
		}
		ItemLore lore = client.player.getMainHandItem().get(DataComponents.LORE);
		// same lore, same result: only parse when the held item changes (this runs every frame, and a lore has dozens of lines)
		if (lore == null || lore == heldLoreRead) {
			return;
		}
		heldLoreRead = lore;
		boolean inAbility = false;
		long duration = 0L;
		for (Component line : lore.lines()) {
			String text = line.getString().strip();
			Matcher name = LORE_ABILITY.matcher(text);
			if (name.find()) {
				inAbility = true;
				if (ability.isEmpty()) {
					ability = name.group(1).strip();
				}
				continue;
			}
			if (!inAbility) {
				continue;
			}
			Matcher lasts = LORE_DURATION.matcher(text);
			if (duration == 0L && lasts.find() && lasts.group(1).length() <= 3) {
				duration = Long.parseLong(lasts.group(1)) * 1000L;
			}
			Matcher cooldown = LORE_COOLDOWN.matcher(text);
			if (cooldown.find() && cooldown.group(1).length() <= 4) {
				cooldownMillis = Long.parseLong(cooldown.group(1)) * 1000L;
				activeMillis = duration < cooldownMillis ? duration : 0L;
				loreCooldown = true;
				return;
			}
		}
	}

	/** The player list read last; the same list is not parsed again (it refreshes once per second, while this runs every frame). */
	private List<String> readTab;

	/** Parses the player list widget, which stays correct after cooldown reductions: "Pickaxe Ability:" followed by "Name: 1m 5s" or "Name: Available". */
	private void readWidget() {
		List<String> tab = SkyBlockArea.tab();
		long now = System.currentTimeMillis();
		// right after a use, and while the ability is active, the list still shows a time from before (such as a few seconds): it is read only
		// after that, to correct the running timer, and never starts one; the list is not marked read meanwhile
		if (tab == readTab || usedAt > 0 && (now - usedAt < TAB_LATE_MILLIS || inActivePhase(now))) {
			return;
		}
		readTab = tab;
		boolean inBox = false;
		for (String line : tab) {
			String plain = line.strip();
			if (plain.toLowerCase(Locale.ROOT).startsWith("pickaxe ability")) {
				inBox = true;
				continue;
			}
			if (!inBox) {
				continue;
			}
			Matcher matcher = WIDGET_LINE.matcher(plain);
			if (matcher.find()) {
				knownFromWidget = true;
				ability = matcher.group(1).strip();
				String status = matcher.group(2).strip();
				if (status.equalsIgnoreCase("Available") || status.equalsIgnoreCase("Ready")) {
					// right after use the list still shows it active for a few seconds: keep the counted cooldown
					if (now - usedAt > 8000L) {
						readyAt = 0;
					}
				} else if (matcher.group(3) != null || matcher.group(4) != null || matcher.group(5) != null) {
					long minutes = number(matcher.group(5) != null ? matcher.group(5) : matcher.group(3));
					long seconds = number(matcher.group(6) != null ? matcher.group(6) : matcher.group(4));
					long left = (minutes * 60 + seconds) * 1000L;
					// The list only refreshes every few seconds, so it always shows slightly more than what is left: keep the counted time, and only adopt
					// the list's value when there is none, or when it is clearly lower (cooldown reduced). It never raises a running timer (a new use comes with
					// its own message), and a cooldown counted down to zero is not restarted by the few seconds the list still shows.
					boolean known = readyAt != 0;
					long counted = known ? readyAt - now : -1;
					boolean ranOut = known && counted <= 0 && left <= 8000L;
					if (!ranOut && (!known || left < counted - 1500)) {
						readyAt = now + left;
					}
					// a pickaxe that does not state its cooldown: the first time the list gives after a use, with the time since, is the whole
					// cooldown, so the bar is full exactly when it is ready
					if (!loreCooldown && !totalFromList && left > 0 && usedAt > 0) {
						totalFromList = true;
						cooldownMillis = Math.max(1000L, left + (now - usedAt));
						totalMillis = cooldownMillis;
						learnedCooldown = true;
					}
					// a correction never leaves the bar's whole shorter than what is left
					totalMillis = Math.max(totalMillis, readyAt - now);
				}
			}
			return;
		}
	}

	private boolean wanted(Minecraft client) {
		// while changing worlds (entering a mineshaft, warping) the location is briefly unknown: keep the previous visibility
		if (ProfitActivity.changingWorld()) {
			return wasWanted;
		}
		return wasWanted = wantedNow(client);
	}

	/** The last visibility, kept while changing worlds. */
	private boolean wasWanted;

	private boolean wantedNow(Minecraft client) {
		if (!allowedHere()) {
			return false;
		}
		// auto-detected: while mining and briefly after; a held pickaxe only counts in mining areas, never in the Garden
		return !SkyBlockArea.inGarden() && (ProfitActivity.MINING.within(10_000L)
			|| holdingPickaxe(client) && (SkyBlockArea.inMines() || SkyBlockArea.inDwarvenMines()));
	}

	/** @return an ARGB color with a share of white in it, its alpha kept */
	private static int lighter(int argb) {
		int r = argb >> 16 & 0xFF;
		int g = argb >> 8 & 0xFF;
		int b = argb & 0xFF;
		r += Math.round((255 - r) * ACTIVE_LIGHTER);
		g += Math.round((255 - g) * ACTIVE_LIGHTER);
		b += Math.round((255 - b) * ACTIVE_LIGHTER);
		return argb & 0xFF000000 | r << 16 | g << 8 | b;
	}

	/** @return whether the ability is in its active phase now: from the use for as long as its lore says, or until it expires when that is unknown */
	private boolean inActivePhase(long now) {
		return active && (activeMillis <= 0L || now < activeSince + activeMillis);
	}

	/** @return a number from the player list, bounded: a long one is not a time */
	private static long number(String digits) {
		return digits == null || digits.length() > 4 ? 0L : Long.parseLong(digits);
	}

	/** @return how full the bar is: while active it drains over the active phase, then it fills over the whole cooldown of this use */
	private float fraction(long now) {
		if (readyAt == 0 || now >= readyAt) {
			return 1f;
		}
		if (activeMillis > 0L && inActivePhase(now)) {
			return Mth.clamp(1f - (now - activeSince) / (float) activeMillis, 0f, 1f);
		}
		return Mth.clamp(1f - (readyAt - now) / (float) Math.max(1000L, totalMillis), 0f, 1f);
	}

	@Override
	public void prepare(Minecraft client) {
		if (allowedHere() && client.level != null) {
			readWidget();
			readHeld(client);
		}
		long now = System.currentTimeMillis();
		boolean editing = HudEditor.isOpen();
		// in the editor it only counts as shown while it is simulated; asked for in the list, the main mod draws it anyway
		shownAmount.follow(simulated() || wanted(client) ? 1f : 0f);
		boolean ready = readyAt == 0 || now >= readyAt;
		if (ready && !wasReady) {
			readyMoment.start();
			// once per use: the counted cooldown and the server's message can both bring it back to ready, a moment apart
			if (readyNotice.get() && allowedHere() && !noticed) {
				noticed = true;
				Toasts.info("Pickaxe ability", (ability.isEmpty() ? "Your pickaxe ability" : ability) + " is ready");
				if (readySound.get()) {
					client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.6f, readyVolume.get()));
					client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), 1.2f, readyVolume.get() * 0.7f));
				}
			}
		}
		wasReady = ready;
		// the ability lasts at most 20 seconds: an expiry message may have been missed
		if (active && now - activeSince > 25_000L) {
			active = false;
		}
		// a simulated mining activity in the editor shows the ability ready or on cooldown, as chosen
		Activity simulated = SkyBlockSimulation.active() ? SkyBlockSimulation.activity() : null;
		if (simulated != null && simulated.isMining()) {
			fill.follow(SkyBlockSimulation.option() == Activity.ABILITY_READY ? 1f : SIMULATED_COOLDOWN);
		} else {
			fill.follow(editing && readyAt == 0 ? 0.62f : fraction(now));
		}
	}

	/** How full the bar is for a simulated ability on cooldown in the HUD editor. */
	private static final float SIMULATED_COOLDOWN = 0.35f;
	/** The seconds left of a simulated ability on cooldown in the HUD editor. */
	private static final long SIMULATED_SECONDS = 12L;

	@Override
	public boolean shown(Minecraft client) {
		return shownAmount.value() > 0.01f;
	}

	/** The ability's ring, the plate in it, the icon on the plate, and the gap to the text beside it. */
	private static final int RING = 33;
	private static final float RING_THICKNESS = 2f;
	private static final float PLATE = 24f;
	private static final int ICON = 11;
	private static final int BESIDE = 7;
	/** The state's text size beside the ring, and the seconds' size in the ring. */
	private static final float STATE = 1.05f;
	private static final float SECONDS = 1.05f;
	/** The ready moment: the ring closes, the icon pops to 108 % and back, and one soft green pulse goes out. */
	private static final long READY_MILLIS = 600L;
	private static final float POP_PEAK = 120f;
	private static final float POP_END = 260f;
	private static final float POP = 0.08f;
	private static final float PULSE_START = 100f;
	private static final float PULSE_REACH = 14f;
	private final PanelMoment readyMoment = new PanelMoment(READY_MILLIS);
	/** The seconds shown in the ring, made again only when they change. */
	private long shownSeconds = -1;
	private String secondsText = "";

	private boolean simple() {
		return look.get() == PanelLooks.Abilities.SIMPLE;
	}

	/** The width the content wanted when it was last drawn, as the panel is sized without a canvas; 0 before that. */
	private int contentWidth;

	/** @return the width the player set for the panel, or 0 while it is the usual one, when it grows to its content */
	private int setWidth() {
		return cardWidth() == USUAL_CARD_WIDTH ? 0 : cardWidth();
	}

	@Override
	public int panelWidth() {
		return contentWidth > 0 ? contentWidth : cardWidth();
	}

	@Override
	public int panelHeight() {
		return simple() ? PanelKit.SIMPLE_ROW : contentTop() + RING + PanelKit.BOTTOM;
	}

	@Override
	public void drawPanel(Canvas ui, GuiGraphicsExtractor graphics, Minecraft client) {
		float amount = HudEditor.isOpen() ? 1f : Mth.clamp(shownAmount.value(), 0f, 1f);
		float alphaBefore = Draw.alpha();
		Draw.setAlpha(alphaBefore * amount);
		try {
			long now = System.currentTimeMillis();
			float value = Mth.clamp(fill.value(), 0f, 1f);
			Activity simulated = SkyBlockSimulation.active() ? SkyBlockSimulation.activity() : null;
			boolean ready = simulated != null && simulated.isMining() ? SkyBlockSimulation.option() == Activity.ABILITY_READY : readyAt == 0 || now >= readyAt;
			// while active the bar drains over the active phase in a lighter shade, and the seconds are those of the active phase
			boolean draining = !ready && simulated == null && activeMillis > 0L && inActivePhase(now);
			long until = draining ? activeSince + activeMillis : readyAt;
			long left = ready ? 0 : simulated != null ? SIMULATED_SECONDS : (long) Math.ceil((until - now) / 1000.0);
			if (left != shownSeconds) {
				shownSeconds = left;
				secondsText = left + "s";
			}
			String name = ability.isEmpty() ? "Pickaxe ability" : ability;
			int color = ready ? readyColor.argb() : draining ? lighter(barColor.argb()) : barColor.argb();
			if (simple()) {
				drawSimple(ui, name, ready, value, color);
				return;
			}
			// the name is never cut where the panel may grow; with a width of the player's it shrinks a little first
			float nameWidth = ui.boldWidthScaled(name, TextScale.BODY);
			float nameX = PanelKit.PAD + RING + BESIDE;
			int needed = (int) Math.ceil(nameX + nameWidth + PanelKit.PAD);
			contentWidth = setWidth() > 0 || !showName.get() ? cardWidth() : Math.max(cardWidth(), needed);
			int width = contentWidth;
			drawGlass(ui, width, panelHeight(), PanelKit.RADIUS);
			familyHeader(ui, width, "pickaxe", PanelKit.TILE_ABILITY, "Abilities", active ? "Active" : null, PanelKit.GREEN_TEXT, active);
			drawRing(ui, graphics, ready, value, color);
			float x = PanelKit.PAD + RING + BESIDE;
			float middle = contentTop() + RING / 2f;
			float before = Draw.alpha();
			boolean editing = HudEditor.isOpen();
			if (showName.get() || editing) {
				Draw.setAlpha(before * HudEditor.partAlpha(showName.get()));
				float room = width - PanelKit.PAD - x;
				float scale = nameWidth > room ? Math.max(AbilityRow.LEAST_SCALE, room / nameWidth) : 1f;
				ui.textAtScaledBold(ui.fit(name, Math.round(room / scale)), x, middle - 9f + (1f - scale) * NAME_MIDDLE, TextScale.BODY * scale, HudTheme.text());
				Draw.setAlpha(before);
				part("Ability name", showName, x, middle - 10f, width - PanelKit.PAD - x, 10f);
			}
			float stateY = showName.get() || editing ? middle + 2f : middle - 3.5f;
			if (ready) {
				ui.textAtScaledBold("Ready", x, stateY, STATE, PanelKit.readable(PanelKit.GREEN_TEXT));
			} else {
				ui.textAtScaled(active ? "Active" : "Cooldown", x, stateY + 0.5f, PanelKit.META, HudTheme.secondary());
			}
		} finally {
			Draw.setAlpha(alphaBefore);
		}
	}

	/** The simple row: the icon and the name as they are asked for, the bar, and the status right-aligned in a slot of its own, which does not move when it changes. */
	private void drawSimple(Canvas ui, String name, boolean ready, float value, int color) {
		float digit = ui.boldWidthScaled("0", PanelKit.META);
		float slot = Math.max(ui.boldWidthScaled(READY_TEXT, PanelKit.META), STATUS_DIGITS * digit + ui.boldWidthScaled(SECONDS_UNIT, PanelKit.META));
		boolean named = showName.get();
		AbilityRow row = AbilityRow.of(named ? ui.boldWidthScaled(name, PanelKit.META) : 0f, slot, showIcon.get(), setWidth());
		contentWidth = row.width();
		drawGlass(ui, row.width(), PanelKit.SIMPLE_ROW, PanelKit.SIMPLE_RADIUS);
		float textY = PanelKit.SIMPLE_ROW / 2f - TEXT_MIDDLE;
		if (showIcon.get()) {
			int[] tile = ready ? PanelKit.TILE_ABILITY : PanelKit.TILE_COMMISSIONS;
			int tileY = (PanelKit.SIMPLE_ROW - AbilityRow.TILE) / 2;
			int tileX = Math.round(AbilityRow.TILE_LEFT);
			ui.roundedGradient(tileX, tileY, AbilityRow.TILE, AbilityRow.TILE, TILE_RADIUS, tile[0], tile[1]);
			ui.icon("pickaxe", tileX + TILE_INSET, tileY + TILE_INSET, AbilityRow.TILE - 2 * TILE_INSET, 0xFFFFFFFF);
		}
		if (named) {
			float scale = PanelKit.META * row.nameScale();
			String shown = row.cut() ? ui.fit(name, Math.round(row.nameRoom() / scale)) : name;
			ui.textAtScaledBold(shown, row.nameX(), textY + (1f - row.nameScale()) * TEXT_MIDDLE, scale, HudTheme.text());
		}
		if (row.barWidth() > LEAST_BAR_DRAWN) {
			PanelKit.bar(ui, row.barX(), PanelKit.SIMPLE_ROW / 2f - PanelKit.BAR / 2f, row.barWidth(), PanelKit.BAR, ready ? 1f : value, color);
		}
		if (ready) {
			ui.textAtScaledBold(READY_TEXT, row.statusEnd() - ui.boldWidthScaled(READY_TEXT, PanelKit.META), textY, PanelKit.META, PanelKit.readable(PanelKit.GREEN_TEXT));
		} else if (showSeconds.get()) {
			drawTabular(ui, secondsText, row.statusEnd(), textY, digit);
		}
	}

	/** Draws a text that ends at a place with its digits in equal cells, so that it does not jitter as they change. */
	private static void drawTabular(Canvas ui, String text, float end, float y, float digit) {
		float x = end;
		for (int i = text.length() - 1; i >= 0; i--) {
			char letter = text.charAt(i);
			String one = Character.isDigit(letter) ? DIGITS[letter - '0'] : String.valueOf(letter);
			float width = ui.boldWidthScaled(one, PanelKit.META);
			float cell = Character.isDigit(letter) ? digit : width;
			x -= cell;
			ui.textAtScaledBold(one, x + (cell - width) / 2f, y, PanelKit.META, 0xFFFFFFFF);
		}
	}

	/** What a panel is wide while its width was not touched. */
	private static final int USUAL_CARD_WIDTH = 200;
	private static final String READY_TEXT = "Ready";
	private static final String SECONDS_UNIT = "s";
	private static final String[] DIGITS = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9"};
	/** How many digits the status makes room for, and what a bar is too small to draw below. */
	private static final int STATUS_DIGITS = 3;
	private static final float LEAST_BAR_DRAWN = 6f;
	private static final float TEXT_MIDDLE = 3.2f;
	private static final float NAME_MIDDLE = 4.5f;
	private static final float TILE_RADIUS = 3f;
	private static final int TILE_INSET = 2;

	/** Plays the ready moment once, for the HUD editor's simulation of the moments. */
	public void playReady() {
		readyMoment.start();
	}

	/** Draws the ring: green and full with the icon when ready; otherwise filling as the cooldown runs, with the seconds left in the middle. */
	private void drawRing(Canvas ui, GuiGraphicsExtractor graphics, boolean ready, float value, int color) {
		float centerX = PanelKit.PAD + RING / 2f;
		float centerY = contentTop() + RING / 2f;
		float millis = readyMoment.millis();
		PanelKit.ring(graphics, ui, centerX, centerY, RING, RING_THICKNESS, ready ? 1f : value, color, ready ? 0.7f : 0f);
		if (millis >= 0f) {
			// one soft pulse ring going out and fading
			float pulse = PanelMoment.stage(millis, PULSE_START, READY_MILLIS);
			ui.ringAt(centerX, centerY, RING + PULSE_REACH * pulse, 1.5f, Draw.withOpacity(color, 0.5f * (1f - pulse)));
		}
		if (!ready) {
			if (showSeconds.get() || HudEditor.isOpen()) {
				float before = Draw.alpha();
				Draw.setAlpha(before * HudEditor.partAlpha(showSeconds.get()));
				float width = ui.boldWidthScaled(secondsText, SECONDS);
				ui.textAtScaledBold(secondsText, centerX - width / 2f, centerY - 3.6f * SECONDS, SECONDS, HudTheme.text());
				Draw.setAlpha(before);
				part("Time left", showSeconds, centerX - RING / 2f, centerY - RING / 2f, RING, RING);
			}
			return;
		}
		ui.discAt(centerX, centerY, PLATE, Draw.withOpacity(Draw.mix(color, 0xFF000000, 0.72f), 0.95f));
		// the icon pops to 108 % and back as the ring closes
		float pop = millis < 0f ? 0f : millis < POP_PEAK ? PanelMoment.stage(millis, 0f, POP_PEAK) : 1f - PanelMoment.stage(millis, POP_PEAK, POP_END);
		float size = ICON * (1f + POP * pop);
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(centerX - size / 2f, centerY - size / 2f);
		pose.scale(size / ICON, size / ICON);
		ui.icon("pickaxe", 0, 0, ICON, Draw.mix(color, 0xFFFFFFFF, 0.55f));
		pose.popMatrix();
	}

	@Override
	public JsonObject save() {
		JsonObject json = super.save();
		if (learnedCooldown) {
			json.addProperty("learned_cooldown_ms", cooldownMillis);
		}
		return json;
	}

	@Override
	public void load(JsonObject json) {
		super.load(json);
		if (json.has("learned_cooldown_ms")) {
			try {
				cooldownMillis = Math.max(1000L, json.get("learned_cooldown_ms").getAsLong());
				learnedCooldown = true;
			} catch (RuntimeException ignored) {
			}
		}
	}

	// ------------------------------------------------------------------------------------------------ Pickobulus

	public void registerPickobulus() {
		LevelRenderEvents.COLLECT_SUBMITS.register(this::drawPickobulus);
	}

	/** @return whether Pickobulus is the held pickaxe's ability and should be shown now */
	private boolean pickobulusHeld(Minecraft client) {
		if (!holdingPickaxe(client)) {
			return false;
		}
		// the selected ability: from the widget or chat, otherwise from the pickaxe
		boolean chosen = ability.toLowerCase(Locale.ROOT).contains("pickobulus");
		if (!chosen) {
			// the pickaxe lore, re-parsed only when the held item changes, since this runs every frame
			ItemLore lore = client.player.getMainHandItem().get(DataComponents.LORE);
			if (lore != loreRead) {
				loreRead = lore;
				loreSaysPickobulus = false;
				if (lore != null) {
					for (Component line : lore.lines()) {
						if (line.getString().contains("Pickobulus")) {
							loreSaysPickobulus = true;
							break;
						}
					}
				}
			}
			chosen = loreSaysPickobulus;
		}
		return chosen && (!pickobulusReady.get() || abilityReady());
	}

	/** The held item lore parsed last, and whether it names Pickobulus. */
	private ItemLore loreRead;
	private boolean loreSaysPickobulus;

	/** Which blocks the blast breaks depends on the area. */
	private enum BlastArea {
		/** Dwarven Mines, Gold Mine and Deep Caverns: their ores, stone and glass, which turn into bedrock. */
		ORES,
		/** Crystal Hollows: every block in range. */
		HOLLOWS,
		/** Glacite Tunnels and Great Glacite Lake: ice, gemstones and the tunnel ores. */
		TUNNELS,
		/** A Glacite Mineshaft: every block in range. */
		MINESHAFT
	}

	private static BlastArea blastArea() {
		String island = SkyBlockArea.island().toLowerCase(Locale.ROOT);
		String zone = SkyBlockArea.zone().toLowerCase(Locale.ROOT);
		if (island.contains("mineshaft")) {
			return BlastArea.MINESHAFT;
		}
		if (zone.contains("glacite") || zone.contains("base camp") || zone.contains("fossil") || zone.contains("tunnels")) {
			return BlastArea.TUNNELS;
		}
		if (island.contains("crystal hollows")) {
			return BlastArea.HOLLOWS;
		}
		if (island.contains("dwarven mines") || island.contains("gold mine") || island.contains("deep caverns")) {
			return BlastArea.ORES;
		}
		return null;
	}

	/** Blocks the blast breaks in the Dwarven Mines, Gold Mine and Deep Caverns, besides stained glass. */
	private static final Set<Block> ORE_BLOCKS = Set.of(
		Blocks.STONE, Blocks.COBBLESTONE, Blocks.POLISHED_DIORITE, Blocks.PRISMARINE, Blocks.PRISMARINE_BRICKS, Blocks.DARK_PRISMARINE, Blocks.DYED_TERRACOTTA.pick(DyeColor.CYAN),
		Blocks.WOOL.pick(DyeColor.LIGHT_BLUE), Blocks.WOOL.pick(DyeColor.GRAY), Blocks.LAPIS_BLOCK, Blocks.GOLD_BLOCK, Blocks.IRON_BLOCK, Blocks.DIAMOND_BLOCK, Blocks.EMERALD_BLOCK,
		Blocks.REDSTONE_BLOCK, Blocks.COAL_BLOCK, Blocks.QUARTZ_BLOCK, Blocks.GOLD_ORE, Blocks.IRON_ORE, Blocks.COAL_ORE, Blocks.LAPIS_ORE, Blocks.REDSTONE_ORE,
		Blocks.DIAMOND_ORE, Blocks.EMERALD_ORE, Blocks.NETHER_QUARTZ_ORE, Blocks.NETHERRACK, Blocks.GLOWSTONE, Blocks.OBSIDIAN, Blocks.END_STONE);
	/** Blocks the blast breaks in the Glacite Tunnels besides ice and stained glass: the tunnel ores. */
	private static final Set<Block> TUNNEL_BLOCKS = Set.of(
		Blocks.POLISHED_DIORITE, Blocks.INFESTED_STONE, Blocks.CARPET.pick(DyeColor.LIGHT_GRAY), Blocks.PRISMARINE, Blocks.PRISMARINE_BRICKS, Blocks.DARK_PRISMARINE,
		Blocks.WOOL.pick(DyeColor.LIGHT_BLUE), Blocks.WOOL.pick(DyeColor.GRAY), Blocks.DYED_TERRACOTTA.pick(DyeColor.CYAN), Blocks.DYED_TERRACOTTA.pick(DyeColor.BROWN), Blocks.TERRACOTTA, Blocks.SMOOTH_RED_SANDSTONE,
		Blocks.INFESTED_COBBLESTONE, Blocks.COBBLESTONE, Blocks.CLAY);

	private static boolean stainedGlass(BlockState state) {
		return state.getBlock() instanceof StainedGlassBlock
			|| state.getBlock() instanceof StainedGlassPaneBlock;
	}

	/** Cached landing position of the thrown pickaxe and its surroundings. */
	private BlockPos lastCentre;
	private BlastArea lastArea;
	private long lastAt;
	private List<BlockPos> lastBlocks = List.of();
	/** The shape and boxes of each of those blocks, computed together with them. */
	private List<VoxelShape> lastShapes = List.of();
	private List<List<AABB>> lastBoxes = List.of();
	private boolean lastFull;

	/**
	 * @return the blocks the blast mines, as measured by the Skyblocker developers: within three blocks before and two after the landing position on
	 *         every axis (the server's blast range), only those exposed to air and of the types mined in the area. A mined block counts as air for the
	 *         following ones, since the blast processes them in this order, where it turns them into air rather than bedrock.
	 */
	private static List<BlockPos> blast(Level level, BlockPos centre, BlastArea area) {
		BlockState[][][] grid = new BlockState[8][8][8];
		var pos = new BlockPos.MutableBlockPos();
		for (int i = 0; i < 8; i++) {
			for (int j = 0; j < 8; j++) {
				for (int k = 0; k < 8; k++) {
					grid[i][j][k] = level.getBlockState(pos.setWithOffset(centre, i - 4, j - 4, k - 4));
				}
			}
		}
		BlockState air = Blocks.AIR.defaultBlockState();
		List<BlockPos> blocks = new ArrayList<>();
		for (int i = 1; i <= 6; i++) {
			for (int j = 1; j <= 6; j++) {
				for (int k = 1; k <= 6; k++) {
					BlockState state = grid[i][j][k];
					if (state.isAir() || state.is(Blocks.BEDROCK)) {
						continue;
					}
					boolean exposed = grid[i - 1][j][k].isAir() || grid[i + 1][j][k].isAir() || grid[i][j - 1][k].isAir() || grid[i][j + 1][k].isAir()
						|| grid[i][j][k - 1].isAir() || grid[i][j][k + 1].isAir();
					if (!exposed) {
						continue;
					}
					boolean mined;
					boolean becomesAir;
					switch (area) {
						case ORES -> {
							// The tunnels are part of the Dwarven Mines, and where the scoreboard does not name the sub-area (or names it differently), tunnel
							// ice and ores (Glacite, Umber, Tungsten) are mined as there and turn into air.
							becomesAir = state.is(Blocks.PACKED_ICE);
							mined = becomesAir || ORE_BLOCKS.contains(state.getBlock()) || stainedGlass(state) || TUNNEL_BLOCKS.contains(state.getBlock());
						}
						case TUNNELS -> {
							becomesAir = state.is(Blocks.PACKED_ICE) || stainedGlass(state);
							mined = becomesAir || TUNNEL_BLOCKS.contains(state.getBlock());
						}
						default -> {
							mined = true;
							becomesAir = true;
						}
					}
					if (!mined) {
						continue;
					}
					if (becomesAir) {
						grid[i][j][k] = air;
					}
					blocks.add(centre.offset(i - 4, j - 4, k - 4));
				}
			}
		}
		return blocks;
	}

	private void drawPickobulus(LevelRenderContext context) {
		Minecraft client = Minecraft.getInstance();
		if (!pickobulus.get() || client.player == null || client.level == null || !SkyBlock.inSkyBlock() || !pickobulusHeld(client)) {
			return;
		}
		BlastArea area = blastArea();
		if (area == null) {
			return;
		}
		// the pickaxe is thrown from slightly above the eyes, straight along the view, and lands on the first block it hits
		var start = client.player.position().add(0, client.player.getEyeHeight() + THROW_HEIGHT, 0);
		var end = start.add(client.player.getForward().scale(THROW_RANGE));
		var hit = client.level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
			ClipContext.Fluid.NONE, client.player));
		if (hit.getType() != HitResult.Type.BLOCK) {
			return;
		}
		BlockPos centre = hit.getBlockPos();
		long now = System.currentTimeMillis();
		boolean full = pickobulusFull.get();
		// The surrounding blocks with their shapes and boxes are recomputed when the aim or area changed or a block was mined, at most four times per
		// second: not every frame, since there are some hundred blocks.
		if (!centre.equals(lastCentre) || area != lastArea || full != lastFull || now - lastAt > 250L) {
			lastCentre = centre;
			lastArea = area;
			lastFull = full;
			lastAt = now;
			lastBlocks = blast(client.level, centre, area);
			lastShapes = new ArrayList<>(lastBlocks.size());
			lastBoxes = new ArrayList<>(lastBlocks.size());
			for (var at : lastBlocks) {
				// every block's shape (slab, snow layer), or a full block for each
				var shape = full ? null : client.level.getBlockState(at).getShape(client.level, at);
				shape = shape == null || shape.isEmpty() ? Shapes.block() : shape;
				lastShapes.add(shape);
				lastBoxes.add(shape.toAabbs());
			}
		}
		if (lastBlocks.isEmpty()) {
			return;
		}
		var cam = client.gameRenderer.mainCamera().position();
		var pose = context.poseStack();
		// Every blast block alike, including the aimed one, in the block overlay style (fill while the overlay is off): outlined, filled, both for the
		// glow, or in its animated gradient, whose colors flow through all blocks as one piece.
		int base = pickobulusColor.get() & 0xFFFFFF;
		var blocks = lastBlocks;
		var shapes = lastShapes;
		var boxes = lastBoxes;
		BlockStyle look = WorldDraw.blockOverlayStyle();
		boolean moving = look == BlockStyle.SHADER;
		boolean filled = look != BlockStyle.OUTLINE;
		boolean outlined = look == BlockStyle.OUTLINE || look == BlockStyle.GLOW;
		float camPlace = (float) ((cam.x + cam.y + cam.z) / 3.0);
		if (filled) {
			float alpha = look == BlockStyle.GLOW ? 0.16f : 0.22f;
			int argb = (Math.round(alpha * 255f) << 24) | base;
			context.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.debugQuads(), (last, consumer) -> {
				for (int i = 0; i < blocks.size(); i++) {
					var at = blocks.get(i);
					for (var part : boxes.get(i)) {
						var box = part.move(at.getX() - cam.x, at.getY() - cam.y, at.getZ() - cam.z).inflate(0.004);
						// the corner's world position, so the colors stay attached to the blocks while the camera moves
						WorldDraw.quads(last, consumer, box, argb, moving ? place -> WorldDraw.blockOverlayShade((place + camPlace) * 0.3f, alpha * 1.4f) : null);
					}
				}
			});
		}
		if (outlined) {
			float width = Math.min(WorldDraw.blockOverlayLineWidth(), 3f);
			int line = (look == BlockStyle.GLOW ? 0xB0000000 : 0xE0000000) | base;
			for (int i = 0; i < blocks.size(); i++) {
				var at = blocks.get(i);
				pose.pushPose();
				pose.translate(at.getX() - cam.x, at.getY() - cam.y, at.getZ() - cam.z);
				try {
					context.submitNodeCollector().submitShapeOutline(pose, shapes.get(i), RenderTypes.lines(),
						line, width, false);
				} finally {
					pose.popPose();
				}
			}
		}
	}
}
