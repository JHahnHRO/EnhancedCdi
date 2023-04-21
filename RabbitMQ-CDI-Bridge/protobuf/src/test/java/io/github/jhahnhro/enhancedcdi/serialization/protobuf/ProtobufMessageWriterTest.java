package io.github.jhahnhro.enhancedcdi.serialization.protobuf;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import com.google.protobuf.Duration;
import com.google.protobuf.Message;
import com.rabbitmq.client.AMQP;
import io.github.jhahnhro.enhancedcdi.messaging.messages.MessageBuilder;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Outgoing;
import org.junit.jupiter.api.Test;

class ProtobufMessageWriterTest {

    private static final Duration DURATION = Duration.newBuilder().setSeconds(123456789L).build();
    private final ProtobufMessageWriter<Duration> writer = new ProtobufMessageWriter<>();

    private static Outgoing<Duration> getMessage() {
        final AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder().deliveryMode(1).build();
        return new Outgoing.Cast<>("exchange", "routing.key", properties, DURATION, Message.class);
    }

    @Test
    void givenValidProtobufMessage_whenCanRead_thenReturnTrue() {
        final Outgoing<Duration> message = getMessage();

        assertThat(writer.canWrite(message)).isTrue();
    }

    @Test
    void givenValidProtobufMessage_whenRead_thenReturnCorrectResult() throws IOException {
        final Outgoing<Duration> message = getMessage();

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final MessageBuilder<OutputStream,?> output = message.builder().setType(OutputStream.class).setContent(baos);

        writer.write(message, output);

        final byte[] actual = baos.toByteArray();
        assertThat(actual).isEqualTo(DURATION.toByteArray());
    }
}