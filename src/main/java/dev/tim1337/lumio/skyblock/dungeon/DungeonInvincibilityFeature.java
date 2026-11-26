package dev.tim1337.lumio.skyblock.dungeon;

import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;

import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.draw.PanelKit;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.util.ServerTicks;
import dev.tim1337.lumio.api.feature.AddonPanel;
import dev.tim1337.lumio.api.setting.Choice;
import dev.tim1337.lumio.api.setting.Toggle;
import dev.tim1337.lumio.skyblock.data.ItemGains;
import dev.tim1337.lumio.skyblock.data.ItemHoldings;
import dev.tim1337.lumio.skyblock.data.ItemRepo;
import dev.tim1337.lumio.skyblock.mixin.accessor.ContainerScreenAccess;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Invincibility timers: the Bonzo Mask, the Spirit Mask and the Phoenix pet, each ready (green), invincible for a few seconds (blue) or cooling
 * down (grey), from the server's line when one saves the player and counted in server ticks ({@link Invincibility}). Alerts when one saves the
 * player and when it is ready again, a party message if wanted (off at first, through the announcement queue), and the cooldown over the mask's
 * slot in the inventory.
 */
public class DungeonInvincibilityFeature extends AddonPanel {
	/** When the panel shows. */
	public enum Show {
		ALWAYS("Always"), IN_BOSS("In boss"), ON_COOLDOWN("On cooldown");

		final String label;

		Show(String label) {
			this.label = label;
		}
	}

	private static final String ALERTS = "Alerts";
	private static final int WIDTH = 150;
	private static final int PAD = PanelKit.PAD;
	private static final int HEADER = 18;
	private static final int ROW = 22;
	private static final int ICON = 16;
	/** The item in its box: the room it keeps to the box's edge, and how dark it is while its item cools down. */
	private static final float ICON_INSET = 1.5f;
	private static final float ICON_DIM = 0.55f;
	private static final int ITEM_SIZE = 16;
	private static final int ICON_GAP = 8;
	private static final int SLOT = 16;
	private static final int COOLDOWN_SHADE = 0x99000000;
	/** The sample in the HUD editor: a Spirit Mask just used, a Phoenix cooling down. */
	private static final long SAMPLE_SPIRIT_TICKS = 56;
	private static final long SAMPLE_PHOENIX_TICKS = 840;

	private final Choice<Show> show = add(make().choice("show", Component.literal("Show"), Show.ALWAYS, value -> Component.literal(value.label),
		Choice.Style.SEGMENTED), "General");
	private final Toggle onSlot = add(make().toggle("on_slot", Component.literal("Cooldown on the mask"), true), "General",
		"Shades its slot while it cools down");
	private final Toggle savedAlert = add(make().toggle("saved_alert", Component.literal("Alert when it saves you"), true), ALERTS);
	private final Toggle readyAlert = add(make().toggle("ready_alert", Component.literal("Alert when ready again"), true), ALERTS);
	private final Choice<DungeonAlert.Style> style = add(make().choice("style", Component.literal("Style"), DungeonAlert.Style.TOAST,
		value -> Component.literal(value.label()), Choice.Style.SEGMENTED), ALERTS);
	private final Toggle announce = add(make().toggle("announce", Component.literal("Tell the party"), false), ALERTS,
		"A party message when one saves you");

	private final Invincibility timers = new Invincibility();
	private final Invincibility sample = new Invincibility();
	private final Invincibility.State[] lastStates = new Invincibility.State[Invincibility.KINDS.size()];
	/** The panel's texts, made once per server tick. */
	private final Invincibility.State[] states = new Invincibility.State[Invincibility.KINDS.size()];
	private final String[] values = new String[Invincibility.KINDS.size()];
	/** The item drawn in each box (the mask's head, the pet's head), from the item list's data; empty until that is read. */
	private final ItemStack[] icons = new ItemStack[Invincibility.KINDS.size()];
	private final String[] iconIds = new String[Invincibility.KINDS.size()];
	private long iconTick = Long.MIN_VALUE;
	private long textTick = Long.MIN_VALUE;
	private boolean textLive;
	private final AnnouncementQueue queue = new AnnouncementQueue(System::currentTimeMillis, span -> ThreadLocalRandom.current().nextLong(span + 1),
		AnnouncementQueue.SHARED_GAP);
	private long sends;

	public DungeonInvincibilityFeature() {
		super("dungeon_invincibility", "Invincibility timers", "Bonzo, Spirit and Phoenix timers.", false,
			Anchor.RIGHT, false, 8f, 196f);
		iconRgb(SystemColors.BLUE_RGB);
		iconName("timer");
		summary("Bonzo, Spirit, Phoenix");
		style.visibleWhen(() -> savedAlert.get() || readyAlert.get());
		offerNoBackground();
		sample.saved(Invincibility.Kind.SPIRIT, 0, -1);
		sample.saved(Invincibility.Kind.PHOENIX, 0, -1);
		Dungeon.listen(new Dungeon.Listener() {
			@Override
			public void left() {
				queue.clear();
			}
		});
	}

