package io.github.dzkchen.dhen.render

import net.minecraft.client.particle.Particle
import net.minecraft.client.particle.SingleQuadParticle
import net.minecraft.core.particles.ParticleType
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier

internal class ParticleRule(val hidden: Boolean, val scale: Float, val alpha: Float, val color: Int)

internal object ParticleOverrides {
	private const val HIDDEN = "off"
	private const val ENTRY_SEPARATOR = " "
	private const val PAIR = '='
	private const val FIELD_SEPARATOR = ","
	private const val COLOR_MARK = "#"
	private const val NO_COLOR = -1
	private const val UNCHANGED = 1.0f
	private const val FAINTEST_VISIBLE = 0.1f
	private const val CHANNEL_MAX = 255.0f
	private const val CHANNEL_MASK = 0xFF
	private const val RED_SHIFT = 16
	private const val GREEN_SHIFT = 8
	private const val COLOR_DIGITS = 6
	private const val HEX = 16

	@Volatile
	var rules: Map<ParticleType<*>, ParticleRule> = emptyMap()

	@JvmStatic
	fun tuned(particle: Particle, type: ParticleType<*>): Particle? {
		val rule = rules[type] ?: return particle
		if (rule.hidden) return null
		if (particle is SingleQuadParticle) {
			if (rule.color != NO_COLOR) {
				particle.setColor(
					(rule.color shr RED_SHIFT and CHANNEL_MASK) / CHANNEL_MAX,
					(rule.color shr GREEN_SHIFT and CHANNEL_MASK) / CHANNEL_MAX,
					(rule.color and CHANNEL_MASK) / CHANNEL_MAX
				)
			}
			val fading = particle as ParticleAlpha
			if (rule.alpha > FAINTEST_VISIBLE && rule.alpha < fading.dhenParticleAlpha()) {
				fading.dhenFadeParticle(rule.alpha)
			}
		}
		return if (rule.scale == UNCHANGED) particle else particle.scale(rule.scale)
	}

	fun read(raw: String): Map<ParticleType<*>, ParticleRule> {
		val parsed = LinkedHashMap<ParticleType<*>, ParticleRule>()
		for (entry in raw.split(ENTRY_SEPARATOR)) {
			val split = entry.indexOf(PAIR)
			if (split <= 0 || split == entry.length - 1) continue
			val id = Identifier.tryParse(entry.substring(0, split).lowercase()) ?: continue
			val type = BuiltInRegistries.PARTICLE_TYPE.getValue(id) ?: continue
			rule(entry.substring(split + 1))?.let { parsed[type] = it }
		}
		return parsed
	}

	private fun rule(raw: String): ParticleRule? {
		if (raw.equals(HIDDEN, ignoreCase = true)) return ParticleRule(true, UNCHANGED, UNCHANGED, NO_COLOR)
		val fields = raw.split(FIELD_SEPARATOR)
		if (fields.size > 3) return null
		val scale = fields[0].toFloatOrNull() ?: return null
		val alpha = if (fields.size > 1) fields[1].toFloatOrNull() ?: return null else UNCHANGED
		val color = if (fields.size > 2) color(fields[2]) ?: return null else NO_COLOR
		return ParticleRule(false, scale, alpha, color)
	}

	private fun color(raw: String): Int? {
		val digits = raw.removePrefix(COLOR_MARK)
		return if (digits.length == COLOR_DIGITS) digits.toIntOrNull(HEX) else null
	}
}
