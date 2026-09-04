package io.github.dzkchen.dhen.features.dev

import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.config.ActionSetting
import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.ColorSetting
import io.github.dzkchen.dhen.config.KeybindSetting
import io.github.dzkchen.dhen.config.NumberSetting
import io.github.dzkchen.dhen.config.OrderedSelectionSetting
import io.github.dzkchen.dhen.config.SelectorSetting
import io.github.dzkchen.dhen.config.Setting.Companion.withDependency
import io.github.dzkchen.dhen.config.SoundSetting
import io.github.dzkchen.dhen.config.StringSetting
import io.github.dzkchen.dhen.event.ClientTickEvent
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.ui.hud.HudTextElement
import io.github.dzkchen.dhen.util.Color
import net.minecraft.sounds.SoundEvents
import org.lwjgl.glfw.GLFW

object CompTest : Module(
	name = "Comp Test",
	category = Category.DEV,
	description = "One row of every setting control Dhen draws, plus a HUD element."
) {
	private val toggleSetting = BooleanSetting(
		"Toggle",
		default = true,
		description = "Hides every row under it while it is off."
	)

	private val sliderSetting = NumberSetting(
		"Slider",
		default = 1.0,
		min = 0.1,
		max = 5.0,
		step = 0.1,
		description = "A slider that moves in tenths."
	).withDependency { toggleSetting.on }

	private val textSetting = StringSetting(
		"Text",
		default = "Player123",
		description = "The words the HUD element shows."
	).withDependency { toggleSetting.on }

	private val cycleSetting = SelectorSetting(
		"Cycle",
		default = FIRST,
		options = listOf(FIRST, SECOND, THIRD),
		description = "A pill you click through."
	).withDependency { toggleSetting.on }

	private val dropdownSetting = SelectorSetting(
		"Dropdown",
		default = FIRST,
		options = listOf(FIRST, SECOND, THIRD),
		listed = true,
		description = "A list that opens under the row."
	).withDependency { toggleSetting.on }

	private val orderedSetting = OrderedSelectionSetting(
		"Ordered",
		options = listOf(FIRST, SECOND, THIRD),
		default = listOf(FIRST, SECOND),
		description = "Rows you tick and drag into an order."
	).withDependency { toggleSetting.on }

	private val colorSetting = ColorSetting(
		"Color",
		Color.rgba(85, 255, 255),
		description = "A colour with no alpha slider."
	).withDependency { toggleSetting.on }

	private val translucentColorSetting = ColorSetting(
		"Translucent Color",
		Color.rgba(255, 85, 255, 128),
		allowAlpha = true,
		description = "A colour you can make see-through."
	).withDependency { toggleSetting.on }

	private val keybindSetting = KeybindSetting(
		"Keybind",
		GLFW.GLFW_KEY_P,
		"Says a line in chat when you press it."
	).onPress { Dhen.announce("Comp Test keybind pressed.") }
		.withDependency { toggleSetting.on }

	private val soundSetting = SoundSetting(
		"Sound",
		SoundEvents.UI_BUTTON_CLICK.value(),
		"A sound picked from the sound browser."
	).withDependency { toggleSetting.on }

	private val actionSetting = ActionSetting(
		"Action",
		{ Dhen.announce("Comp Test reads '${textSetting.value}' at ${sliderSetting.amount}.") },
		"Says what the rows above hold."
	).withDependency { toggleSetting.on }

	internal val element = hud(HudTextElement("Comp Test", textSetting.default))

	init {
		registerSetting(toggleSetting)
		registerSetting(sliderSetting)
		registerSetting(textSetting)
		registerSetting(cycleSetting)
		registerSetting(dropdownSetting)
		registerSetting(orderedSetting)
		registerSetting(colorSetting)
		registerSetting(translucentColorSetting)
		registerSetting(keybindSetting)
		registerSetting(soundSetting)
		registerSetting(actionSetting)

		on<ClientTickEvent.End> { follow() }
	}

	private var shownInk = 0

	private fun follow() {
		val shown = textSetting.value
		if (element.text != shown) element.text = shown
		val ink = colorSetting.value.argb
		if (shownInk != ink) {
			shownInk = ink
			element.color = ink
		}
	}

	private const val FIRST = "First"
	private const val SECOND = "Second"
	private const val THIRD = "Third"
}
