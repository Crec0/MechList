package net.andrews.mechlist;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;

public record GoogleSheetsConfig(String spreadsheetId, String sheetRange, String apiKey, boolean shouldOpAll) {
	public static final Gson GSON = new GsonBuilder()
		.setPrettyPrinting()
		.registerTypeAdapter(GoogleSheetsConfig.class, new GoogleSheetsConfigDeserializer())
		.create();

	public void setSpreadsheetId(String id) {
		var updatedConfig = new GoogleSheetsConfig(id, sheetRange(), apiKey(), shouldOpAll());
		updatedConfig.saveToFile();
		MechList.setConfig(updatedConfig);
	}

	public void setSheetRange(String range) {
		var updatedConfig = new GoogleSheetsConfig(spreadsheetId(), range, apiKey(), shouldOpAll());
		updatedConfig.saveToFile();
		MechList.setConfig(updatedConfig);
	}

	public void saveToFile() {

		try {
			if (!Files.exists(MechList.CONFIG_DIR)) {
				Files.createDirectories(MechList.CONFIG_DIR);
			}
		} catch (IOException e) {
			MechList.LOGGER.error("Failed to create config directory", e);
		}

		try (BufferedWriter writer = Files.newBufferedWriter(ConfigFiles.CONFIG.getPath())) {
			GSON.toJson(this, writer);
		} catch (IOException e) {
			MechList.LOGGER.error("Failed to save config file", e);
		}
	}

	public GoogleSheetsConfig loadFromFile() {
		try {
			String fileContents = new String(Files.readAllBytes(ConfigFiles.CONFIG.getPath()));
			return GSON.fromJson(fileContents.isBlank() ? "{}" : fileContents, GoogleSheetsConfig.class);
		} catch (IOException e) {
			MechList.LOGGER.error("No config file found, creating new one");
			this.saveToFile();
		} catch (JsonParseException e) {
			MechList.LOGGER.error("Failed to parse config file", e);
		}
		return new GoogleSheetsConfig("", "", "", false);
	}

	private static class GoogleSheetsConfigDeserializer implements JsonDeserializer<GoogleSheetsConfig> {

		private String getAsString(JsonObject json, String key) {
			return json.get(key) == null ? "" : json.get(key).getAsString();
		}

		private boolean getAsBoolean(JsonObject json, String key) {
			return json.get(key) != null && json.get(key).getAsBoolean();
		}

		@Override
		public GoogleSheetsConfig deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
			JsonObject jsonObject = json.getAsJsonObject();
			String spreadsheetId = getAsString(jsonObject, "spreadsheetId");
			String sheetRange = getAsString(jsonObject, "sheetRange");
			String apiKey = getAsString(jsonObject, "api-key");
			boolean shouldOpAll = getAsBoolean(jsonObject, "shouldOpAll");
			return new GoogleSheetsConfig(spreadsheetId, sheetRange, apiKey, shouldOpAll);
		}
	}
}
