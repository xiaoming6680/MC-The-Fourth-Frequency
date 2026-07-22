package com.xm.thefourthfrequency.client_render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.xm.thefourthfrequency.client_ui.WorldInterfaceClientState;
import com.xm.thefourthfrequency.networking.WorldInterfaceProtocol;
import com.xm.thefourthfrequency.networking.WorldInterfaceSnapshotS2C;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Extracts all active gates once and submits them through one additive position-color quad layer. */
public final class WorldInterfaceGatewayBatchRenderer {
	public static final int MAX_GATEWAYS = WorldInterfaceProtocol.MAX_GATEWAYS;
	public static final int RENDER_LAYER_COUNT = 1;
	public static final int MAX_VERTICES_PER_GATE = 16;
	public static final int MAX_BATCH_VERTICES = MAX_GATEWAYS * MAX_VERTICES_PER_GATE;
	public static final double FULL_DETAIL_DISTANCE = 36.0D;
	// Gates sit on a nominal 96-block ring; retain headroom for block rounding and camera offset.
	public static final double RENDER_CUTOFF_DISTANCE = 112.0D;
	private static final double FULL_DETAIL_DISTANCE_SQR = FULL_DETAIL_DISTANCE * FULL_DETAIL_DISTANCE;
	private static final double RENDER_CUTOFF_DISTANCE_SQR = RENDER_CUTOFF_DISTANCE * RENDER_CUTOFF_DISTANCE;
	private static final RenderStateDataKey<GatewayBatch> STATE_KEY = RenderStateDataKey.create(
			() -> "thefourthfrequency:world_interface_gateways");
	private static final GatewayBatch EMPTY = new GatewayBatch(Vec3.ZERO, List.of(), 0, 0, 0);
	private static boolean initialized;

	private WorldInterfaceGatewayBatchRenderer() {
	}

	public static void initialize() {
		if (initialized) return;
		initialized = true;
		WorldRenderEvents.END_EXTRACTION.register(WorldInterfaceGatewayBatchRenderer::extract);
		WorldRenderEvents.BEFORE_TRANSLUCENT.register(WorldInterfaceGatewayBatchRenderer::draw);
	}

	private static void extract(WorldExtractionContext context) {
		FabricRenderState state = (FabricRenderState) context.worldState();
		WorldInterfaceSnapshotS2C encounter = WorldInterfaceClientState.snapshot().encounter();
		if (encounter == null || encounter.gatewayState() == WorldInterfaceProtocol.GatewayState.DORMANT
				|| encounter.gatewayPositions().isEmpty()) {
			state.setData(STATE_KEY, EMPTY);
			return;
		}
		Vec3 camera = context.camera().position();
		long gameTime = context.world().getGameTime();
		int count = Math.min(encounter.gatewayPositions().size(), MAX_GATEWAYS);
		List<GatewayQuad> gates = new ArrayList<>(count);
		for (int index = 0; index < count; index++) {
			BlockPos position = encounter.gatewayPositions().get(index);
			double x = position.getX() + 0.5D;
			double y = position.getY() + 0.35D;
			double z = position.getZ() + 0.5D;
			double deltaX = camera.x - x;
			double deltaZ = camera.z - z;
			double distanceSqr = deltaX * deltaX + deltaZ * deltaZ;
			if (distanceSqr > RENDER_CUTOFF_DISTANCE_SQR) continue;
			boolean fullDetail = distanceSqr <= FULL_DETAIL_DISTANCE_SQR;
			float phase = (gameTime + index * 11L) * 0.085F;
			float pulse = 0.88F + Mth.sin(phase) * 0.12F;
			gates.add(new GatewayQuad(x, y, z, fullDetail, pulse));
		}
		int red = 0;
		int green = 0;
		int blue = 0;
		switch (encounter.gatewayState()) {
			case PURPLE -> {
				red = 190;
				green = 54;
				blue = 255;
			}
			case GOLD -> {
				red = 255;
				green = 204;
				blue = 72;
			}
			case RED -> {
				red = 255;
				green = 42;
				blue = 68;
			}
			case DORMANT -> {
				state.setData(STATE_KEY, EMPTY);
				return;
			}
		}
		state.setData(STATE_KEY, gates.isEmpty() ? EMPTY
				: new GatewayBatch(camera, List.copyOf(gates), red, green, blue));
	}

	private static void draw(WorldRenderContext context) {
		GatewayBatch batch = ((FabricRenderState) context.worldState()).getData(STATE_KEY);
		if (batch == null || batch.gates().isEmpty()) return;
		VertexConsumer vertices = context.consumers().getBuffer(RenderTypes.lightning());
		for (GatewayQuad gate : batch.gates()) drawGate(vertices, batch.camera(), batch, gate);
	}

	private static void drawGate(VertexConsumer vertices, Vec3 camera, GatewayBatch batch, GatewayQuad gate) {
		float x = (float) (gate.x() - camera.x);
		float bottom = (float) (gate.y() - camera.y);
		float z = (float) (gate.z() - camera.z);
		float height = (gate.fullDetail() ? 13.5F : 9.0F) * gate.pulse();
		float top = bottom + height;
		float width = (gate.fullDetail() ? 1.15F : 0.65F) * gate.pulse();
		int alpha = gate.fullDetail() ? 196 : 118;
		quadX(vertices, x, bottom, top, z, width, batch.red(), batch.green(), batch.blue(), alpha);
		quadZ(vertices, x, bottom, top, z, width, batch.red(), batch.green(), batch.blue(), alpha);
		if (!gate.fullDetail()) return;
		float flareBottom = bottom + height * 0.40F;
		float flareTop = bottom + height * 0.60F;
		float flareWidth = width * 2.15F;
		quadX(vertices, x, flareBottom, flareTop, z, flareWidth,
				batch.red(), batch.green(), batch.blue(), 132);
		quadZ(vertices, x, flareBottom, flareTop, z, flareWidth,
				batch.red(), batch.green(), batch.blue(), 132);
	}

	private static void quadX(VertexConsumer vertices, float x, float bottom, float top, float z,
			float width, int red, int green, int blue, int alpha) {
		vertex(vertices, x - width, bottom, z, red, green, blue, alpha);
		vertex(vertices, x + width, bottom, z, red, green, blue, alpha);
		vertex(vertices, x + width, top, z, red, green, blue, alpha);
		vertex(vertices, x - width, top, z, red, green, blue, alpha);
	}

	private static void quadZ(VertexConsumer vertices, float x, float bottom, float top, float z,
			float width, int red, int green, int blue, int alpha) {
		vertex(vertices, x, bottom, z - width, red, green, blue, alpha);
		vertex(vertices, x, bottom, z + width, red, green, blue, alpha);
		vertex(vertices, x, top, z + width, red, green, blue, alpha);
		vertex(vertices, x, top, z - width, red, green, blue, alpha);
	}

	private static void vertex(VertexConsumer vertices, float x, float y, float z,
			int red, int green, int blue, int alpha) {
		vertices.addVertex(x, y, z).setColor(red, green, blue, alpha);
	}

	private record GatewayBatch(Vec3 camera, List<GatewayQuad> gates, int red, int green, int blue) {
	}

	private record GatewayQuad(double x, double y, double z, boolean fullDetail, float pulse) {
	}
}
