package io.github.dzkchen.dhen.gui

import io.github.dzkchen.dhen.config.SelectorSetting

internal object ClickGuiLayout {
	const val COLUMNS = "Columns"
	const val ACCORDION = "Accordion"

	val setting = SelectorSetting(
		"Layout",
		COLUMNS,
		listOf(COLUMNS, ACCORDION),
		"Every category side by side, or a stacked list that opens one at a time."
	)

	val accordion: Boolean
		get() = setting.value == ACCORDION
}
