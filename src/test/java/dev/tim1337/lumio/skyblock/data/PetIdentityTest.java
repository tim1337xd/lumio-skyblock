package dev.tim1337.lumio.skyblock.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

class PetIdentityTest {
	/** A repository file with an animated skin, a skin with chosen variants, a seasonal and a day and night skin. */
	private static final RepoPetSkins SKINS = RepoPetSkins.of(JsonParser.parseString("""
		{
		  "skins": {
		    "PET_SKIN_ANIMATED": {"ticks": 4, "textures": ["id-1:FRAME_ONE", "id-2:FRAME_TWO"]},
		    "PET_SKIN_CHOICE_RED": {"ticks": 1, "textures": ["id:RED"]},
		    "PET_SKIN_CHOICE_BLUE": {"ticks": 1, "textures": ["id:BLUE"]},
		    "PET_SKIN_SEASONS_SPRING": {"ticks": 1, "textures": ["id:SPRING"]},
		    "PET_SKIN_SEASONS_WINTER": {"ticks": 1, "textures": ["id:WINTER"]},
		    "PET_SKIN_TIME_DAY": {"ticks": 1, "textures": ["id:DAY"]},
		    "PET_SKIN_TIME_NIGHT": {"ticks": 1, "textures": ["id:NIGHT"]},
		    "PET_SKIN_BROKEN": {"ticks": 1, "textures": "not a list"}
		  },
		  "pet_skin_variant": {"PET_SKIN_CHOICE": ["PET_SKIN_CHOICE_RED", "PET_SKIN_CHOICE_BLUE"]},
		  "pet_skin_nbt_name": ["choice"]
		}
		""").getAsJsonObject());

	private static PetIdentity pet(String info) {
		return PetIdentity.read(JsonParser.parseString(info).getAsJsonObject(), SKINS);
	}

	@Test
	void aRealPetIsKeyedByItsUuid() {
		PetIdentity dragon = pet("{\"type\":\"GOLDEN_DRAGON\",\"tier\":\"LEGENDARY\",\"uniqueId\":\"AB-12\",\"skin\":\"GOLDEN_DRAGON_ANIMATED\"}");
		assertEquals("uuid:ab-12", dragon.key());
		assertEquals("GOLDEN_DRAGON;4", dragon.petItem());
		assertEquals("PET_SKIN_GOLDEN_DRAGON_ANIMATED", dragon.skinItem());
	}

	@Test
	void theOlderUuidFieldCountsWhenThereIsNoUniqueId() {
		assertEquals("uuid:cd-34", pet("{\"type\":\"WOLF\",\"tier\":\"EPIC\",\"uuid\":\"CD-34\"}").key());
	}

	@Test
	void withoutUuidTypeRaritySkinAndVariantMakeTheKey() {
		PetIdentity plain = pet("{\"type\":\"WOLF\",\"tier\":\"EPIC\"}");
		PetIdentity doge = pet("{\"type\":\"WOLF\",\"tier\":\"EPIC\",\"skin\":\"WOLF_DOGE\"}");
		PetIdentity legendary = pet("{\"type\":\"WOLF\",\"tier\":\"LEGENDARY\"}");
		assertEquals("WOLF;3||-1", plain.key());
		assertNotEquals(plain.key(), doge.key(), "two of the same pet with different skins are two icons");
		assertNotEquals(plain.key(), legendary.key());
	}

	@Test
	void noSkinIsWrittenInSeveralWays() {
		assertEquals("", pet("{\"type\":\"WOLF\",\"tier\":\"EPIC\",\"skin\":\"null\"}").skin());
		assertEquals("", pet("{\"type\":\"WOLF\",\"tier\":\"EPIC\",\"skin\":null}").skin());
		assertEquals("", pet("{\"type\":\"WOLF\",\"tier\":\"EPIC\"}").skinItem());
	}

	@Test
	void theLookChangesWhenASkinIsPutOnOrTakenOff() {
		PetIdentity before = pet("{\"type\":\"WOLF\",\"tier\":\"EPIC\",\"uniqueId\":\"a\"}");
		PetIdentity after = pet("{\"type\":\"WOLF\",\"tier\":\"EPIC\",\"uniqueId\":\"a\",\"skin\":\"WOLF_DOGE\"}");
		assertEquals(before.key(), after.key(), "the same pet");
		assertNotEquals(before.look(), after.look(), "so its icon is made again");
	}

