package com.pidinit;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Hud implements ModInitializer {
	public static final String MOD_ID = "hud";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private int tickCounter = 0;

	@Override
	public void onInitialize() {
		LOGGER.info("HUD mod initializing...");

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			tickCounter++;

			if (tickCounter >= 10) {
				tickCounter = 0;

				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					int x = (int) player.getX();
					int z = (int) player.getZ();
					int y = (int) player.getY();

					String heading = getCardinalDirection(player.getYRot());

					String message = String.format("X:%-7d Y:%-7d H:%-4d ◆ %s", x, z, y, heading);

					player.displayClientMessage(Component.literal(message), true);
				}
			}
		});

		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (entity.getType() == EntityType.WARDEN) {
				entity.discard();
				LOGGER.info("Prevented warden spawn at {}", entity.blockPosition());
			}
		});

		LOGGER.info("HUD mod initialized!");
	}

	private String getCardinalDirection(float yaw) {
		double normalizedYaw = ((yaw % 360) + 360) % 360;

		if (normalizedYaw >= 337.5 || normalizedYaw < 22.5)
			return "S ";
		if (normalizedYaw >= 22.5 && normalizedYaw < 67.5)
			return "SW";
		if (normalizedYaw >= 67.5 && normalizedYaw < 112.5)
			return "W ";
		if (normalizedYaw >= 112.5 && normalizedYaw < 157.5)
			return "NW";
		if (normalizedYaw >= 157.5 && normalizedYaw < 202.5)
			return "N ";
		if (normalizedYaw >= 202.5 && normalizedYaw < 247.5)
			return "NE";
		if (normalizedYaw >= 247.5 && normalizedYaw < 292.5)
			return "E ";
		return "SE";
	}
}