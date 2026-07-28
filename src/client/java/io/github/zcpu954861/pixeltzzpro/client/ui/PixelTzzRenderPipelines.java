package io.github.zcpu954861.pixeltzzpro.client.ui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import io.github.zcpu954861.pixeltzzpro.PixelTzzPro;
import net.minecraft.client.renderer.RenderPipelines;

/**
 * Small client-only pipelines shared by the reusable Pixel TZZ UI components.
 */
public final class PixelTzzRenderPipelines {
	public static final RenderPipeline GUI_TEXTURED_GRAYSCALE = RenderPipelines.register(
		RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
			.withLocation(PixelTzzPro.id("pipeline/gui_textured_grayscale"))
			.withFragmentShader(PixelTzzPro.id("core/gui_textured_grayscale"))
			.build()
	);

	private PixelTzzRenderPipelines() {
	}

	public static void register() {
		// Loads this class before the first client resource compilation pass.
	}
}
