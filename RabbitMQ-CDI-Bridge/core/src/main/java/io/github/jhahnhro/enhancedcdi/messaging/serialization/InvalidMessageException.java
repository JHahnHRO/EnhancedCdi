package io.github.jhahnhro.enhancedcdi.messaging.serialization;

public class InvalidMessageException extends RuntimeException {
    public InvalidMessageException(String s, Exception e) {super(s, e);}

    public InvalidMessageException(Exception e) {
        super(e);
    }
}