	@Test
	void notAPetWithoutAType() {
		assertNull(pet("{\"tier\":\"EPIC\"}"));
	}

	@Test
	void theVariantIndexComesFromTheExtraData() {
		PetIdentity blue = pet("{\"type\":\"X\",\"tier\":\"RARE\",\"skin\":\"CHOICE\",\"extraData\":{\"choice\":1}}");
		assertEquals(1, blue.variant());
		assertEquals("PET_SKIN_CHOICE_BLUE", blue.variantNow(SKINS, 0, true));
		assertEquals(-1, pet("{\"type\":\"X\",\"tier\":\"RARE\",\"skin\":\"CHOICE\",\"extraData\":{\"other\":1}}").variant());
		assertEquals("PET_SKIN_CHOICE", pet("{\"type\":\"X\",\"tier\":\"RARE\",\"skin\":\"CHOICE\",\"extraData\":{\"choice\":7}}").variantNow(SKINS, 0, true),
			"an index the repository doesn't know shows the skin itself");
	}

	@Test
	void seasonalAndDayNightSkinsShowTheVariantOfNow() {
		PetIdentity seasons = pet("{\"type\":\"X\",\"tier\":\"RARE\",\"skin\":\"SEASONS\"}");
		assertEquals("PET_SKIN_SEASONS_SPRING", seasons.variantNow(SKINS, 0, true));
		assertEquals("PET_SKIN_SEASONS_WINTER", seasons.variantNow(SKINS, 3, true));
		PetIdentity time = pet("{\"type\":\"X\",\"tier\":\"RARE\",\"skin\":\"TIME\"}");
		assertEquals("PET_SKIN_TIME_DAY", time.variantNow(SKINS, 0, true));
		assertEquals("PET_SKIN_TIME_NIGHT", time.variantNow(SKINS, 0, false));
		assertEquals("", pet("{\"type\":\"X\",\"tier\":\"RARE\"}").variantNow(SKINS, 0, true), "no skin, no variant");
	}

	@Test
	void anAnimatedSkinShowsItsFirstFrame() {
		assertEquals("FRAME_ONE", SKINS.firstFrame("PET_SKIN_ANIMATED"), "the texture without the profile id in front");
		assertEquals("", SKINS.firstFrame("PET_SKIN_UNKNOWN"));
		assertFalse(SKINS.textures().containsKey("PET_SKIN_BROKEN"), "an odd entry is left out");
	}

	@Test
	void theCalendarAndTheTimeOfDay() {
		long epoch = 1_559_829_300_000L;
		long season = 124L * 60 * 60 * 1000 / 4;
		assertEquals(0, PetIdentity.seasonAt(epoch));
		assertEquals(1, PetIdentity.seasonAt(epoch + season));
		assertEquals(3, PetIdentity.seasonAt(epoch - 1), "the end of the year before");
		assertTrue(PetIdentity.isDay(6000));
		assertFalse(PetIdentity.isDay(18000));
		assertTrue(PetIdentity.isDay(24000L * 5 + 100));
	}

	@Test
	void aPetFromAMessageHasNoSkinAndNoUuid() {
		PetIdentity named = PetIdentity.ofName("Golden Dragon", "LEGENDARY");
		assertEquals("GOLDEN_DRAGON;4", named.petItem());
		assertEquals("GOLDEN_DRAGON;4||-1", named.key());
		assertNull(PetIdentity.ofName(" ", ""));
	}

	@Test
	void raritiesAsTheRepositoryCountsThem() {
		assertEquals(0, PetIdentity.rarityIndex("COMMON"));
		assertEquals(5, PetIdentity.rarityIndex("mythic"));
		assertEquals(0, PetIdentity.rarityIndex("SOMETHING"));
	}

	@Test
	void anEmptyFileGivesNothing() {
		RepoPetSkins none = RepoPetSkins.of(new JsonObject());
		assertTrue(none.textures().isEmpty() && none.variants().isEmpty() && none.variantKeys().isEmpty());
	}
}
