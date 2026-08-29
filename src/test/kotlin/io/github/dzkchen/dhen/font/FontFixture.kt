package io.github.dzkchen.dhen.font

import java.nio.file.Files
import java.nio.file.Path

internal object FontFixture {
	val TRUETYPE_TAG = byteArrayOf(0, 1, 0, 0)

	private const val PADDING = 64

	fun folder(configRoot: Path): Path = Files.createDirectories(configRoot.resolve(FontStore.DIRECTORY))

	fun truetype(configRoot: Path, name: String) = write(configRoot, name, TRUETYPE_TAG + ByteArray(PADDING))

	fun face(configRoot: Path, name: String): FontFace {
		truetype(configRoot, name)
		return FontStore.refresh().single { it.name == name }
	}

	fun write(configRoot: Path, name: String, bytes: ByteArray) {
		FontStore.install(configRoot)
		Files.write(folder(configRoot).resolve(name + FontStore.EXTENSION), bytes)
	}

	fun sidecar(configRoot: Path, name: String, text: String) {
		Files.writeString(folder(configRoot).resolve(name + FontStore.SIDECAR), text)
	}
}
