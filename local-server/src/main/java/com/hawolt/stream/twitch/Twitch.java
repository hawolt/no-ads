package com.hawolt.stream.twitch;

import com.hawolt.ionhttp.IonClient;
import com.hawolt.ionhttp.request.IonRequest;
import com.hawolt.ionhttp.request.IonResponse;
import com.hawolt.stream.exceptions.TwitchInitializationException;
import com.hawolt.stream.exceptions.TwitchScriptException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Twitch {
    private static final Object lock = new Object();
    private static TwitchConfiguration configuration;
    private static long timestamp;

    private static void simulate(IonClient client, String channel) throws IOException {
        Twitch.timestamp = System.currentTimeMillis();
        IonRequest request = IonRequest.on(String.format("https://www.twitch.tv/%s", channel))
                .addHeader("Accept", "*/*")
                .addHeader("Connection", "keep-alive")
                .addHeader("Host", "www.twitch.tv")
                .get();
        try (IonResponse response = client.execute(request)) {
            if (response.code() == 405) simulate(client, channel);
        }
    }

    public static TwitchConfiguration getConfiguration(IonClient client, String channel) throws IOException, TwitchScriptException, TwitchInitializationException {
        synchronized (lock) {
            long timestamp = System.currentTimeMillis();
            boolean condition = timestamp - Twitch.timestamp <= TimeUnit.MINUTES.toMillis(5);
            if (configuration != null && condition) {
                Twitch.simulate(client, channel);
                return configuration;
            }
        }
        Twitch.timestamp = System.currentTimeMillis();
        IonRequest request = IonRequest.on(String.format("https://www.twitch.tv/%s", channel))
                .addHeader("Accept", "*/*")
                .addHeader("Connection", "keep-alive")
                .addHeader("Host", "www.twitch.tv")
                .get();
        try (IonResponse response = client.execute(request)) {
            String content = new String(
                    response.body(),
                    StandardCharsets.UTF_8
            );
            return (Twitch.configuration = TwitchConfiguration.create(content));
        }
    }

    public static String getPatternValue(Pattern pattern, String script) throws TwitchScriptException {
        Matcher matcher = pattern.matcher(script);
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new TwitchScriptException(String.format("NO_MATCH:[" + pattern.pattern() + "]"));
    }
}
