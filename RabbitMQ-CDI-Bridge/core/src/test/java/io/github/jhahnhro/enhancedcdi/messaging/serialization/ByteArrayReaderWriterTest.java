package io.github.jhahnhro.enhancedcdi.messaging.serialization;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Delivery;
import com.rabbitmq.client.Envelope;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Incoming;
import io.github.jhahnhro.enhancedcdi.messaging.messages.MessageBuilder;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Outgoing;
import org.junit.jupiter.api.Test;

class ByteArrayReaderWriterTest {

    ByteArrayReaderWriter readerWriter = new ByteArrayReaderWriter();

    @Test
    void read() throws IOException {
        byte[] body = new byte[]{0, 1, 2, 3, 4, 5, 6, 7};
        AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder().deliveryMode(2)
                .contentType("application/octet-stream")
                .build();
        Envelope envelope = new Envelope(123456L, false, "exchange", "routing.key");
        Incoming<byte[]> incoming = new Incoming.Cast<>(new Delivery(envelope, properties, body), "queue", body);

        assertThat(readerWriter.canRead(incoming)).isTrue();

        final byte[] actualBytes = readerWriter.read(incoming.withContent(new ByteArrayInputStream(body)));
        assertThat(actualBytes).isEqualTo(body);
    }

    @Test
    void write() throws IOException {
        byte[] body = new byte[]{0, 1, 2, 3, 4, 5, 6, 7};
        AMQP.BasicProperties properties = new AMQP.BasicProperties().builder().deliveryMode(2).build();
        Outgoing<byte[]> outgoing = new Outgoing.Cast<>("exchange", "routing.key", properties, body);

        assertThat(readerWriter.canWrite(outgoing)).isTrue();

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        MessageBuilder<OutputStream, ?> builder = outgoing.builder().setContent(baos);

        readerWriter.write(outgoing, builder);

        byte[] actualBytes = baos.toByteArray();
        assertThat(actualBytes).isEqualTo(body);

        AMQP.BasicProperties actualProperties = builder.properties();
        assertThat(actualProperties.getContentType()).isEqualTo("application/octet-stream");
    }
}