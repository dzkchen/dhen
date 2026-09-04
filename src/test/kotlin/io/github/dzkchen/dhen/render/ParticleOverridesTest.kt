package io.github.dzkchen.dhen.render

import io.github.dzkchen.dhen.bootstrapMinecraft
import net.minecraft.core.particles.ParticleTypes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class ParticleOverridesTest {
	@Test
	fun `off hides the type`() {
		val rule = requireNotNull(ParticleOverrides.read("flame=off")[ParticleTypes.FLAME])
		assertTrue(rule.hidden)
	}

	@Test
	fun `a namespaced key and a bare key name the same type`() {
		assertEquals(
			ParticleOverrides.read("crit=2").keys,
			ParticleOverrides.read("minecraft:CRIT=2").keys
		)
	}

	@Test
	fun `scale, opacity and colour are read in that order`() {
		val rule = requireNotNull(ParticleOverrides.read("dust=0.5,0.6,#66FFFF")[ParticleTypes.DUST])
		assertEquals(0.5f, rule.scale)
		assertEquals(0.6f, rule.alpha)
		assertEquals(0x66FFFF, rule.color)
	}

	@Test
	fun `a missing field keeps the value unchanged`() {
		val rule = requireNotNull(ParticleOverrides.read("crit=3")[ParticleTypes.CRIT])
		assertEquals(1f, rule.alpha)
		assertEquals(-1, rule.color)
	}

	@Test
	fun `entries that cannot be read are dropped rather than half applied`() {
		assertTrue(ParticleOverrides.read("crit=abc").isEmpty())
		assertTrue(ParticleOverrides.read("crit=1,abc").isEmpty())
		assertTrue(ParticleOverrides.read("crit=1,0.5,3").isEmpty())
		assertTrue(ParticleOverrides.read("crit=1,0.5,#66FFFF,4").isEmpty())
		assertTrue(ParticleOverrides.read("crit").isEmpty())
		assertTrue(ParticleOverrides.read("crit=").isEmpty())
		assertTrue(ParticleOverrides.read("=2").isEmpty())
		assertTrue(ParticleOverrides.read("").isEmpty())
	}

	@Test
	fun `an unknown particle name is skipped without losing the rest of the line`() {
		val rules = ParticleOverrides.read("not_a_particle=off flame=off")
		assertEquals(1, rules.size)
		assertNull(rules[ParticleTypes.CRIT])
		assertNotNull(rules[ParticleTypes.FLAME])
	}

	private companion object {
		@JvmStatic
		@BeforeAll
		fun bootstrap() {
			bootstrapMinecraft()
		}
	}
}
