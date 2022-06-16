package io.github.jhahn.enhancedcdi.messaging;

public class InvalidMessageException extends RuntimeException {
    public InvalidMessageException(String s, Exception e) {super(s, e);}
}
