package io.github.jhahn.enhancedcdi.messaging;

import com.google.protobuf.InvalidProtocolBufferException;

public class InvalidMessageException extends RuntimeException {
    public InvalidMessageException(String s, Exception e) {super(s, e);}

    public InvalidMessageException(InvalidProtocolBufferException e) {
        super(e);
    }
}