	public void register() {
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
	}

	/** Reads a server line: a save starts that item's timer. */
	public void onServerLine(String line) {
		if (!isEnabled()) {
			return;
		}
		Invincibility.Kind kind = Invincibility.savedBy(line);
		if (kind == null) {
			return;
		}
		long tick = ServerTicks.count();
		timers.saved(kind, tick, kind == Invincibility.Kind.BONZO ? helmetCooldown() : -1);
		lastStates[kind.ordinal()] = Invincibility.State.INVINCIBLE;
		if (savedAlert.get()) {
			DungeonAlert.show(style.get(), kind.label() + " saved you", "invincible for a moment", SystemColors.BLUE);
		}
		if (announce.get()) {
			queue.add("saved " + sends++, "pc " + kind.label() + " used (" + timers.used(tick) + "/3 on cooldown)");
		}
	}

	/** The Bonzo Mask's own cooldown, from the lore of the helmet worn, or -1. */
	private static int helmetCooldown() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return -1;
		}
		List<String> lore = ItemGains.loreLines(client.player.getItemBySlot(EquipmentSlot.HEAD));
		for (int i = lore.size() - 1; i >= 0; i--) {
			int seconds = Invincibility.loreCooldown(lore.get(i));
			if (seconds > 0) {
				return seconds;
			}
		}
		return -1;
	}

	private void tick(Minecraft client) {
		if (!isEnabled() || client.player == null) {
			queue.clear();
			return;
		}
		long tick = ServerTicks.count();
		for (Invincibility.Kind kind : Invincibility.KINDS) {
			Invincibility.State state = timers.state(kind, tick);
			Invincibility.State last = lastStates[kind.ordinal()];
			if (state == Invincibility.State.READY && last != null && last != Invincibility.State.READY && readyAlert.get()) {
				DungeonAlert.show(style.get(), kind.label() + " ready", "", SystemColors.GREEN);
			}
			lastStates[kind.ordinal()] = state;
		}
		AnnouncementQueue.Waiting next = queue.poll();
		if (next != null && client.getConnection() != null) {
			client.getConnection().sendCommand(next.text());
		}
		if (queue.waiting() == 0) {
			queue.forgetEvents();
		}
	}

	private boolean shownNow() {
		if (!isEnabled() || !Dungeon.in()) {
			return false;
		}
		return switch (show.get()) {
			case ALWAYS -> true;
			case IN_BOSS -> Dungeon.inBoss();
			case ON_COOLDOWN -> timers.used(ServerTicks.count()) > 0;
		};
	}

	@Override
	public boolean shown(Minecraft client) {
		return shownNow() || simulated();
	}

	@Override
	public boolean inEditorWhenHidden() {
		return true;
	}

	@Override
	public int panelWidth() {
		return WIDTH;
	}

	@Override
	public int panelHeight() {
		return PAD + HEADER + Invincibility.KINDS.size() * ROW + PAD / 2;
	}

	@Override
	public void drawPanel(Canvas ui, GuiGraphicsExtractor graphics, Minecraft client) {
		drawGlass(ui, WIDTH, panelHeight(), PanelKit.RADIUS);
		boolean live = shownNow();
		Invincibility shownTimers = live ? timers : sample;
		long tick = live ? ServerTicks.count() : SAMPLE_SPIRIT_TICKS;
		PanelKit.sectionLabel(ui, "INVINCIBILITY", PAD, PAD);
		if (tick != textTick || live != textLive) {
			textTick = tick;
			textLive = live;
			for (Invincibility.Kind kind : Invincibility.KINDS) {
				long at = !live && kind == Invincibility.Kind.PHOENIX ? SAMPLE_PHOENIX_TICKS : tick;
				Invincibility.State state = shownTimers.state(kind, at);
				states[kind.ordinal()] = state;
				values[kind.ordinal()] = switch (state) {
					case READY -> "Ready";
					case INVINCIBLE -> tenths(shownTimers.ticksLeft(kind, at));
					case COOLDOWN -> wholeSeconds(shownTimers.ticksLeft(kind, at));
				};
			}
		}
		// the icons: looked up once per server tick, and every frame while one is still missing (the item list may not be read yet)
		if (tick != iconTick || iconsMissing()) {
			iconTick = tick;
			findIcons(client);
		}
		int y = PAD + HEADER;
		for (Invincibility.Kind kind : Invincibility.KINDS) {
			int color = switch (states[kind.ordinal()]) {
				case READY -> SystemColors.GREEN;
				case INVINCIBLE -> SystemColors.BLUE;
				case COOLDOWN -> HudTheme.secondary();
			};
			String value = values[kind.ordinal()];
			int iconY = y + (ROW - ICON) / 2;
			ui.roundedSmooth(PAD, iconY, ICON, ICON, 4f, Draw.withOpacity(color, 0.28f));
			ui.ring(PAD, iconY, ICON, ICON, 4f, 1, Draw.withOpacity(color, 0.6f));
			drawIcon(ui, graphics, icons[kind.ordinal()], PAD, iconY, states[kind.ordinal()] == Invincibility.State.COOLDOWN);
			ui.textAtScaled(kind.label(), PAD + ICON + ICON_GAP, y + (ROW - 8) / 2f, TextScale.BODY, HudTheme.text());
			TabularText.drawRight(ui, value, WIDTH - PAD, y + (ROW - 8) / 2f, TextScale.BODY, true, color);
			y += ROW;
		}
	}

	/**
	 * Finds the item of each box in the item list's data, once per server tick: the mask the player owns (the starred one if that is worn or kept
	 * somewhere, else the plain one) and the Phoenix pet. Only the list the mod already has is read; it is loaded as the item list loads it.
	 */
	private void findIcons(Minecraft client) {
		if (!ItemRepo.ready()) {
			ItemRepo.loadIfNeeded();
			return;
		}
		String worn = client.player == null ? null : ItemGains.idOf(client.player.getItemBySlot(EquipmentSlot.HEAD));
		for (Invincibility.Kind kind : Invincibility.KINDS) {
			String starred = Invincibility.starredId(kind);
			boolean owned = starred != null && (starred.equals(worn) || ItemHoldings.count(starred) > 0);
			for (String id : Invincibility.iconIds(kind, owned)) {
				ItemRepo.Entry entry = ItemRepo.get(id);
				if (entry == null) {
					continue;
				}
				if (!id.equals(iconIds[kind.ordinal()])) {
					iconIds[kind.ordinal()] = id;
					icons[kind.ordinal()] = entry.stack();
				}
				break;
			}
		}
	}

	private boolean iconsMissing() {
		for (ItemStack icon : icons) {
			if (icon == null) {
				return true;
			}
		}
		return false;
	}

	/** Draws an item centered in its box, as the inventory draws it, scaled to the box; darker while its item cools down. */
	private static void drawIcon(Canvas ui, GuiGraphicsExtractor graphics, ItemStack stack, int x, int y, boolean dim) {
		if (stack == null || stack.isEmpty()) {
			return;
		}
		float scale = Invincibility.iconScale(ICON, ICON_INSET, ITEM_SIZE);
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x + ICON / 2f - ITEM_SIZE * scale / 2f, y + ICON / 2f - ITEM_SIZE * scale / 2f);
		pose.scale(scale, scale);
		graphics.item(stack, 0, 0);
		pose.popMatrix();
		if (dim) {
			graphics.nextStratum();
			ui.roundedSmooth(x, y, ICON, ICON, 4f, Draw.withOpacity(0xFF000000, ICON_DIM));
		}
	}

	private static String tenths(long ticks) {
		long tenths = (ticks + 1) / 2;
		return tenths / 10 + "." + tenths % 10 + " s";
	}

	private static String wholeSeconds(long ticks) {
		return (ticks + 19) / 20 + " s";
	}

	/** Shades a mask's slot in the inventory, from the bottom up, by the share of its cooldown still left. */
	public void drawSlots(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (!isEnabled() || !onSlot.get() || timers.used(ServerTicks.count()) == 0) {
			return;
		}
		long tick = ServerTicks.count();
		ContainerScreenAccess access = (ContainerScreenAccess) screen;
		for (Slot slot : screen.getMenu().slots) {
			ItemStack stack = slot.getItem();
			if (stack.isEmpty()) {
				continue;
			}
			Invincibility.Kind kind = maskOf(ItemGains.idOf(stack));
			if (kind == null) {
				continue;
			}
			float share = timers.cooldownShare(kind, tick);
			if (share <= 0f) {
				continue;
			}
			int x = access.evskyblock$left() + slot.x;
			int top = access.evskyblock$top() + slot.y;
			int shade = Math.round(SLOT * share);
			graphics.fill(x, top + SLOT - shade, x + SLOT, top + SLOT, COOLDOWN_SHADE);
		}
	}

	private static Invincibility.Kind maskOf(String id) {
		if (id == null) {
			return null;
		}
		return switch (id) {
			case "BONZO_MASK", "STARRED_BONZO_MASK" -> Invincibility.Kind.BONZO;
			case "SPIRIT_MASK", "STARRED_SPIRIT_MASK" -> Invincibility.Kind.SPIRIT;
			default -> null;
		};
	}
}
