package com.hawolt.stream.twitch;

import com.hawolt.stream.exceptions.BadTwitchChannelException;
import org.json.JSONObject;

public class TwitchPlaybackToken {

    private final String signature, token, channel;

    public TwitchPlaybackToken(JSONObject object) throws BadTwitchChannelException {
        JSONObject data = object.getJSONObject("data");
        if (data.isNull("streamPlaybackAccessToken")) throw new BadTwitchChannelException();
        JSONObject token = data.getJSONObject("streamPlaybackAccessToken");
        this.signature = token.getString("signature");
        this.token = token.getString("value");
        this.channel = new JSONObject(this.token).getString("channel");
    }

    public String signature() {
        return signature;
    }

    public String channel() {
        return channel;
    }

    public String get() {
        return token;
    }
}
