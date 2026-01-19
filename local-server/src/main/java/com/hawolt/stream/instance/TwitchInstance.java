package com.hawolt.stream.instance;

public class TwitchInstance {
    private final String channel, cookie, id;

    public TwitchInstance(String channel, String id, String cookie) {
        this.channel = channel;
        this.cookie = cookie;
        this.id = id;
    }

    public String getChannel() {
        return channel;
    }

    public String getCookie() {
        return cookie;
    }

    public String getUniqueId() {
        return id;
    }
}
