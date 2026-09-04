package io.github.dzkchen.dhen.features.visual

import com.mojang.blaze3d.vertex.PoseStack
import io.github.dzkchen.dhen.config.NumberSetting
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.entity.state.AvatarRenderState

object PlayerSize : Module(
	name = "Player Size",
	category = Category.VISUAL,
	description = "Changes the size of your own player model."
) {
	internal val sizeXSetting = NumberSetting(
		"Size X",
		1.0,
		-1.0,
		3.0,
		0.1,
		description = "Width of your player model."
	)
	internal val sizeYSetting = NumberSetting(
		"Size Y",
		1.0,
		-1.0,
		3.0,
		0.1,
		description = "Height of your player model. A negative value stands it on its head."
	)
	internal val sizeZSetting = NumberSetting(
		"Size Z",
		1.0,
		-1.0,
		3.0,
		0.1,
		description = "Depth of your player model."
	)

	private var sizeX by sizeXSetting
	private var sizeY by sizeYSetting
	private var sizeZ by sizeZSetting

	@JvmStatic
	fun scaleOwnAvatar(state: AvatarRenderState, pose: PoseStack) {
		if (!enabled) return
		val player = Minecraft.getInstance().player ?: return
		if (state.id != player.id) return
		val height = sizeY.toFloat()
		if (height < 0f) pose.translate(0f, height * UPSIDE_DOWN_LIFT, 0f)
		pose.scale(sizeX.toFloat(), height, sizeZ.toFloat())
	}

	private const val UPSIDE_DOWN_LIFT = 2f
}
