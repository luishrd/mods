package com.pidinit;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.mojang.brigadier.arguments.StringArgumentType;

public class Hud implements ModInitializer {
	public static final String MOD_ID = "hud";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private int tickCounter = 0;

	@Override
	public void onInitialize() {
		LOGGER.info("HUD mod initializing...");

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(
					Commands.literal("pins").executes(context -> {
						context.getSource().sendSystemMessage(Component.literal("Pin system works!"));
						return 1;
					}));
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(
					Commands.literal("pin")
							.then(Commands.argument("name", StringArgumentType.greedyString())
									.executes(context -> {
										String pinName = StringArgumentType.getString(context, "name");
										context.getSource().sendSystemMessage(
												Component.literal("Creating pin: " + pinName));
										return 1;
									})));
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			tickCounter++;

			if (tickCounter >= 10) {
				tickCounter = 0;

				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					ServerLevel level = player.level();

					int x = (int) player.getX();
					int z = (int) player.getZ();
					int y = (int) player.getY();

					String heading = getCardinalDirection(player.getYRot());

					long dayTime = level.getDayTime() % 24000;

					long ticksUntilBed = (12542 - dayTime + 24000) % 24000;
					long ticksUntilMobs = (13000 - dayTime + 24000) % 24000;
					long ticksUntilDawn = (24000 - dayTime) % 24000;

					String event;
					long ticksUntil;

					if (ticksUntilBed <= ticksUntilMobs && ticksUntilBed <= ticksUntilDawn) {
						event = "BED  in";
						ticksUntil = ticksUntilBed;
					} else if (ticksUntilMobs <= ticksUntilDawn) {
						event = color("MOBS in", Color.RED);
						ticksUntil = ticksUntilMobs;
					} else {
						event = "DAWN in";
						ticksUntil = ticksUntilDawn;
					}

					int minutes = (int) (ticksUntil / 1200);
					int seconds = (int) ((ticksUntil % 1200) / 20);
					String timeString = String.format(color("%02d:%02d", Color.GREY), minutes, seconds);

					String xCoord = color("X: ", Color.GOLD) + color(String.format("%-7d", x), Color.GREY);
					String yCoord = color("Y: ", Color.GOLD) + color(String.format("%-7d", z), Color.GREY);
					String hCoord = color("H: ", Color.GOLD) + color(String.format("%-4d", y), Color.GREY);
					String formattedHeading = color(heading, Color.GREEN);
					String separator = color("◆", Color.GREY);

					String message = String.format("%s %s %s %s %s %s %s %s",
							xCoord, yCoord, hCoord, separator, formattedHeading, separator, event, timeString);

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

	private enum Color {
		GREY("§7"),
		GOLD("§6"),
		BLUE("§9"),
		GREEN("§a"),
		WHITE("§f"),
		RED("§c");

		private final String code;

		Color(String code) {
			this.code = code;
		}
	}

	private String color(String text, Color color) {
		return color.code + text + "§r";
	}

	private String bold(String text) {
		return "§l" + text + "§r";
	}
}