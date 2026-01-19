package com.hawolt.stream.twitch;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

public class TwitchTokenGQL {

    public static class Token extends JSONObject {
        public byte[] convert() {
            return this.toString().getBytes(StandardCharsets.UTF_8);
        }
    }

    public static class PlaybackToken extends Token {
        private PlaybackToken(String operationName, String query, String channel) {
            put("operationName", operationName);
            put("query", query);
            JSONObject variables = new JSONObject();
            variables.put("playerType", "site");
            variables.put("platform", "web");
            variables.put("login", channel);
            variables.put("isLive", true);
            variables.put("isVod", false);
            variables.put("vodID", "");
            put("variables", variables);
        }
    }

    public static PlaybackToken getPlaybackToken(String operationName, String query, String channel) {
        return new PlaybackToken(operationName, query, channel);
    }
}
