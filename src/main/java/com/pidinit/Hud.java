package com.pidinit;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Path;

public class Hud implements ModInitializer {
	public static final String MOD_ID = "hud";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private int tickCounter = 0;

	private static class PinData {
		final int x, y, z;
		final String dimension;

		PinData(int x, int y, int z, String dimension) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.dimension = dimension;
		}
	}

	private static class NavigationDisplay {
		final String indicator;
		final int distance;

		NavigationDisplay(String indicator, int distance) {
			this.indicator = indicator;
			this.distance = distance;
		}
	}

	private final Map<String, Map<String, Map<String, PinData>>> pins = new ConcurrentHashMap<>();
	private final Map<String, String> activeNavigationTarget = new ConcurrentHashMap<>();

	@Override
	public void onInitialize() {
		LOGGER.info("HUD mod initializing...");

		// pins
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(
					Commands.literal("pins").executes(context -> {
						ServerPlayer player = context.getSource().getPlayerOrException();
						String playerUUID = player.getStringUUID();
						String currentDimension = player.level().dimension().identifier().toString();

						Map<String, Map<String, PinData>> playerPins = pins.get(playerUUID);

						if (playerPins == null || !playerPins.containsKey(currentDimension)) {
							context.getSource().sendSystemMessage(
									Component.literal("No pins in this dimension"));
							return 1;
						}

						Map<String, PinData> dimensionPins = playerPins.get(currentDimension);

						if (dimensionPins.isEmpty()) {
							context.getSource().sendSystemMessage(
									Component.literal("No pins in this dimension"));
							return 1;
						}

						context.getSource().sendSystemMessage(
								Component.literal("=== Your Pins ==="));

						for (Map.Entry<String, PinData> entry : dimensionPins.entrySet()) {
							PinData pin = entry.getValue();
							context.getSource().sendSystemMessage(
									Component.literal(String.format("  %s: X:%d Y:%d Z:%d",
											entry.getKey(), pin.x, pin.y, pin.z)));
						}

						return 1;
					}));
		});

		// pin remove name
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(
					Commands.literal("pin")
							.then(Commands.literal("remove")
									.then(Commands.argument("name", StringArgumentType.greedyString())
											.executes(context -> {
												String pinName = StringArgumentType.getString(context, "name");
												ServerPlayer player = context.getSource().getPlayerOrException();
												String playerUUID = player.getStringUUID();
												String dimension = player.level().dimension().identifier().toString();

												Map<String, Map<String, PinData>> playerPins = pins.get(playerUUID);

												if (playerPins == null || !playerPins.containsKey(dimension)) {
													context.getSource().sendSystemMessage(
															Component.literal("Pin '" + pinName + "' not found"));
													return 0;
												}

												Map<String, PinData> dimensionPins = playerPins.get(dimension);
												PinData removed = dimensionPins.remove(pinName);

												if (removed == null) {
													context.getSource().sendSystemMessage(
															Component.literal("Pin '" + pinName + "' not found"));
													return 0;
												}

												savePins(player.level(), playerUUID);

												context.getSource().sendSystemMessage(
														Component.literal("✓ Pin '" + pinName + "' removed"));
												return 1;
											}))));
		});

		// pin name
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(
					Commands.literal("pin")
							.then(Commands.argument("name", StringArgumentType.greedyString())
									.executes(context -> {
										String pinName = StringArgumentType.getString(context, "name");

										ServerPlayer player = context.getSource().getPlayerOrException();
										BlockPos pos = player.blockPosition();
										String dimension = player.level().dimension().identifier().toString();
										String playerUUID = player.getStringUUID();

										pins.computeIfAbsent(playerUUID, k -> new ConcurrentHashMap<>())
												.computeIfAbsent(dimension, k -> new ConcurrentHashMap<>())
												.put(pinName,
														new PinData(pos.getX(), pos.getY(), pos.getZ(), dimension));

										savePins(player.level(), playerUUID);

										context.getSource().sendSystemMessage(
												Component.literal(String.format("✓ Pin '%s' saved at X:%d Y:%d Z:%d",
														pinName, pos.getX(), pos.getY(), pos.getZ())));
										return 1;
									})));
		});

		// go name
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(
					Commands.literal("go")
							.then(Commands.argument("name", StringArgumentType.greedyString())
									.executes(context -> {
										String pinName = StringArgumentType.getString(context, "name");
										ServerPlayer player = context.getSource().getPlayerOrException();
										String playerUUID = player.getStringUUID();
										String dimension = player.level().dimension().identifier().toString();

										Map<String, Map<String, PinData>> playerPins = pins.get(playerUUID);

										if (playerPins == null || !playerPins.containsKey(dimension)) {
											context.getSource().sendSystemMessage(
													Component.literal(
															"Pin '" + pinName + "' not found in this dimension"));
											return 0;
										}

										PinData pin = playerPins.get(dimension).get(pinName);

										if (pin == null) {
											context.getSource().sendSystemMessage(
													Component.literal("Pin '" + pinName + "' not found"));
											return 0;
										}

										activeNavigationTarget.put(playerUUID, pinName);

										context.getSource().sendSystemMessage(
												Component.literal("✓ Navigating to '" + pinName + "'"));
										return 1;
									})));
		});

		// go stop
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(
					Commands.literal("go")
							.then(Commands.literal("stop")
									.executes(context -> {
										ServerPlayer player = context.getSource().getPlayerOrException();
										String playerUUID = player.getStringUUID();

										if (activeNavigationTarget.remove(playerUUID) != null) {
											context.getSource().sendSystemMessage(
													Component.literal("✓ Navigation stopped"));
											return 1;
										} else {
											context.getSource().sendSystemMessage(
													Component.literal("No active navigation"));
											return 0;
										}
									})));
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			tickCounter++;

			if (tickCounter >= 10) {
				tickCounter = 0;

				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					ServerLevel level = player.level();
					BlockPos position = player.blockPosition();

					int x = position.getX();
					int z = position.getZ();
					int y = position.getY();

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

					String message;
					String navTarget = activeNavigationTarget.get(player.getStringUUID());

					if (navTarget != null) {
						Map<String, Map<String, PinData>> playerPins = pins.get(player.getStringUUID());
						String currentDim = player.level().dimension().identifier().toString();

						if (playerPins != null) {
							PinData targetPin = null;

							for (Map<String, PinData> dimensionPins : playerPins.values()) {
								if (dimensionPins.containsKey(navTarget)) {
									targetPin = dimensionPins.get(navTarget);
									break;
								}
							}

							if (targetPin != null) {
								NavigationDisplay nav = getNavigationDisplay(player.getYRot(), player.blockPosition(),
										targetPin, currentDim);
								String navSection;

								if (!targetPin.dimension.equals(currentDim)) {
									String dimName = getDimensionDisplayName(targetPin.dimension);
									navSection = color(nav.indicator, Color.GOLD) + " " +
											color(nav.distance + "m", Color.GREY) + " " +
											color(navTarget + " [" + dimName + "]", Color.GREY);
								} else {
									if (nav.indicator.equals("⬤")) {
										navSection = color(nav.indicator, Color.GOLD) + " " +
												color("at " + navTarget, Color.GREY);
									} else {
										navSection = color(nav.indicator, Color.GOLD) + " " +
												color(nav.distance + "m", Color.GREY) + " " +
												color(navTarget, Color.GREY);
									}
								}

								message = String.format("%s %s %s %s %s %s %s %s %s %s",
										xCoord, yCoord, hCoord, separator, formattedHeading, separator,
										event, timeString, separator, navSection);
							} else {
								activeNavigationTarget.remove(player.getStringUUID());
								message = String.format("%s %s %s %s %s %s %s %s",
										xCoord, yCoord, hCoord, separator, formattedHeading, separator, event,
										timeString);
							}
						} else {
							message = String.format("%s %s %s %s %s %s %s %s",
									xCoord, yCoord, hCoord, separator, formattedHeading, separator, event, timeString);
						}
					} else {
						message = String.format("%s %s %s %s %s %s %s %s",
								xCoord, yCoord, hCoord, separator, formattedHeading, separator, event, timeString);
					}

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

		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (entity instanceof ServerPlayer player) {
				loadPins(player.level(), player.getStringUUID());
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

	private Path getPinsDirectory(ServerLevel level) {
		Path serverDir = level.getServer().getServerDirectory();
		return serverDir.resolve("data").resolve("pins");
	}

	private File getPlayerPinsFile(ServerLevel level, String playerUUID) {
		Path pinsDir = getPinsDirectory(level);
		File pinsDirFile = pinsDir.toFile();

		LOGGER.info("Attempting to create pins directory at: {}", pinsDirFile.getAbsolutePath());

		if (!pinsDirFile.exists()) {
			boolean created = pinsDirFile.mkdirs();
			LOGGER.info("Directory creation result: {}, exists now: {}", created, pinsDirFile.exists());
		} else {
			LOGGER.info("Directory already exists");
		}

		File jsonFile = pinsDir.resolve(playerUUID + ".json").toFile();
		LOGGER.info("Pin file path: {}", jsonFile.getAbsolutePath());

		return jsonFile;
	}

	private void savePins(ServerLevel level, String playerUUID) {
		Map<String, Map<String, PinData>> playerPins = pins.get(playerUUID);
		if (playerPins == null)
			return;

		File file = getPlayerPinsFile(level, playerUUID);
		Gson gson = new GsonBuilder().setPrettyPrinting().create();

		try (FileWriter writer = new FileWriter(file)) {
			gson.toJson(playerPins, writer);
		} catch (IOException e) {
			LOGGER.error("Failed to save pins for player {}", playerUUID, e);
		}
	}

	private void loadPins(ServerLevel level, String playerUUID) {
		File file = getPlayerPinsFile(level, playerUUID);
		if (!file.exists())
			return;

		Gson gson = new Gson();
		Type type = new TypeToken<Map<String, Map<String, PinData>>>() {
		}.getType();

		try (FileReader reader = new FileReader(file)) {
			Map<String, Map<String, PinData>> loadedPins = gson.fromJson(reader, type);
			if (loadedPins != null) {
				pins.put(playerUUID, new ConcurrentHashMap<>(loadedPins));
			}
		} catch (IOException e) {
			LOGGER.error("Failed to load pins for player {}", playerUUID, e);
		}
	}

	private double calculateDistance(BlockPos from, PinData to) {
		int dx = Math.abs(to.x - from.getX());
		int dy = Math.abs(to.y - from.getY());
		int dz = Math.abs(to.z - from.getZ());

		dx = Math.max(0, dx - 2);
		dz = Math.max(0, dz - 2);
		dy = Math.max(0, dy - 2);

		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	private NavigationDisplay getNavigationDisplay(float playerYaw, BlockPos playerPos, PinData target,
			String currentDimension) {
		double distance = calculateDistance(playerPos, target);
		int distanceBlocks = (int) Math.round(distance);

		if (!target.dimension.equals(currentDimension)) {
			return new NavigationDisplay("⊗", distanceBlocks);
		}

		if (distance < 1.0) {
			return new NavigationDisplay("⬤", distanceBlocks);
		}

		double dx = target.x - playerPos.getX();
		double dz = target.z - playerPos.getZ();

		double angleToTarget = Math.toDegrees(Math.atan2(dz, dx)) - 90;
		double normalizedAngle = ((angleToTarget - playerYaw) % 360 + 360) % 360;

		String arrow;
		if (normalizedAngle >= 337.5 || normalizedAngle < 22.5)
			arrow = "⬆";
		else if (normalizedAngle >= 22.5 && normalizedAngle < 67.5)
			arrow = "⬈";
		else if (normalizedAngle >= 67.5 && normalizedAngle < 112.5)
			arrow = "➡";
		else if (normalizedAngle >= 112.5 && normalizedAngle < 157.5)
			arrow = "⬊";
		else if (normalizedAngle >= 157.5 && normalizedAngle < 202.5)
			arrow = "⬇";
		else if (normalizedAngle >= 202.5 && normalizedAngle < 247.5)
			arrow = "⬋";
		else if (normalizedAngle >= 247.5 && normalizedAngle < 292.5)
			arrow = "⬅";
		else
			arrow = "⬉";

		return new NavigationDisplay(arrow, distanceBlocks);
	}

	private String getDimensionDisplayName(String dimensionId) {
		return switch (dimensionId) {
			case "minecraft:overworld" -> "Overworld";
			case "minecraft:the_nether" -> "Nether";
			case "minecraft:the_end" -> "End";
			default -> dimensionId;
		};
	}

}