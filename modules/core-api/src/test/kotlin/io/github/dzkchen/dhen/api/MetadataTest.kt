package io.github.dzkchen.dhen.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.net.URI

class MetadataTest {
	@Test
	fun addonMetadataCapturesDisplayAndDependencyFields() {
		val metadata = AddonMetadata(
			id = AddonId("sample.addon"),
			name = "Sample Addon",
			version = "1.2.3",
			authors = listOf("Ada"),
			description = "Example addon metadata.",
			requiredDhenApi = "[1.0,2.0)",
			artifactType = AddonArtifactType.NATIVE_JAR,
			sourceUrl = URI("https://example.com/addons/sample"),
			minecraftVersionRange = "[26.2,27.0)",
			fabricLoaderVersionRange = "[0.19,1.0)",
			depends = listOf(AddonDependency(AddonId("sample.library"), "[1.0,2.0)")),
			recommends = listOf(AddonDependency(AddonId("sample.optional"))),
			breaks = listOf(AddonDependency(AddonId("sample.conflict"), reason = "Conflicting HUD renderer")),
			loadAfter = listOf(AddonId("sample.library")),
			providedModules = listOf(ModuleId("sample.addon:demo")),
			releaseNotes = "Initial release.",
		)

		assertEquals("Native JAR", metadata.artifactType.displayName)
		assertEquals(listOf("Ada"), metadata.authors)
		assertEquals("[1.0,2.0)", metadata.requiredApiRange)
		assertEquals(VersionRange.parse("[1.0,2.0)"), metadata.parsedRequiredApiRange)
		assertEquals(VersionRange.parse("[26.2,27.0)"), metadata.parsedMinecraftVersionRange)
		assertEquals(VersionRange.parse("[0.19,1.0)"), metadata.parsedFabricLoaderVersionRange)
		assertEquals(VersionRange.parse("[1.0,2.0)"), metadata.depends.single().parsedVersionRange)
		assertEquals(listOf(AddonId("sample.library")), metadata.dependencies.map { it.id })
		assertEquals(listOf(AddonId("sample.conflict")), metadata.conflicts.map { it.id })
		assertEquals(listOf(ModuleId("sample.addon:demo")), metadata.providedModules)
	}

	@Test
	fun addonMetadataRejectsInvalidValuesWithoutMinecraft() {
		assertThrows(IllegalArgumentException::class.java) {
			AddonMetadata(AddonId("sample.addon"), "", "1.0.0")
		}
		assertThrows(IllegalArgumentException::class.java) {
			AddonMetadata(AddonId("sample.addon"), "Sample", "1.0.0", sourceUrl = URI("example.com/addon"))
		}
		assertThrows(IllegalArgumentException::class.java) {
			AddonMetadata(
				id = AddonId("sample.addon"),
				name = "Sample",
				version = "1.0.0",
				providedModules = listOf(ModuleId("other.addon:demo")),
			)
		}
		assertThrows(IllegalArgumentException::class.java) {
			AddonMetadata(
				id = AddonId("sample.addon"),
				name = "Sample",
				version = "1.0.0",
				depends = listOf(AddonDependency(AddonId("sample.library")), AddonDependency(AddonId("sample.library"))),
			)
		}
		assertThrows(IllegalArgumentException::class.java) {
			AddonMetadata(AddonId("sample.addon"), "Sample", "1.0.0", requiredDhenApi = "1.0,2.0")
		}
	}

