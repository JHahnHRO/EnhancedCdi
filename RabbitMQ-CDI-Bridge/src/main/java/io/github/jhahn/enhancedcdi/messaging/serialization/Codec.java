package io.github.jhahn.enhancedcdi.messaging.serialization;

public interface Codec<IN, OUT> extends Deserializer<IN>, Serializer<OUT> {}
