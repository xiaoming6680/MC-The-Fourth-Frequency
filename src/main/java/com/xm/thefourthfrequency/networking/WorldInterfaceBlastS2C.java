package com.xm.thefourthfrequency.networking;

import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.UUID;

/**
 * One detonation, where it happened and how hard.
 *
 * <p><b>Why this exists when {@link BossActionS2C} already carries a clock.</b> Every shake in the
 * encounter used to be derived from the action envelope: the client knows the action, its start tick
 * and its duration, so it can work out on its own which tick a beat lands on, and a packet saying
 * "shake now" would be a second, less reliable clock alongside one that already works. That argument
 * still holds for the beats the envelope describes - the morph reveal, the summon roar - and those
 * are still derived.
 *
 * <p>It does not hold for a blast. The third form runs a volley lane <em>outside</em> the persisted
 * envelope, so several attacks can be in the air that the envelope does not mention at all. The
 * energy bolt is an entity that detonates wherever it happens to hit, which is not on any tick anyone
 * can predict. The laser's contact point walks across the island for two seconds. And an anchor comes
 * down whenever a player decides to cut it. None of that is a function of the envelope, and deriving
 * it was why explosions in this fight landed in silence on the camera - or, worse, shook it a second
 * and a half before the thing arrived.
 *
 * <p>So the rule is: <em>a blast is announced, a beat is derived.</em> The server owns where an
 * explosion happened because the server is what decided; the client owns the falloff, because the
 * server does not need to know where the camera is.
 */
public record WorldInterfaceBlastS2C(
		UUID encounterId,
		double x,
		double y,
		double z,
		float radius,
		int gradeId
) implements CustomPacketPayload {
	public static final Type<WorldInterfaceBlastS2C> TYPE = new Type<>(Identifier.fromNamespaceAndPath(
			TheFourthFrequency.MOD_ID, "world_interface_blast_v1"));
	public static final StreamCodec<RegistryFriendlyByteBuf, WorldInterfaceBlastS2C> CODEC =
			StreamCodec.of(WorldInterfaceBlastS2C::write, WorldInterfaceBlastS2C::read);
	/**
	 * Widest radius a single blast may claim, in blocks.
	 *
	 * <p>Bounded because the radius is the falloff denominator on the receiving side: an unbounded
	 * one is a shake nobody on the island can escape, which is the failure mode this whole feature
	 * has to avoid rather than the effect it is trying to produce.
	 */
	public static final float MAX_RADIUS = 160.0F;

	public WorldInterfaceBlastS2C {
		Objects.requireNonNull(encounterId, "encounterId");
		if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
			throw new IllegalArgumentException("Blast origin must be finite");
		}
		if (!Float.isFinite(radius) || radius <= 0.0F || radius > MAX_RADIUS) {
			throw new IllegalArgumentException("Blast radius must be within (0, " + MAX_RADIUS + "]");
		}
		WorldInterfaceProtocol.BlastGrade.fromWireId(gradeId);
	}

	public WorldInterfaceProtocol.BlastGrade grade() {
		return WorldInterfaceProtocol.BlastGrade.fromWireId(gradeId);
	}

	private static void write(RegistryFriendlyByteBuf buffer, WorldInterfaceBlastS2C value) {
		buffer.writeUUID(value.encounterId);
		buffer.writeDouble(value.x);
		buffer.writeDouble(value.y);
		buffer.writeDouble(value.z);
		buffer.writeFloat(value.radius);
		buffer.writeVarInt(value.gradeId);
	}

	private static WorldInterfaceBlastS2C read(RegistryFriendlyByteBuf buffer) {
		return new WorldInterfaceBlastS2C(buffer.readUUID(), buffer.readDouble(), buffer.readDouble(),
				buffer.readDouble(), buffer.readFloat(), buffer.readVarInt());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
