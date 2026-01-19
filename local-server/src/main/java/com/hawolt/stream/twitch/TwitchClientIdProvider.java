package com.hawolt.stream.twitch;

import com.hawolt.ionhttp.IonClient;
import com.hawolt.ionhttp.request.IonRequest;
import com.hawolt.ionhttp.request.IonResponse;
import com.hawolt.logger.Logger;
import com.hawolt.stream.exceptions.TwitchScriptException;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class TwitchClientIdProvider {
    private static final Pattern CLIENT_ID_PATTERN = Pattern.compile("clientId=\"(.*?)\"");
    private static final TwitchClientIdProvider INSTANCE = new TwitchClientIdProvider();

    public static String getGlobalClientId() {
        return INSTANCE.getClientId();
    }

    private long timestamp = System.currentTimeMillis();
    private String clientId;

    private TwitchClientIdProvider() {
        try {
            this.clientId = fetchClientId();
        } catch (IOException | TwitchScriptException e) {
            Logger.fatal("Failed to fetch client ID", e);
            System.exit(1);
        }
    }

    public String getClientId() {
        if ((System.currentTimeMillis() - timestamp) >= TimeUnit.HOURS.toMillis(1)) {
            Logger.debug("client ID invalidated");
            this.timestamp = System.currentTimeMillis();
            try {
                this.clientId = fetchClientId();
            } catch (IOException | TwitchScriptException e) {
                Logger.error(e);
            }
        }
        return clientId;
    }

    public String fetchClientId() throws IOException, TwitchScriptException {
        IonRequest request = IonRequest.on("https://www.twitch.tv/?")
                .addHeader("Accept", "*/*")
                .addHeader("Connection", "keep-alive")
                .addHeader("Host", "www.twitch.tv")
                .get();
        try (IonResponse response = IonClient.getDefault().execute(request)) {
            String content = new String(response.body());
            return Twitch.getPatternValue(CLIENT_ID_PATTERN, content);
        }
    }
}
