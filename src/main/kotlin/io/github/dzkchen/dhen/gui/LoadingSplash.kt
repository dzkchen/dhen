package io.github.dzkchen.dhen.gui

import com.mojang.blaze3d.platform.NativeImage
import io.github.dzkchen.dhen.Dhen
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.texture.MipmapStrategy
import net.minecraft.client.renderer.texture.ReloadableTexture
import net.minecraft.client.renderer.texture.TextureContents
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.ReloadInstance
import net.minecraft.server.packs.resources.ResourceManager
import org.slf4j.LoggerFactory
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Path

internal object LoadingSplash {
	private const val MARK_PATH = "textures/gui/splash_mark.png"
	private const val MARK_JAR_ENTRY = "assets/${Dhen.MOD_ID}/$MARK_PATH"

	private val LOGGER = LoggerFactory.getLogger(Dhen.MOD_ID)
	private val MARK: Identifier = Dhen.id(MARK_PATH)
	private val MARK_METADATA = TextureMetadataSection(false, true, MipmapStrategy.MEAN, 0f)

	private var markReady = false
	private var failed = false
	private var progress = 0f

	@JvmStatic
	fun enabled(): Boolean = !failed && ClientPrefs.splash.value

	@JvmStatic
	fun paint(
		graphics: GuiGraphicsExtractor,
		reload: ReloadInstance,
		fadeOutStart: Long,
		fadeInStart: Long,
		fadesIn: Boolean,
		mouseX: Int,
		mouseY: Int,
		now: Long,
		partialTick: Float
	): Boolean {
		if (!enabled()) return false
		return try {
			val minecraft = Minecraft.getInstance()
			ensureMark(minecraft)
			draw(
				minecraft,
				graphics,
				reload,
				SplashLayout.animation(fadeOutStart, now, SplashLayout.FADE_OUT_MILLIS),
				SplashLayout.animation(fadeInStart, now, SplashLayout.FADE_IN_MILLIS),
				fadesIn,
				mouseX,
				mouseY,
				partialTick
			)
			true
		} catch (throwable: Throwable) {
			failed = true
			LOGGER.error("Loading splash failed, falling back to the vanilla overlay", throwable)
			false
		}
	}

	private fun draw(
		minecraft: Minecraft,
		graphics: GuiGraphicsExtractor,
		reload: ReloadInstance,
		fadeOut: Float,
		fadeIn: Float,
		fadesIn: Boolean,
		mouseX: Int,
		mouseY: Int,
		partialTick: Float
	) {
		val reduced = Effects.reduced
		if (SplashLayout.revealsUnderlay(fadeOut, fadeIn, fadesIn, reduced)) {
			extractUnderlay(minecraft, graphics, fadeOut, mouseX, mouseY, partialTick)
			graphics.nextStratum()
		}

		val width = graphics.guiWidth()
		val height = graphics.guiHeight()
		FlatGui.fill(
			graphics,
			0,
			0,
			width,
			height,
			SplashLayout.withAlpha(DhenPalette.SPLASH_CANVAS, SplashLayout.canvasAlpha(fadeOut, fadeIn, fadesIn, reduced))
		)

		val scale = SplashLayout.markScale(width)
		val markWidth = SplashLayout.MARK_WIDTH * scale
		val markHeight = SplashLayout.MARK_HEIGHT * scale
		val markLeft = SplashLayout.centered(width, markWidth)
		graphics.blit(
			RenderPipelines.GUI_TEXTURED,
			MARK,
			markLeft,
			SplashLayout.centered(height, markHeight),
			0f,
			0f,
			markWidth,
			markHeight,
			SplashLayout.MARK_WIDTH,
			SplashLayout.MARK_HEIGHT,
			SplashLayout.MARK_WIDTH,
			SplashLayout.MARK_HEIGHT,
			GlassGui.withAlpha(
				DhenPalette.SPLASH_INK,
				SplashLayout.contentOpacity(fadeOut, fadeIn, fadesIn, reduced)
			)
		)

		progress = SplashLayout.advanceProgress(progress, reload.actualProgress, reduced)
		drawProgressBar(
			graphics,
			markLeft,
			markWidth,
			SplashLayout.barTop(height),
			SplashLayout.barHeight(scale),
			progress,
			SplashLayout.barOpacity(fadeOut, reduced)
		)

		if (SplashLayout.handedOver(fadeOut)) {
			progress = 0f
			minecraft.gui.setOverlay(null)
		}
	}

	private fun drawProgressBar(
		graphics: GuiGraphicsExtractor,
		left: Int,
		width: Int,
		top: Int,
		height: Int,
		progress: Float,
		opacity: Float
	) {
		if (opacity <= 0f) return
		val bottom = top + height
		FlatGui.fill(graphics, left, top, left + width, bottom, GlassGui.withAlpha(DhenPalette.SPLASH_TRACK, opacity))
		FlatGui.fill(
			graphics,
			left,
			top,
			left + SplashLayout.filledWidth(width, progress),
			bottom,
			GlassGui.withAlpha(DhenPalette.SPLASH_INK, opacity)
		)
	}

	private fun extractUnderlay(
		minecraft: Minecraft,
		graphics: GuiGraphicsExtractor,
		fadeOut: Float,
		mouseX: Int,
		mouseY: Int,
		partialTick: Float
	) {
		val screen = minecraft.gui.screen()
		if (screen == null) {
			minecraft.gui.hud.extractDeferredSubtitles()
			return
		}
		val pointerX = if (fadeOut >= 1f) 0 else mouseX
		val pointerY = if (fadeOut >= 1f) 0 else mouseY
		screen.extractRenderStateWithTooltipAndSubtitles(graphics, pointerX, pointerY, partialTick)
	}

	private fun ensureMark(minecraft: Minecraft) {
		if (markReady) return
		val texture = SplashMark()
		texture.apply(texture.loadContents(minecraft.resourceManager))
		minecraft.textureManager.register(MARK, texture)
		markReady = true
	}

	private fun markPath(): Path = FabricLoader.getInstance()
		.getModContainer(Dhen.MOD_ID)
		.flatMap { it.findPath(MARK_JAR_ENTRY) }
		.orElseThrow { FileNotFoundException(MARK_JAR_ENTRY) }

	private class SplashMark : ReloadableTexture(MARK) {
		override fun loadContents(resourceManager: ResourceManager): TextureContents =
			Files.newInputStream(markPath()).use { TextureContents(NativeImage.read(it), MARK_METADATA) }
	}
}
