package io.github.dzkchen.dhen.features.privacy

import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import net.minecraft.client.ClientBrandRetriever
import org.slf4j.LoggerFactory

object SpoofAsVanilla : Module(
	name = "Spoof as Vanilla",
	category = Category.PRIVACY,
	description = "Reports the client as unmodded and blocks every mod channel."
) {
	private val log = LoggerFactory.getLogger(Dhen.MOD_ID)

	@Volatile
	private var announced = false

	@JvmStatic
	fun isSpoofing(): Boolean {
		if (!enabled) return false
		if (!announced) {
			announced = true
			log.info("Spoof as Vanilla is reporting the client brand as '{}'", ClientBrandRetriever.VANILLA_NAME)
		}
		return true
	}

	override fun onEnabled() {
		announced = false
		ModWhitelist.spoofingChanged(true)
	}

	override fun onDisabled() {
		ModWhitelist.spoofingChanged(false)
	}
}
