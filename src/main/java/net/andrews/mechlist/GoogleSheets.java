package net.andrews.mechlist;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.ValueRange;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GoogleSheets {

	private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

	public static Set<String> getUsernames() throws IOException, GeneralSecurityException {
		Sheets service = new Sheets.Builder(
			GoogleNetHttpTransport.newTrustedTransport(),
			JSON_FACTORY,
			request -> request.setInterceptor(interceptor -> interceptor.getUrl().set("key", MechList.getConfig().apiKey()))
		)
		.setApplicationName(MechList.USER_ID)
		.build();

		ValueRange response = service.spreadsheets().values().get(
			MechList.getConfig().spreadsheetId(),
			MechList.getConfig().sheetRange()
		).execute();

		List<List<Object>> values = response.getValues();

		if (values == null || values.isEmpty()) {
			return null;
		}

		Set<String> usernames = new HashSet<>();
		for (List<Object> row : values) {
			for (Object cell : row) {
				if (cell instanceof String s && !s.isBlank()) {
					usernames.add(s.trim().toLowerCase());
				}
			}
		}
		return usernames;
	}
}
