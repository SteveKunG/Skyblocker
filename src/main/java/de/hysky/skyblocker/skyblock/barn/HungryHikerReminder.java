package de.hysky.skyblocker.skyblock.barn;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.mojang.brigadier.Message;
import de.hysky.skyblocker.SkyblockerMod;
import de.hysky.skyblocker.annotations.Init;
import de.hysky.skyblocker.config.SkyblockerConfigManager;
import de.hysky.skyblocker.events.SkyblockEvents;
import de.hysky.skyblocker.utils.Constants;
import de.hysky.skyblocker.utils.Utils;
import de.hysky.skyblocker.utils.scheduler.Scheduler;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;

public class HungryHikerReminder {
	private static final String HUNGRY_HIKER_FILE = "hungry_hiker.txt";
	private static final Pattern HUNGRY_HIKER_PATTERN = Pattern.compile("^§e\\[NPC] Hungry Hiker§f: Come back before then so I don't perish!$");
	private static final Logger LOGGER = LoggerFactory.getLogger("Skyblocker Hungry Hiker Reminder");
	private static final int REMINDER_TIME = 864_000; // 12 hours
	private static boolean scheduled = false;

	private HungryHikerReminder() {}

	@Init
	public static void init() {
		SkyblockEvents.JOIN.register(HungryHikerReminder::checkTempFile);
		ClientReceiveMessageEvents.ALLOW_GAME.register(HungryHikerReminder::checkIfHungryHiker);
	}

	@SuppressWarnings("SameReturnValue")
	public static boolean checkIfHungryHiker(Message message, boolean overlay) {
		if (!HUNGRY_HIKER_PATTERN.matcher(message.getString()).matches() || scheduled) return true;

		Scheduler.INSTANCE.schedule(HungryHikerReminder::sendMessage, REMINDER_TIME);
		scheduled = true;
		File tempFile = SkyblockerMod.CONFIG_DIR.resolve(HUNGRY_HIKER_FILE).toFile();

		if (!tempFile.exists()) {
			try {
				tempFile.createNewFile();
			} catch (IOException e) {
				LOGGER.error("Failed to create temp file for Hungry Hiker Reminder!", e);
				return true;
			}
		}

		try (FileWriter writer = new FileWriter(tempFile)) {
			writer.write(String.valueOf(System.currentTimeMillis())); // Overwrites the file so no need to handle case where the file already exists and has text
		} catch (IOException e) {
			LOGGER.error("Failed to write to temp file for Hungry Hiker Reminder!", e);
		}

		return true;
	}

	private static void sendMessage() {
		if (MinecraftClient.getInstance().player == null || !Utils.isOnSkyblock()) return;
		if (SkyblockerConfigManager.get().helpers.barn.enableHungryHikerReminder) {
			MinecraftClient.getInstance().player.sendMessage(Constants.PREFIX.get().append(Text.translatable("skyblocker.config.helpers.barn.sendHungryHikerReminderMessage").formatted(Formatting.RED)), false);
		}

		File tempFile = SkyblockerMod.CONFIG_DIR.resolve(HUNGRY_HIKER_FILE).toFile();

		try {
			scheduled = false;
			if (tempFile.exists()) Files.delete(tempFile.toPath());
		} catch (Exception e) {
			LOGGER.error("Failed to delete temp file for Hungry Hiker Reminder!", e);
		}
	}

	private static void checkTempFile() {
		File tempFile = SkyblockerMod.CONFIG_DIR.resolve(HUNGRY_HIKER_FILE).toFile();
		if (!tempFile.exists() || scheduled) return;

		long time;
		try (Stream<String> file = Files.lines(tempFile.toPath())) {
			time = Long.parseLong(file.findFirst().orElseThrow());
		} catch (Exception e) {
			LOGGER.error("Failed to read temp file for Hungry Hiker Reminder!", e);
			return;
		}

		if (System.currentTimeMillis() - time >= 60 * 720 * 1000) sendMessage();
		else {
			Scheduler.INSTANCE.schedule(HungryHikerReminder::sendMessage, REMINDER_TIME - (int) ((System.currentTimeMillis() - time) / 50)); // 50 milliseconds is 1 tick
			scheduled = true;
		}
	}
}
