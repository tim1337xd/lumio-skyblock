package dev.tim1337.lumio.skyblock.hud;

import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.util.KeyNames;
import dev.tim1337.lumio.skyblock.data.ItemRepo;
import dev.tim1337.lumio.skyblock.data.ItemTypes;
import dev.tim1337.lumio.skyblock.data.SkyBlockPrices;

import net.minecraft.client.KeyMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The hover card of an item in the item search's own panels (the game's tooltip stays the game's): its name in its rarity color, its rarity and type,
 * the lowest BIN or Bazaar price and the craft cost, how many the player has and where, and the keys: Click Details, then the bound keys for the recipe,
 * uses and Pin. What it says is worked out once per hovered item and again each second, never per frame.
 */
final class ItemHoverCard {
	private static final int WIDTH = 168;
	private static final int PAD = 8;
	private static final int ROW = 10;
	private static final int OFFSET = 12;
	private static final int HINT_HEIGHT = 11;
	private static final long REFRESH_MILLIS = 1000L;
	private static final int GOLD = 0xFFFFC94A;
	private static final String COIN = "SKYBLOCK_COIN";
	private static final String DRAG_HINT = "Or drag it into Pinned.";

	/** A key cap and what it does. */
	private record Hint(String key, String action) {
	}

	private final ItemListFeature feature;
	private String shownFor;
	private long madeAt;
	private String label = "";
	private String price = "";
	private String priceLabel = "";
	private String craft = "";
	private final List<Hint> hints = new ArrayList<>();

	ItemHoverCard(ItemListFeature feature) {
		this.feature = feature;
	}

	private void make(ItemRepo.Entry entry) {
		long now = System.currentTimeMillis();
		if (entry.id.equals(shownFor) && now - madeAt < REFRESH_MILLIS) {
			return;
		}
		shownFor = entry.id;
		madeAt = now;
		label = ItemTypes.of(entry).label().toUpperCase(Locale.ROOT);
		double buy = SkyBlockPrices.bazaarBuy(entry.id);
		double value = buy > 0 ? buy : SkyBlockPrices.lowestBin(entry.id);
		price = value > 0 ? ItemDetailView.coins(value) : "";
		priceLabel = buy > 0 ? "Bazaar" : "Lowest BIN";
		double cost = craftCost(entry.id);
		craft = cost > 0 ? ItemDetailView.coins(cost) : "";
		hints.clear();
		hints.add(new Hint("Click", "Details"));
		addHint(feature.recipeKey(), "Recipe");
		addHint(feature.usageKey(), "Uses");
		addHint(feature.pinKey(), "Pin");
		if (MarketSearch.of(entry.id) != MarketSearch.Market.NONE) {
			addHint(feature.marketKey(), "Market");
		}
	}

	private void addHint(KeyMapping key, String action) {
		if (key != null && !key.isUnbound()) {
			hints.add(new Hint(KeyNames.of(key), action));
		}
	}

	/** @return what its first crafting recipe costs from its ingredients' prices, or 0 */
	private static double craftCost(String id) {
		for (ItemRepo.Recipe recipe : ItemRepo.recipesFor(id)) {
			if (!recipe.crafting()) {
				continue;
			}
			double cost = 0;
			for (ItemRepo.Stack stack : recipe.inputs()) {
				if (stack != null) {
					double each = stack.id().equals(COIN) ? 1 : SkyBlockPrices.listedPrice(stack.id());
					if (each <= 0) {
						return 0;
					}
					cost += each * stack.count();
				}
			}
			double made = recipe.outputs().isEmpty() ? 1 : Math.max(1, recipe.outputs().getFirst().count());
			return cost / made;
		}
		return 0;
	}

	/** Draws the card beside the mouse, kept on the screen. */
	void draw(Canvas ui, ItemRepo.Entry entry, int mouseX, int mouseY, int screenWidth, int screenHeight) {
		make(entry);
		boolean withPrice = feature.pricesOnHover() && (!price.isEmpty() || !craft.isEmpty());
		int rows = 1 + (label.isEmpty() ? 0 : 1) + (withPrice ? 1 : 0);
		int height = 2 * PAD + rows * ROW + 6 + HINT_HEIGHT + ROW;
		int x = mouseX + OFFSET + WIDTH <= screenWidth - 4 ? mouseX + OFFSET : mouseX - OFFSET - WIDTH;
		int y = Math.max(4, Math.min(mouseY - 4, screenHeight - 4 - height));
		ui.graphics().nextStratum();
		HudTheme.panel(ui, x, y, WIDTH, height, Radius.LG, Math.max(0.9f, HudTheme.opacity()));
		int left = x + PAD;
		int width = WIDTH - 2 * PAD;
		float rowY = y + PAD;
		int rarity = HudTheme.readable(entry.rarityColor());
		ui.textAtScaledBold(ui.fitScaled(entry.plainName(), width, TextScale.BODY), left, rowY, TextScale.BODY, rarity);
		rowY += ROW + 1;
		if (!label.isEmpty()) {
			ui.textAtScaledBold(ui.fitScaled(label, width, TextScale.SMALL), left, rowY, TextScale.SMALL, Draw.withOpacity(rarity, 0.85f));
			rowY += ROW;
		}
		if (withPrice) {
			float cursor = left;
			if (!price.isEmpty()) {
				cursor = pair(ui, priceLabel, price, cursor, rowY);
			}
			if (!craft.isEmpty()) {
				pair(ui, "Craft", craft, cursor + 8, rowY);
			}
			rowY += ROW;
		}
		rowY += 3;
		ui.fill(left, Math.round(rowY), width, 1, HudTheme.line(0.1f));
		rowY += 4;
		float hintX = left;
		for (Hint hint : hints) {
			float keyWidth = ui.boldWidthScaled(hint.key(), TextScale.SMALL) + 6;
			float actionWidth = ui.widthScaled(hint.action(), TextScale.SMALL);
			if (hintX + keyWidth + 3 + actionWidth > left + width) {
				break;
			}
			ui.roundedSmooth(hintX, rowY, keyWidth, HINT_HEIGHT - 1, Radius.XS, Draw.withOpacity(HudTheme.text(), 0.12f));
			ui.textAtScaledBold(hint.key(), hintX + 3, rowY + 1.5f, TextScale.SMALL, HudTheme.text());
			ui.textAtScaled(hint.action(), hintX + keyWidth + 3, rowY + 1.5f, TextScale.SMALL, HudTheme.secondary());
			hintX += keyWidth + 3 + actionWidth + 7;
		}
		rowY += HINT_HEIGHT + 1;
		ui.textAtScaled(DRAG_HINT, left, rowY, TextScale.SMALL, HudTheme.secondary());
	}

	/** A quiet label and a bold value after it. @return the x after it */
	private static float pair(Canvas ui, String label, String value, float x, float y) {
		ui.textAtScaled(label, x, y, TextScale.SMALL, HudTheme.secondary());
		float valueX = x + ui.widthScaled(label, TextScale.SMALL) + 4;
		ui.textAtScaledBold(value, valueX, y, TextScale.SMALL, GOLD);
		return valueX + ui.boldWidthScaled(value, TextScale.SMALL);
	}
}
