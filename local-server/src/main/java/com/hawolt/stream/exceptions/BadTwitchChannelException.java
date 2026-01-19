package com.hawolt.stream.exceptions;

public class BadTwitchChannelException extends TwitchException {
    public BadTwitchChannelException() {
        super("CHANNEL_DOES_NOT_EXIST");
    }
}
