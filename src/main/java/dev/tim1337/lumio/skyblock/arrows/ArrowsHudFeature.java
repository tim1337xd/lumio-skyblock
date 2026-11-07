package dev.tim1337.lumio.skyblock.arrows;

import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.draw.PanelKit;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.ui.ExtraStatusBar;
import dev.tim1337.lumio.api.experimental.ui.Toasts;
import dev.tim1337.lumio.api.experimental.util.Debug;
import dev.tim1337.lumio.api.experimental.util.TextColors;
import dev.tim1337.lumio.api.feature.AddonPanel;
import dev.tim1337.lumio.api.setting.Choice;
import dev.tim1337.lumio.api.setting.Slider;
import dev.tim1337.lumio.api.setting.Toggle;
import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;
import dev.tim1337.lumio.skyblock.data.SkyBlock;
import dev.tim1337.lumio.skyblock.data.SkyBlockArea;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The arrows in the quiver, as a status bar or a small panel, and the warning when they run low. It only reads what the game shows (the preview item in the last hotbar slot and the server's messages) and sends nothing. A shot is
 * counted when an arrow of the player appears; the next real value goes first.
 */
public class ArrowsHudFeature extends AddonPanel {
	/** How it is shown. */
	public enum Style {
		BAR("Bar"),
		TEXT("Text");

		private final Component displayName;

		Style(String displayName) {
			this.displayName = Component.literal(displayName);
		}

		public Component displayName() {
			return displayName;
		}
	}

	/** When it is shown; while low it always is. */
	public enum ShowWhen {
		BOW("While holding a bow"),
		ALWAYS("Always on SkyBlock");

		private final Component displayName;

		ShowWhen(String displayName) {
			this.displayName = Component.literal(displayName);
		}

		public Component displayName() {
			return displayName;
		}
	}

	/** The quiver's preview item lies in the last slot of the hotbar. */
	private static final int QUIVER_SLOT = 8;
	/** An arrow without a known owner counts as the player's within this distance from the eyes, squared, and with a bow in hand. */
	private static final double NEAR_EYES_SQUARED = 9.0;
	private static final String QUIVER_MENU = "Quiver";
	private static final String ARROW_WORD = "Arrow";
	private static final String BOW_LORE = "BOW";
	private static final int LOW_RGB = 0xFF9F0A;
	private static final int OUT_RGB = 0xFF453A;
	private static final float WARN_DEFAULT = 100f;
	private static final float WARN_MOST = 1000f;
	private static final int PANEL_HEIGHT = 17;
	private static final String SAMPLE_NAME = "Flint Arrow";
	private static final int SAMPLE_COUNT = 2880;
	private static final String DOT = " · ";

	private final Choice<Style> style = add(make().choice("style", Component.literal("Style"), Style.BAR, Style::displayName), "General");
	private final Choice<ShowWhen> showWhen = add(make().choice("show_when", Component.literal("Show"), ShowWhen.BOW, ShowWhen::displayName), "General");
	private final Slider warnAt = add(make().slider("warn_at", Component.literal("Warn at"), WARN_DEFAULT, 0f, WARN_MOST, 0), "General");
	private final Toggle sound = add(make().toggle("sound", Component.literal("Sound"), true), "General");

	private final ArrowCount count = new ArrowCount();
	private ItemStack lastQuiver;
	private ItemStack lastHeld;
	private boolean holdsBow;
	private boolean menuNoted;
	private boolean otherItemNoted;
	private boolean strayNoted;
	private int shownCount = -1;
	private String countText = "";
	private int measured = 110;
	/** How many warnings and their sounds there were, for what checks that they came. */
	private int warnings;
	private int sounds;

	public ArrowsHudFeature() {
		super("arrows", "Arrows", "How many arrows are in your quiver, and a warning when they run low.", true,
			Anchor.CENTER, true, 0f, 70f);
		iconRgb(SystemColors.ORANGE_RGB);
		iconName("sword");
		summary("Your quiver, and a warning when it runs low");
	}

	/** Listens for arrows appearing; last of the registrations, as it cannot be undone. */
	public void register() {
		ClientEntityEvents.ENTITY_LOAD.register(this::onEntity);
	}

	private void onEntity(Entity entity, ClientLevel level) {
		if (!(entity instanceof AbstractArrow arrow) || !isEnabled() || !SkyBlock.allowed() || !count.known()) {
			return;
		}
		Player player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		Entity owner = arrow.getOwner();
		boolean own = owner == player;
		if (!own && owner == null && holdsBow && arrow.position().distanceToSqr(player.getEyePosition()) <= NEAR_EYES_SQUARED) {
			own = true;
			if (!strayNoted) {
				strayNoted = true;
				Debug.log("Arrows: an arrow without an owner counted as a shot, close to the player with a bow in hand");
			}
		}
		if (own) {
			tell(count.shot(warnLevel()));
		}
	}

	private int warnLevel() {
		return Math.round(warnAt.get());
	}

	/** Reads what a server message says about arrows. */
	public void onMessage(Component message) {
		if (!isEnabled() || !SkyBlock.allowed()) {
			return;
		}
		QuiverText.Message said = QuiverText.ofMessage(TextColors.noColors(message.getString()));
		if (said == null) {
			return;
		}
		tell(switch (said.kind()) {
			case LEFT, OUT -> count.real(said.name(), said.count(), warnLevel());
			case FORGED -> count.forged(said.name(), said.count(), warnLevel());
		});
	}

	/** Called every tick: reads the preview item when it changed, and what is held; gives the status bars their value. */
	public void tick(Minecraft client) {
		Player player = client.player;
		if (!isEnabled() || player == null || !SkyBlock.allowed()) {
			ExtraStatusBar.hide();
			if (player == null) {
				count.clear();
				lastQuiver = null;
			}
			return;
		}
		ItemStack quiver = player.getInventory().getItem(QUIVER_SLOT);
		if (quiver != lastQuiver) {
			lastQuiver = quiver;
			readQuiver(quiver);
		}
		ItemStack held = player.getMainHandItem();
		if (held != lastHeld) {
			lastHeld = held;
			holdsBow = isBow(held);
		}
		noteMenu(client);
		if (style.get() == Style.BAR && wanted()) {
			ExtraStatusBar.show(count.count(), count.most(), count.name(), numberRgb());
		} else {
			ExtraStatusBar.hide();
		}
	}

	private void readQuiver(ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}
		String name = SkyBlockArea.plainLine(stack.getHoverName().getString());
		List<String> lines = lore(stack);
		QuiverText.Selected selected = QuiverText.ofItem(name, lines);
		if (selected != null) {
			tell(count.real(selected.name(), selected.count(), warnLevel()));
		} else if (!otherItemNoted && name.contains(QUIVER_MENU)) {
			// a form of the item no one has seen yet, such as an empty quiver: one line to learn it from
			otherItemNoted = true;
			Debug.log("Arrows: a quiver item that says nothing of arrows: {} / {}", name, lines);
		}
	}

	private static List<String> lore(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore == null) {
			return List.of();
		}
		List<String> lines = new ArrayList<>(lore.lines().size());
		for (Component line : lore.lines()) {
			lines.add(SkyBlockArea.plainLine(line.getString()));
		}
		return lines;
	}

	/** @return whether an item is a bow: the game's own, also for shortbows, or one that says so at the end of its lore */
	private static boolean isBow(ItemStack stack) {
		if (stack.is(Items.BOW)) {
			return true;
		}
		for (String line : lore(stack)) {
			if (line.endsWith(BOW_LORE) && line.equals(line.toUpperCase(Locale.ROOT))) {
				return true;
			}
		}
		return false;
	}

	/** The quiver's menu has no form known yet to read counts from: one line to learn it from. */
	private void noteMenu(Minecraft client) {
		boolean open = client.gui.screen() instanceof AbstractContainerScreen<?> menu && menu.getTitle().getString().contains(QUIVER_MENU);
		if (open && !menuNoted && client.gui.screen() instanceof AbstractContainerScreen<?> menu) {
			for (var slot : menu.getMenu().slots) {
				if (slot.hasItem() && slot.getItem().getHoverName().getString().contains(ARROW_WORD)) {
					Debug.log("Arrows: the quiver menu has {}: {}", slot.getItem().getHoverName().getString(), lore(slot.getItem()));
					break;
				}
			}
		}
		menuNoted = open;
	}

	private void tell(ArrowCount.Alarm alarm) {
		if (alarm == ArrowCount.Alarm.NONE) {
			return;
		}
		warnings++;
		Toasts.info(alarm == ArrowCount.Alarm.OUT ? "Out of arrows" : "Only " + warnLevel() + " arrows left", count.name());
		if (sound.get()) {
			sounds++;
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), alarm == ArrowCount.Alarm.OUT ? 0.7f : 1f));
		}
	}

	private boolean low() {
		return count.known() && count.count() < warnLevel();
	}

	/** @return whether the arrows are to be shown now */
	private boolean wanted() {
		return count.known() && (holdsBow || low() || showWhen.get() == ShowWhen.ALWAYS);
	}

	/** @return the color of the number as 0xRRGGBB, or 0 for the usual */
	private int numberRgb() {
		if (!count.known() || !low()) {
			return 0;
		}
		return count.count() == 0 ? OUT_RGB : LOW_RGB;
	}

	@Override
	public boolean shown(Minecraft client) {
		return style.get() == Style.TEXT && wanted();
	}

	@Override
	public boolean inEditorWhenHidden() {
		return style.get() == Style.TEXT;
	}

	@Override
	public int panelWidth() {
		return measured;
	}

	@Override
	public int panelHeight() {
		return PANEL_HEIGHT;
	}

	@Override
	public void drawPanel(Canvas ui, GuiGraphicsExtractor graphics, Minecraft client) {
		boolean real = count.known();
		String name = real ? count.name() : SAMPLE_NAME;
		int value = real ? count.count() : SAMPLE_COUNT;
		if (value != shownCount) {
			shownCount = value;
			countText = String.format(Locale.ROOT, "%,d", value);
		}
		float nameWidth = ui.widthScaled(name, TextScale.BODY);
		float dotWidth = ui.widthScaled(DOT, TextScale.BODY);
		float countWidth = ui.widthScaled(countText, TextScale.BODY);
		measured = Math.round(nameWidth + dotWidth + countWidth) + 2 * PanelKit.PAD;
		drawGlass(ui, measured, PANEL_HEIGHT, PanelKit.RADIUS);
		float y = PANEL_HEIGHT / 2f - 4.5f * TextScale.BODY;
		float x = PanelKit.PAD;
		ui.textAtScaled(name, x, y, TextScale.BODY, HudTheme.text());
		x += nameWidth;
		ui.textAtScaled(DOT, x, y, TextScale.BODY, HudTheme.secondary());
		x += dotWidth;
		int rgb = numberRgb();
		ui.textAtScaled(countText, x, y, TextScale.BODY, rgb == 0 ? HudTheme.text() : 0xFF000000 | rgb);
	}
}
