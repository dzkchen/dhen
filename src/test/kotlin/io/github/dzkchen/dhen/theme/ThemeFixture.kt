package io.github.dzkchen.dhen.theme

import java.nio.file.Files
import java.nio.file.Path

internal object ThemeFixture {
	fun folder(configRoot: Path): Path = configRoot.resolve(ThemeStore.DIRECTORY)

	fun write(configRoot: Path, id: String, manifest: String) {
		val folder = Files.createDirectories(folder(configRoot).resolve(id))
		Files.writeString(folder.resolve(ThemeFormat.MANIFEST), manifest)
	}

	fun forgetDiscovered(configRoot: Path) {
		ThemeStore.refresh(configRoot.resolve("no-such-config-root"))
	}
}
