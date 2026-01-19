package com.hawolt.stream.instance;

import com.hawolt.ionhttp.IonClient;
import com.hawolt.stream.exceptions.TwitchCookieException;

import java.io.IOException;

public abstract class TwitchInstanceProvider {

    public abstract TwitchInstance getInstance(IonClient client) throws IOException, TwitchCookieException;
}