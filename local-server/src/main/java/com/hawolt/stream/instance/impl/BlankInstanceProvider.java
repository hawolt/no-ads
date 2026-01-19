package com.hawolt.stream.instance.impl;

import com.hawolt.ionhttp.IonClient;
import com.hawolt.stream.instance.TwitchInstance;
import com.hawolt.stream.instance.TwitchInstanceProvider;

public class BlankInstanceProvider extends TwitchInstanceProvider {
    private final String channel;

    public BlankInstanceProvider(String channel) {
        this.channel = channel;
    }

    @Override
    public TwitchInstance getInstance(IonClient client) {
        return new TwitchInstance(channel, "", "");
    }
}