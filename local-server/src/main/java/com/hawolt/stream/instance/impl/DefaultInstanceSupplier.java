package com.hawolt.stream.instance.impl;

import com.hawolt.ionhttp.IonClient;
import com.hawolt.ionhttp.cookies.CookieManager;
import com.hawolt.ionhttp.request.IonRequest;
import com.hawolt.ionhttp.request.IonResponse;
import com.hawolt.logger.Logger;
import com.hawolt.stream.exceptions.TwitchCookieException;
import com.hawolt.stream.instance.TwitchInstance;
import com.hawolt.stream.instance.TwitchInstanceProvider;

import java.io.IOException;

public class DefaultInstanceSupplier extends TwitchInstanceProvider {
    private final String channel;

    public DefaultInstanceSupplier(String channel) {
        this.channel = channel;
    }

    @Override
    public TwitchInstance getInstance(IonClient client) throws IOException, TwitchCookieException {
        IonRequest request = IonRequest.on(String.format("https://www.twitch.tv/%s", channel))
                .addHeader("Accept", "*/*")
                .addHeader("Connection", "keep-alive")
                .addHeader("Host", "www.twitch.tv")
                .head();
        try (IonResponse response = client.execute(request)) {
            return getCookie(client);
        }
    }

    private TwitchInstance getCookie(IonClient client) throws TwitchCookieException {
        CookieManager manager = client.getCookieManager();
        String twitch = manager.getCookie("twitch.tv");
        String[] cookies = twitch.split(";");
        String uniqueId = null, uniqueIdDurable = null;
        for (String cookie : cookies) {
            String[] pair = cookie.trim().split("=");
            switch (pair[0]) {
                case "unique_id":
                    uniqueId = pair[1];
                    break;
                case "unique_id_durable":
                    uniqueIdDurable = pair[1];
                    break;
            }
        }
        String id = uniqueId != null ? uniqueId : uniqueIdDurable;
        if (id == null) throw new TwitchCookieException("NO_COOKIE_RETRY");
        Logger.debug("[gql] {}:[{}]", channel, id);
        return new TwitchInstance(channel, id, twitch);
    }
}