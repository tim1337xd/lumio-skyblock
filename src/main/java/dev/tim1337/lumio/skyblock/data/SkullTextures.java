package dev.tim1337.lumio.skyblock.data;

import com.google.common.collect.LinkedHashMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** What identifies a player head in SkyBlock: its texture. */
public final class SkullTextures {
	private static final Pattern TEXTURE_URL = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"");

	private SkullTextures() {
	}

	/** @return the end of the texture URL of a head, which identifies it, from its Base64 texture property; "" when there is none */
	public static String ofProperty(String base64) {
		try {
			String json = new String(Base64.getMimeDecoder().decode(base64), StandardCharsets.UTF_8);
			Matcher matcher = TEXTURE_URL.matcher(json);
			if (matcher.find()) {
				String url = matcher.group(1);
				return url.substring(url.lastIndexOf('/') + 1);
			}
		} catch (IllegalArgumentException ignored) {
			return "";
		}
		return "";
	}

	/** @return the end of the texture address of a player head, "" for anything else */
	public static String of(ItemStack head) {
		if (head.isEmpty() || !head.is(Items.PLAYER_HEAD)) {
			return "";
		}
		var profile = head.get(DataComponents.PROFILE);
		if (profile != null) {
			for (var property : profile.partialProfile().properties().get("textures")) {
				String texture = ofProperty(property.value());
				if (!texture.isEmpty()) {
					return texture;
				}
			}
		}
		return "";
	}

	/** @return the Base64 texture property of a player head as the server sent it, to make the head again; "" for anything else */
	public static String propertyOf(ItemStack head) {
		if (head.isEmpty() || !head.is(Items.PLAYER_HEAD)) {
			return "";
		}
		var profile = head.get(DataComponents.PROFILE);
		if (profile != null) {
			for (var property : profile.partialProfile().properties().get("textures")) {
				if (!property.value().isBlank()) {
					return property.value();
				}
			}
		}
		return "";
	}

	/** Gives a player head this Base64 texture property; its profile id is made from the texture, so the same texture is the same head. */
	public static void apply(ItemStack head, String property) {
		LinkedHashMultimap<String, Property> properties = LinkedHashMultimap.create();
		properties.put("textures", new Property("textures", property));
		UUID uuid = UUID.nameUUIDFromBytes(property.getBytes(StandardCharsets.UTF_8));
		head.set(DataComponents.PROFILE, ResolvableProfile.createResolved(new GameProfile(uuid, "", new PropertyMap(properties))));
	}

	/** @return a player head with this Base64 texture property */
	public static ItemStack head(String property) {
		ItemStack head = new ItemStack(Items.PLAYER_HEAD);
		apply(head, property);
		return head;
	}

	/** @return whether a stack is a player head with this texture */
	public static boolean is(ItemStack head, String texture) {
		if (head.isEmpty() || !head.is(Items.PLAYER_HEAD)) {
			return false;
		}
		var profile = head.get(DataComponents.PROFILE);
		if (profile == null) {
			return false;
		}
		for (var property : profile.partialProfile().properties().get("textures")) {
			if (texture.equals(ofProperty(property.value()))) {
				return true;
			}
		}
		return false;
	}
}
