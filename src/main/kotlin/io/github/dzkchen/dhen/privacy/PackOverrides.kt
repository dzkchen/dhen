package io.github.dzkchen.dhen.privacy

import com.google.common.collect.ImmutableMultimap
import com.mojang.authlib.GameProfile
import com.mojang.authlib.properties.Property
import com.mojang.authlib.properties.PropertyMap
import io.github.dzkchen.dhen.data.item.SkyBlockItem
import io.github.dzkchen.dhen.data.item.SkyBlockItems
import io.github.dzkchen.dhen.data.repo.PackModelRepo
import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.Resource
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ResolvableProfile
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function

internal object PackOverrides {
	const val QUIVER_ARROW = "quiver_arrow"
	const val GLINT_ON = "on"
	const val GLINT_OFF = "off"

	private const val PAIR = '='
	private const val ENTRY_SEPARATOR = " "
	private const val PACK_NAMESPACE = "hypixel_skyblock"
	private const val ATTUNE_MODE = "td_attune_mode"
	private const val FUNGI_MODE = "fungi_cutter_mode"
	private const val TEXTURES = "textures"
	private const val PROFILE_SEED = "dhen:"
	private const val NO_ATTUNEMENT = -1
	private const val FIRE_ATTUNEMENT = 1
	private const val MAW_ATTUNEMENT = 3
	private const val RED = "RED"
	private const val BROWN = "BROWN"

	private val FIRE_DAGGERS = setOf("FIREDUST_DAGGER", "BURSTFIRE_DAGGER", "HEARTFIRE_DAGGER")
	private val MAW_DAGGERS = setOf("MAWDUST_DAGGER", "BURSTMAW_DAGGER", "HEARTMAW_DAGGER")
	private val KATANAS = setOf("VOIDEDGE_KATANA", "VORPAL_KATANA", "ATOMSPLIT_KATANA")
	private val FUNGI_CUTTERS = setOf("FUNGI_CUTTER", "FUNGI_CUTTER_2", "FUNGI_CUTTER_3")

	private val TEXT_SHADERS = setOf(
		"shaders/core/text.vsh",
		"shaders/core/rendertype_text.vsh",
		"shaders/core/rendertype_text_see_through.vsh",
		"shaders/core/rendertype_text_intensity.vsh",
		"shaders/core/rendertype_text_intensity_see_through.vsh",
		"shaders/include/modify_vanilla_color.glsl",
		"shaders/include/color_util.glsl"
	)

	private val profiles = ConcurrentHashMap<String, ResolvableProfile>()

	private val arrowModel by lazy(LazyThreadSafetyMode.NONE) { vanillaModel(Items.ARROW) }
	private val goldenSword by lazy(LazyThreadSafetyMode.NONE) { vanillaModel(Items.GOLDEN_SWORD) }
	private val diamondSword by lazy(LazyThreadSafetyMode.NONE) { vanillaModel(Items.DIAMOND_SWORD) }
	private val redMushroom by lazy(LazyThreadSafetyMode.NONE) { vanillaModel(Items.RED_MUSHROOM) }
	private val brownMushroom by lazy(LazyThreadSafetyMode.NONE) { vanillaModel(Items.BROWN_MUSHROOM) }

	@Volatile
	var onCooldown: (ItemStack) -> Boolean = { Minecraft.getInstance().player?.cooldowns?.isOnCooldown(it) == true }

	@Volatile
	var reverting: Boolean = false

	@Volatile
	var packTextShaders: Boolean? = null

	@Volatile
	var whitelist: Set<String> = emptySet()

	@Volatile
	var replacements: Map<String, Identifier> = emptyMap()

	@Volatile
	var glints: Map<String, Boolean> = emptyMap()

	fun identity(stack: ItemStack): String? {
		if (stack.isEmpty) return null
		return identityOf(SkyBlockItems.of(stack))
	}

	fun model(stack: ItemStack, current: Identifier?): Identifier? {
		if (!reverting || current == null || stack.isEmpty) return current
		val item = SkyBlockItems.of(stack)
		val id = identityOf(item) ?: return current
		replacements[id]?.let { return it }
		if (current.namespace != PACK_NAMESPACE || id in whitelist) return current
		if (id == QUIVER_ARROW) return arrowModel ?: current
		val reverted = PackModelRepo.table.model(id) ?: return current
		return dynamic(item, stack, reverted)
	}

	private fun identityOf(item: SkyBlockItem): String? =
		if (item.hasQuiverArrow) QUIVER_ARROW else item.id.ifEmpty { null }

