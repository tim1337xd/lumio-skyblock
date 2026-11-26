package dev.tim1337.lumio.skyblock.hud;

import dev.tim1337.lumio.api.TooltipLine;
import dev.tim1337.lumio.api.TooltipLineProvider;
import dev.tim1337.lumio.api.experimental.util.KeyNames;
import dev.tim1337.lumio.skyblock.SkyBlockFeatures;
import dev.tim1337.lumio.skyblock.data.ItemRepo;
import dev.tim1337.lumio.skyblock.data.SkyBlockPrices;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * What hovering an item in the item search's panels shows: the item's real tooltip, as the game draws it, with the item search's own lines added
 * at its end (the price, how many the player has and where, and the keys), the compact card of before, or nothing. The game's lines are never
 * touched: the added ones go into Lumio's separate block, like every added line.
 */
public final class ItemSearchHover implements TooltipLineProvider {
	/** What shows while the mouse is on an item. */
	public enum OnHover {
		TOOLTIP("Item tooltip"),
		CARD("Compact card"),
		NONE("Nothing");

		private final String label;

		OnHover(String label) {
			this.label = label;
		}

		Component displayName() {
			return Component.literal(label);
		}
	}

	/** A hover counts for this long after its last frame: the tooltip's lines are asked for a few times a second, not every frame. */
	private static final long HOVER_MILLIS = 500L;
	static final String CLICK = "Click";
	static final String SEPARATOR = " · ";

	private final ItemListFeature feature;
	private ItemRepo.Entry hovered;
	private long hoveredAt;

	ItemSearchHover(ItemListFeature feature) {
		this.feature = feature;
	}

	/** An item of the panels is under the mouse this frame, with its tooltip showing. */
	void hover(ItemRepo.Entry entry) {
		hovered = entry;
		hoveredAt = System.currentTimeMillis();
	}

	@Override
	public void addLines(ItemStack stack, List<TooltipLine> lines) {
		ItemRepo.Entry entry = hovered;
		// only the tooltip the item search itself shows: the same item in the inventory keeps its usual lines
		if (entry == null || System.currentTimeMillis() - hoveredAt > HOVER_MILLIS || entry.stack() != stack) {
			return;
		}
		String priceLabel = "";
		String price = "";
		// the tooltip's own price lines say it already while they are on
		if (feature.pricesOnHover() && !SkyBlockFeatures.TOOLTIP_LINES.isEnabled()) {
			double buy = SkyBlockPrices.bazaarBuy(entry.id);
			double value = buy > 0 ? buy : SkyBlockPrices.lowestBin(entry.id);
			priceLabel = buy > 0 ? "Bazaar" : "Lowest BIN";
			price = value > 0 ? ItemDetailView.coins(value) : "";
		}
		List<String> keys = new ArrayList<>();
		keys.add(CLICK + " Details");
		addKey(keys, feature.recipeKey(), "Recipe");
		addKey(keys, feature.usageKey(), "Uses");
		addKey(keys, feature.pinKey(), "Pin");
		if (MarketSearch.of(entry.id) != MarketSearch.Market.NONE) {
			addKey(keys, feature.marketKey(), "Market");
		}
		lines.addAll(lines(priceLabel, price, keys));
	}

	/**
	 * Draws an item's tooltip now, as the game draws it over a slot: the same lines, picture and style, through the game's own tooltip drawing,
	 * so Lumio's tooltip look and every added line apply as in the inventory. Drawn at once because the panels are drawn after the screen, when
	 * the game's tooltip of this frame has had its turn.
	 */
	static void drawTooltip(GuiGraphicsExtractor graphics, ItemStack stack, int mouseX, int mouseY) {
		Minecraft client = Minecraft.getInstance();
		List<ClientTooltipComponent> parts = new ArrayList<>();
		for (Component line : Screen.getTooltipFromItem(client, stack)) {
			parts.add(ClientTooltipComponent.create(line.getVisualOrderText()));
		}
		if (parts.isEmpty()) {
			return;
		}
		stack.getTooltipImage().ifPresent(image -> parts.add(1, ClientTooltipComponent.create(image)));
		graphics.nextStratum();
		//#if 26.3
		//$ graphics.tooltip(client.font, parts, mouseX, mouseY, DefaultTooltipPositioner.INSTANCE, stack.get(DataComponents.TOOLTIP_STYLE), true);
		//#else
		graphics.tooltip(client.font, parts, mouseX, mouseY, DefaultTooltipPositioner.INSTANCE, stack.get(DataComponents.TOOLTIP_STYLE));
		//#endif
	}
	private static void addKey(List<String> keys, KeyMapping key, String action) {
		if (key != null && !key.isUnbound()) {
			keys.add(KeyNames.of(key) + " " + action);
		}
	}

	/**
	 * The lines the item search adds under an item's tooltip, in their order: the price and one quiet line with the keys.
	 *
	 * @param price the price as shown, or "" for none
	 * @param keys  each key with what it does ("R Recipe")
	 */
	static List<TooltipLine> lines(String priceLabel, String price, List<String> keys) {
		List<TooltipLine> lines = new ArrayList<>();
		if (!price.isEmpty()) {
			lines.add(TooltipLine.row(priceLabel, price, ""));
		}
		if (!keys.isEmpty()) {
			lines.add(TooltipLine.footnote(String.join(SEPARATOR, keys)));
		}
		return lines;
	}
}
