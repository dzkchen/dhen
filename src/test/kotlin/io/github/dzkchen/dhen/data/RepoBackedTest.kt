package io.github.dzkchen.dhen.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

internal abstract class RepoBackedTest {
	@TempDir
	lateinit var home: Path

	protected val scope = CoroutineScope(Dispatchers.Unconfined)

	protected val repoRoot: Path get() = home.resolve("repo")

	@AfterEach
	fun uninstallData() {
		DataFixture.uninstall()
	}
}
