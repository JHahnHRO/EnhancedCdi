package io.github.jhahn.enhancedcdi.messaging.serialization;

public interface BuiltInCodec<IN, OUT> extends Codec<IN, OUT> {

    @Override
    default int getPriority() {
        return Integer.MIN_VALUE;
    }
}
