package io.github.jhahnhro.enhancedcdi.messaging.serialization;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Delivery;
import com.rabbitmq.client.Envelope;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Incoming;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Outgoing;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PlainTextReaderWriterTest {

    PlainTextReaderWriter readerWriter = new PlainTextReaderWriter();

    private static Envelope defaultEnvelope() {
        return new Envelope(123456L, false, "exchange", "routing.key");
    }

    @Nested
    class TestReader {

        @Test
        void readNonUtf8Charset() throws IOException {
            byte[] body = "äöü".getBytes(StandardCharsets.ISO_8859_1);
            AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder().contentType(
                    "text/plain; charset=" + StandardCharsets.ISO_8859_1.name()).build();

            Incoming<byte[]> incoming = new Incoming.Cast<>(new Delivery(defaultEnvelope(), properties, body), "queue",
                                                            body);

            assertThat(readerWriter.canRead(incoming)).isTrue();

            String actual = readerWriter.read(incoming.withContent(new ByteArrayInputStream(body)));

            assertThat(actual).isEqualTo("äöü");
        }

        @Test
        void readUtf8Charset() throws IOException {
            byte[] body = "äöü".getBytes(StandardCharsets.UTF_8);
            AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder().contentType("text/plain").build();

            Incoming<byte[]> incoming = new Incoming.Cast<>(new Delivery(defaultEnvelope(), properties, body), "queue",
                                                            body);

            assertThat(readerWriter.canRead(incoming)).isTrue();

            String actual = readerWriter.read(incoming.withContent(new ByteArrayInputStream(body)));

            assertThat(actual).isEqualTo("äöü");
        }
    }

    @Nested
    class TestWrite {

        @Test
        void write() throws IOException {
            final AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder().deliveryMode(2).build();
            Outgoing<String> outgoing = new Outgoing.Cast<>("exchange", "routing.key", properties, "äöü");

            assertThat(readerWriter.canWrite(outgoing)).isTrue();

            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            final Outgoing.Builder<OutputStream> builder = new Outgoing.Builder<>("exchange", "routing.key").setContent(
                    baos);

            readerWriter.write(outgoing, builder);

            final byte[] actualBytes = baos.toByteArray();
            final byte[] expectedBytes = "äöü".getBytes(StandardCharsets.UTF_8);
            assertThat(actualBytes).isEqualTo(expectedBytes);

            final AMQP.BasicProperties actualProperties = builder.properties();
            assertThat(actualProperties.getContentType()).isEqualTo("text/plain; charset=UTF-8");
        }
    }

}