	@Test
	fun moduleMetadataCanFullyDescribeAFakeModule() {
		val metadata = ModuleMetadata(
			id = ModuleId("sample.addon:demo"),
			name = "Demo",
			description = "Full metadata fixture.",
			category = ModuleCategory.DUNGEONS,
			settings = listOf(
				BooleanSetting(SettingId("enabled"), "Enabled", default = true),
				EnumSetting(SettingId("mode"), "Mode", values = listOf("compact", "full")),
				IntRangeSetting(SettingId("scale"), "Scale", min = 1, max = 4),
				FloatRangeSetting(SettingId("opacity"), "Opacity", min = 0.0, max = 1.0),
				StringSetting(SettingId("label"), "Label"),
				ColorSetting(SettingId("color"), "Color", default = 0x112233),
				KeybindSetting(SettingId("open"), "Open", default = -1),
				ListSetting(SettingId("filters"), "Filters", StringValueSchema()),
				ObjectSetting(
					SettingId("advanced"),
					"Advanced",
					fields = listOf(BooleanSetting(SettingId("debug"), "Debug")),
				),
			),
			conflicts = listOf(ModuleId("sample.addon:old_demo")),
			optionalDependencies = listOf(ModuleId("sample.library:helper")),
			eventSubscriptions = listOf(EventSubscription("client_tick")),
			hudWidgets = listOf(HudWidgetSpec(WidgetId("map"), "Map Overlay")),
			keybinds = listOf(KeybindSpec(KeybindId("open_map"), "Open Map", defaultKey = 77)),
			commands = listOf(CommandSpec("dhen map", "Open map controls")),
		)

		assertEquals("Dungeons", metadata.category.displayName)
		assertEquals(metadata.settings, metadata.configSchema)
		assertEquals(9, metadata.settings.size)
		assertEquals(SettingId("enabled"), metadata.settings.first().id)
		assertEquals(WidgetId("map"), metadata.hudWidgets.single().id)
		assertEquals(KeybindId("open_map"), metadata.keybinds.single().id)
		assertEquals("dhen map", metadata.commands.single().path)
	}

	@Test
	fun moduleMetadataRejectsDuplicateLocalIds() {
		assertThrows(IllegalArgumentException::class.java) {
			ModuleMetadata(
				id = ModuleId("sample.addon:demo"),
				name = "Demo",
				category = ModuleCategory.CHAT,
				settings = listOf(
					BooleanSetting(SettingId("enabled"), "Enabled"),
					BooleanSetting(SettingId("enabled"), "Enabled again"),
				),
			)
		}
	}

	@Test
	fun configSchemasRejectInvalidDeclarations() {
		assertThrows(IllegalArgumentException::class.java) {
			EnumSetting(SettingId("mode"), "Mode", values = listOf("compact", "compact"))
		}
		assertThrows(IllegalArgumentException::class.java) {
			EnumSetting(SettingId("mode"), "Mode", values = listOf("compact"), default = "full")
		}
		assertThrows(IllegalArgumentException::class.java) {
			IntRangeSetting(SettingId("scale"), "Scale", min = 4, max = 1)
		}
		assertThrows(IllegalArgumentException::class.java) {
			IntRangeSetting(SettingId("scale"), "Scale", min = 1, max = 4, default = 5)
		}
		assertThrows(IllegalArgumentException::class.java) {
			FloatRangeSetting(SettingId("opacity"), "Opacity", min = Double.NaN, max = 1.0)
		}
		assertThrows(IllegalArgumentException::class.java) {
			FloatRangeSetting(SettingId("opacity"), "Opacity", min = 0.0, max = 1.0, default = 2.0)
		}
		assertThrows(IllegalArgumentException::class.java) {
			ColorSetting(SettingId("color"), "Color", default = 0x1000000)
		}
		assertThrows(IllegalArgumentException::class.java) {
			KeybindSetting(SettingId("open"), "Open", default = -2)
		}
		assertThrows(IllegalArgumentException::class.java) {
			ListSetting(SettingId("filters"), "Filters", IntRangeValueSchema(1, 3), default = listOf(4))
		}
		assertThrows(IllegalArgumentException::class.java) {
			ObjectSetting(
				SettingId("advanced"),
				"Advanced",
				fields = listOf(
					BooleanSetting(SettingId("debug"), "Debug"),
					BooleanSetting(SettingId("debug"), "Debug again"),
				),
			)
		}
	}
}
