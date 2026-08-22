package io.github.dzkchen.dhen.render

import com.mojang.blaze3d.pipeline.RenderPipeline
import io.github.dzkchen.dhen.Dhen
import net.fabricmc.loader.api.FabricLoader
import net.irisshaders.iris.api.v0.IrisApi
import net.irisshaders.iris.api.v0.IrisProgram
import org.slf4j.LoggerFactory

internal enum class IrisShaderProgram { LINES, BASIC }

internal object IrisCompat {
	private const val IRIS_MOD_ID = "iris"

	private val assignment: PipelineAssignment by lazy {
		if (FabricLoader.getInstance().isModLoaded(IRIS_MOD_ID)) IrisPresent else IrisAbsent
	}

	fun assignOnce(pipeline: RenderPipeline, program: IrisShaderProgram) {
		try {
			assignment.assign(pipeline, program)
		} catch (throwable: Throwable) {
			log.warn("Could not hand {} to Iris; it will draw unshaded under a shader pack", pipeline.location, throwable)
		}
	}

	private interface PipelineAssignment {
		fun assign(pipeline: RenderPipeline, program: IrisShaderProgram) = Unit
	}

	private object IrisAbsent : PipelineAssignment

	private object IrisPresent : PipelineAssignment {
		override fun assign(pipeline: RenderPipeline, program: IrisShaderProgram) =
			IrisApi.getInstance().assignPipeline(pipeline, program.irisProgram())

		private fun IrisShaderProgram.irisProgram() = when (this) {
			IrisShaderProgram.LINES -> IrisProgram.LINES
			IrisShaderProgram.BASIC -> IrisProgram.BASIC
		}
	}

	private val log = LoggerFactory.getLogger(Dhen.MOD_ID)
}
