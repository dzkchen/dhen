package io.github.dzkchen.dhen.gui

import io.github.dzkchen.dhen.config.SelectorSetting

internal object ClickGuiKeys {
	const val EXPAND_ROWS = "Expand rows"
	const val JUMP_COLUMNS = "Jump columns"

	val setting = SelectorSetting(
		"Arrow keys",
		EXPAND_ROWS,
		listOf(EXPAND_ROWS, JUMP_COLUMNS),
		"What Left and Right do to the focused row: open its settings, or move to the next category. Tab opens settings either way."
	)

	val jumpColumns: Boolean
		get() = setting.value == JUMP_COLUMNS
}