	fun foil(stack: ItemStack, current: Boolean): Boolean {
		val forced = glints
		if (!reverting || forced.isEmpty() || stack.isEmpty) return current
		return forced[identity(stack) ?: return current] ?: current
	}

	fun headProfile(stack: ItemStack, current: ResolvableProfile?): ResolvableProfile? {
		if (!reverting || stack.isEmpty || stack.`is`(Items.PLAYER_HEAD)) return current
		val item = SkyBlockItems.of(stack)
		if (item.id.isEmpty()) return current
		return dashed(item.helmetSkin)?.let(::cachedProfile) ?: cachedProfile(item.id) ?: current
	}

	@JvmStatic
	fun selectTextShaders(
		listed: Map<Identifier, Resource>,
		stack: Function<Identifier, List<Resource>>
	): Map<Identifier, Resource>? {
		val preferPack = packTextShaders ?: return null
		var selected: MutableMap<Identifier, Resource>? = null
		for (location in listed.keys) {
			if (location.namespace != VANILLA_NAMESPACE || location.path !in TEXT_SHADERS) continue
			val candidates = stack.apply(location)
			val chosen = if (preferPack) {
				candidates.firstOrNull(::isServerPack) ?: candidates.lastOrNull()
			} else {
				candidates.lastOrNull { !isServerPack(it) }
			}
			val into = selected ?: LinkedHashMap(listed).also { selected = it }
			if (chosen == null) into.remove(location) else into[location] = chosen
		}
		return selected
	}

	private fun isServerPack(resource: Resource): Boolean = LanguageKeys.isServerPackId(resource.sourcePackId())

	fun readReplacements(raw: String): Map<String, Identifier> {
		val parsed = LinkedHashMap<String, Identifier>()
		for ((id, target) in pairs(raw)) {
			PackModelRepo.itemModel(target)?.let { parsed[id] = it }
		}
		return parsed
	}

	fun readGlints(raw: String): Map<String, Boolean> {
		val parsed = LinkedHashMap<String, Boolean>()
		for ((id, state) in pairs(raw)) {
			when (state) {
				GLINT_ON -> parsed[id] = true
				GLINT_OFF -> parsed[id] = false
			}
		}
		return parsed
	}

	private fun pairs(raw: String): List<Pair<String, String>> = raw.split(ENTRY_SEPARATOR).mapNotNull { entry ->
		val split = entry.indexOf(PAIR)
		if (split <= 0 || split == entry.length - 1) null
		else key(entry.substring(0, split)) to entry.substring(split + 1).lowercase()
	}

	private fun key(raw: String): String =
		if (raw.equals(QUIVER_ARROW, ignoreCase = true)) QUIVER_ARROW else raw.uppercase()

	internal fun forgetProfiles() {
		profiles.clear()
	}

	private fun dynamic(item: SkyBlockItem, stack: ItemStack, fallback: Identifier): Identifier = when (item.id) {
		in FIRE_DAGGERS -> attuned(item, FIRE_ATTUNEMENT, goldenSword, fallback)
		in MAW_DAGGERS -> attuned(item, MAW_ATTUNEMENT, diamondSword, fallback)
		in KATANAS -> if (onCooldown(stack)) goldenSword ?: fallback else fallback

		in FUNGI_CUTTERS -> when (item.tag.getStringOr(FUNGI_MODE, "")) {
			RED -> redMushroom ?: fallback
			BROWN -> brownMushroom ?: fallback
			else -> fallback
		}

		else -> fallback
	}

	private fun attuned(item: SkyBlockItem, mode: Int, attuned: Identifier?, fallback: Identifier): Identifier =
		if (item.tag.getIntOr(ATTUNE_MODE, NO_ATTUNEMENT) == mode) attuned ?: fallback else fallback


	private fun cachedProfile(id: String): ResolvableProfile? {
		profiles[id]?.let { return it }
		val texture = PackModelRepo.table.texture(id) ?: return null
		return profiles.computeIfAbsent(id) { profile(id, texture) }
	}

	private fun profile(id: String, texture: String): ResolvableProfile {
		val properties = PropertyMap(ImmutableMultimap.of(TEXTURES, Property(TEXTURES, texture)))
		return ResolvableProfile.createResolved(
			GameProfile(UUID.nameUUIDFromBytes((PROFILE_SEED + id).toByteArray()), id, properties)
		)
	}

	private fun dashed(raw: String): String? {
		if (raw.isEmpty()) return null
		return if (raw.indexOf(':') < 0) raw else raw.replace(':', '-')
	}

	private fun vanillaModel(item: Item): Identifier? = item.components().get(DataComponents.ITEM_MODEL)

	private const val VANILLA_NAMESPACE = "minecraft"
}
