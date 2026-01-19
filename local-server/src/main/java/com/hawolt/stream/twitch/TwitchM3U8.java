package com.hawolt.stream.twitch;

import com.hawolt.ionhttp.IonClient;
import com.hawolt.ionhttp.request.IonRequest;
import com.hawolt.ionhttp.request.IonResponse;
import com.hawolt.stream.exceptions.TwitchStreamOffline;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class TwitchM3U8 {

    public static TwitchEXTM3U request(IonClient client, TwitchPlaybackToken token) throws IOException, TwitchStreamOffline {
        String usher = String.format("https://usher.ttvnw.net/api/channel/hls/%s.m3u8", token.channel());
        IonRequest request = IonRequest.on(usher)
                .addHeader("Host", "usher.ttvnw.net")
                .addQueryParameter("allow_source", "true")
                .addQueryParameter("sig", token.signature())
                .addQueryParameter("token", token.get())
                .get();
        try (IonResponse response = client.execute(request)) {
            String playlist = new String(
                    response.body(),
                    StandardCharsets.UTF_8
            );
            if (response.code() == 404) throw new TwitchStreamOffline(playlist);
            return new TwitchEXTM3U(playlist);
        }
    }

}
