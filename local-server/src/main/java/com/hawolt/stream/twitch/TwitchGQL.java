package com.hawolt.stream.twitch;

import com.hawolt.ionhttp.IonClient;
import com.hawolt.ionhttp.request.IonRequest;
import com.hawolt.ionhttp.request.IonResponse;
import com.hawolt.stream.exceptions.BadTwitchChannelException;
import com.hawolt.stream.instance.TwitchInstance;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class TwitchGQL {
    public static TwitchPlaybackToken getPlaybackAccessTokenGQL(
            IonClient client, TwitchConfiguration configuration, TwitchInstance instance
    ) throws IOException, BadTwitchChannelException {
        TwitchTokenGQL.PlaybackToken token = TwitchTokenGQL.getPlaybackToken(
                configuration.getOperationName(),
                configuration.getQuery(),
                instance.getChannel()
        );
        byte[] b = token.convert();
        IonRequest request = IonRequest.on("https://gql.twitch.tv/gql")
                .addHeader("Cookie", instance.getCookie())
                .addHeader("Client-ID", TwitchClientIdProvider.getGlobalClientId())
                .addHeader("Device-ID", instance.getUniqueId())
                .addHeader("Content-Type", "text/plain")
                .addHeader("Accept", "*/*")
                .addHeader("Host", "gql.twitch.tv")
                .addHeader("Content-Length", String.valueOf(b.length))
                .payload(b)
                .post();
        try (IonResponse response = client.execute(request)) {
            return new TwitchPlaybackToken(
                    new JSONObject(
                            new String(
                                    response.body(),
                                    StandardCharsets.UTF_8
                            )
                    )
            );
        }
    }
}
