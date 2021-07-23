package net.andrews.mechlist;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.api.client.auth.oauth2.AuthorizationCodeRequestUrl;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.ValueRange;

public class GoogleSheets {

    public static LocalServerReceiver receiver;
    private static final String APPLICATION_NAME = "MechList";
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = Utils.getConfigDir().resolve("mechlist/tokens").toString();;

    /**
     * Global instance of the scopes required by this quickstart.
     * If modifying these scopes, delete your previously saved tokens/ folder.
     */
    private static final List<String> SCOPES = Collections.singletonList(SheetsScopes.SPREADSHEETS);

    /**
     * Creates an authorized Credential object.
     * @param HTTP_TRANSPORT The network HTTP Transport.
     * @return An authorized Credential object.
     * @throws IOException If the credentials.json file cannot be found.
     */
    private static Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT, Runnable onAuth) throws IOException {
        // Load client secrets.
        String CREDENTIALS_FILE_PATH = Utils.getConfigDir().resolve("mechlist/credentials.json").toString();

        InputStream in = new FileInputStream(new File(CREDENTIALS_FILE_PATH));
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();

        
        if (receiver != null) {
            receiver.stop();
        }
        receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        String userId = "user";
        try {
            Credential credential = flow.loadCredential(userId);
            if (credential != null
                && (credential.getRefreshToken() != null ||
                    credential.getExpiresInSeconds() == null ||
                    credential.getExpiresInSeconds() > 60)) {
              return credential;
            }
           
            // open in browser
            String redirectUri = receiver.getRedirectUri();
            AuthorizationCodeRequestUrl authorizationUrl =
                flow.newAuthorizationUrl().setRedirectUri(redirectUri);
            String url = authorizationUrl.build();
            MechListMod.setAuthUrl(url);
            onAuth.run();
            // receive authorization code and exchange it for an access token
            String code = receiver.waitForCode();
            MechListMod.setAuthUrl(null);
            if (code == null) {
                throw new IOException("failed to authenticate");
            }
            TokenResponse response = flow.newTokenRequest(code).setRedirectUri(redirectUri).execute();
           
            // store credential and return it
            return flow.createAndStoreCredential(response, userId);
          } finally {
            receiver.stop();
          }
    }

    /**
     * Prints the names and majors of students in a sample spreadsheet:
     * https://docs.google.com/spreadsheets/d/1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgvE2upms/edit
     */
    public static Set<String> getUsernames(Runnable onAuth) throws IOException, GeneralSecurityException {
        // Build a new authorized API client service.
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        final String spreadsheetId = Configs.configs.spreadsheetId;
        final String range = Configs.configs.sheetRange;
        Sheets service = new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT, onAuth))
                .setApplicationName(APPLICATION_NAME)
                .build();
        ValueRange response = service.spreadsheets().values()
                .get(spreadsheetId, range)
                .execute();
        List<List<Object>> values = response.getValues();
        if (values == null || values.isEmpty()) {
           return null;

        } else {
            Set<String> set = new HashSet<>();
            for (List row : values) {
                String username = (String)row.get(0);
                set.add(username);
            }
            return set;
        }
    }

    public static void reset() {
        if (receiver != null) {
            try {
            receiver.stop();
            receiver = null;
            } catch (Exception e) {
                System.out.println("Error: " + e);
            }
        }
    }
}
