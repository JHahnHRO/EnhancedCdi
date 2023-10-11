package io.github.jhahnhro.enhancedcdi.serialization.protobuf;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import com.google.protobuf.Duration;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Envelope;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Incoming;
import org.junit.jupiter.api.Test;

class ProtobufMessageReaderTest {

    public static final Duration DURATION = Duration.newBuilder().setSeconds(123456789L).build();
    public static final String FULL_PROTOBUF_TYPE_NAME = Duration.getDescriptor().getFullName();
    private final ProtobufMessageReader<Duration> reader = new ProtobufMessageReader<>(Duration.class) {};

    private static Incoming<byte[]> getMessage(final String contentType, final String fullProtobufTypeName) {
        final byte[] bytes = DURATION.toByteArray();
        final AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder().deliveryMode(1)
                .contentType(contentType)
                .type(fullProtobufTypeName)
                .build();
        final Envelope envelope = new Envelope(0L, false, "exchange", "routing.key");
        return new Incoming.Cast<>("queue", envelope, properties, bytes);
    }

    @Test
    void givenValidProtobufMessage_whenCanRead_thenReturnTrue() {
        final Incoming<byte[]> message = getMessage("application/x-protobuf", FULL_PROTOBUF_TYPE_NAME);

        assertThat(reader.canRead(message)).isTrue();
    }

    @Test
    void givenValidProtobufMessage_whenRead_thenReturnCorrectResult() {
        final Incoming<byte[]> message = getMessage("application/x-protobuf", FULL_PROTOBUF_TYPE_NAME);
        final Incoming<InputStream> messageWithStream = message.withContent(
                new ByteArrayInputStream(message.content()));

        final Duration actual = reader.read(messageWithStream);
        assertThat(actual).isEqualTo(DURATION);
    }

    @Test
    void givenMessageWithWrongContentType_whenCanRead_thenReturnTrue() {
        final Incoming<byte[]> message = getMessage("application/octet-stream", FULL_PROTOBUF_TYPE_NAME);

        assertThat(reader.canRead(message)).isFalse();
    }

    @Test
    void givenMessageWithWrongType_whenCanRead_thenReturnTrue() {
        // java FQN instead of protobuf package+simple name
        final Incoming<byte[]> message = getMessage("application/octet-stream", "com.google.protobuf.Duration");

        assertThat(reader.canRead(message)).isFalse();
    }
}