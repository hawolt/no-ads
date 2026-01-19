package com.hawolt.stream.twitch;

import com.hawolt.ionhttp.IonClient;
import com.hawolt.ionhttp.cookies.impl.DefaultCookieManager;
import com.hawolt.ionhttp.misc.TLS;
import com.hawolt.stream.exceptions.TwitchException;
import com.hawolt.stream.exceptions.TwitchPlaylistException;
import com.hawolt.stream.instance.TwitchInstance;
import com.hawolt.stream.instance.TwitchInstanceProvider;
import com.hawolt.stream.instance.impl.BlankInstanceProvider;
import com.hawolt.stream.playlist.PlaylistM3U8;

import java.io.IOException;

public class TwitchStream {
    private final TwitchInstanceProvider provider;
    private final IonClient client;

    private TwitchInstance instance;

    public static TwitchStream load(String channel) {
        return new TwitchStream(new BlankInstanceProvider(channel));
    }

    public static TwitchStream load(TwitchInstanceProvider provider) {
        return new TwitchStream(provider);
    }

    private TwitchStream(TwitchInstanceProvider provider) {
        this.provider = provider;
        this.client = new IonClient.Builder()
                .setAllowedCipherSuites(IonClient.getAvailableCipherSuites())
                .setCookieManager(new DefaultCookieManager())
                .setVersionTLS(TLS.TLSv1_2)
                .setGracefulTrustManager()
                .build();
    }

    public TwitchEXTM3U load() throws TwitchException, IOException {
        if (instance == null) instance = provider.getInstance(client);
        TwitchConfiguration configuration = Twitch.getConfiguration(client, instance.getChannel());
        TwitchPlaybackToken token = TwitchGQL.getPlaybackAccessTokenGQL(
                client,
                configuration,
                instance
        );
        return TwitchM3U8.request(client, token);
    }

    public PlaylistM3U8 open() throws TwitchException, IOException {
        TwitchEXTM3U m3u = load();
        return m3u.getBestPlaylist().orElseThrow(
                () -> new TwitchPlaylistException("NO_SUITABLE_PLAYLIST")
        );
    }

    public IonClient getClient() {
        return client;
    }
}
