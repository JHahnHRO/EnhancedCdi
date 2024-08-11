package io.github.jhahnhro.enhancedcdi.messaging.processing;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Envelope;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Incoming;
import io.github.jhahnhro.enhancedcdi.messaging.messages.MessageBuilder;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Outgoing;
import io.github.jhahnhro.enhancedcdi.messaging.serialization.MessageWriter;
import io.github.jhahnhro.enhancedcdi.messaging.serialization.Selected;
import jakarta.inject.Inject;
import org.jboss.weld.junit5.EnableWeld;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.Test;

@EnableWeld
class GzipWriterTest {

    private static final byte[] BYTES = {0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1};
    private static final byte[] GZIPPED_BYTES = createCompressedBytes();
    private static final byte[] DEFLATED_BYTES = createDeflatedBytes();

    @WeldSetup
    WeldInitiator w = WeldInitiator.from(GzipWriter.class, FooBarWriter.class).build();

    @Inject
    @Selected
    MessageWriter<FooBar> gzipWriter;

    private static byte[] createCompressedBytes() {
        try (var os = new ByteArrayOutputStream(); var gzos = new GZIPOutputStream(os)) {
            gzos.write(BYTES);
            gzos.finish();
            return os.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] createDeflatedBytes() {
        try (var os = new ByteArrayOutputStream(); var defOS = new DeflaterOutputStream(os)) {
            defOS.write(BYTES);
            defOS.finish();
            return os.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void givenNonResponseMessage_whenWrite_thenDoNotCompress() throws IOException {
        final Outgoing.Cast<FooBar> outgoingMessage = new Outgoing.Cast<>("exchange", "my.routing.key",
                                                                          new AMQP.BasicProperties.Builder().deliveryMode(
                                                                                  1).build(), new FooBar());
        final Outgoing<byte[]> actual = getActualBytes(outgoingMessage);
        assertThat(actual.content()).isEqualTo(BYTES);
        assertThat(actual.properties().getContentEncoding()).isNull();
    }

    @Test
    void givenResponseWithOtherEncodingAlreadyPresent_whenWrite_thenDoNotCompress() throws IOException {
        final var messageBuilder = getFooBarResponse("*").builder();
        messageBuilder.propertiesBuilder().contentEncoding("otherEncoding");
        final Outgoing<FooBar> outgoingMessage = messageBuilder.build();

        final Outgoing<byte[]> actual = getActualBytes(outgoingMessage);
        assertThat(actual.content()).isEqualTo(BYTES);
        assertThat(actual.properties().getContentEncoding()).isEqualTo("otherEncoding");
    }

    @Test
    void givenRequestWithUnsupportedEncodingAccepted_whenWriteResponse_thenCompress() throws IOException {
        final Outgoing.Response<String, FooBar> response = getFooBarResponse("unsupportedEncoding");

        final Outgoing<byte[]> actual = getActualBytes(response);
        assertThat(actual.content()).isEqualTo(BYTES);
        assertThat(actual.properties().getContentEncoding()).isNull();
    }

    @Test
    void givenRequestWithGzipEncodingAccepted_whenWriteResponse_thenCompress() throws IOException {
        final Outgoing.Response<String, FooBar> response = getFooBarResponse("gzip");

        final Outgoing<byte[]> actual = getActualBytes(response);
        assertThat(actual.content()).isEqualTo(GZIPPED_BYTES);
        assertThat(actual.properties().getContentEncoding()).isEqualTo("gzip");
    }

    @Test
    void givenRequestWithAnyEncodingAccepted_whenWriteResponse_thenCompress() throws IOException {
        final Outgoing.Response<String, FooBar> response = getFooBarResponse("*");

        final Outgoing<byte[]> actual = getActualBytes(response);
        assertThat(actual.content()).isEqualTo(GZIPPED_BYTES);
        assertThat(actual.properties().getContentEncoding()).isEqualTo("gzip");
    }

    @Test
    void givenRequestWithDeflateEncodingAccepted_whenWriteResponse_thenCompress() throws IOException {
        final Outgoing.Response<String, FooBar> response = getFooBarResponse("deflate");

        final Outgoing<byte[]> actual = getActualBytes(response);
        assertThat(actual.content()).isEqualTo(DEFLATED_BYTES);
        assertThat(actual.properties().getContentEncoding()).isEqualTo("deflate");
    }

    @Test
    void givenOutgoingRequest_whenWrite_thenAcceptEncodingHeaderSet() throws IOException {

        final AMQP.BasicProperties requestProperties = new AMQP.BasicProperties.Builder().deliveryMode(1)
                .replyTo("auto-generated-reply-queue")
                .correlationId("myCorrelationID")
                .build();
        final Outgoing.Request<FooBar> pingRequest = new Outgoing.Request<>("exchange", "my.routing.key",
                                                                            requestProperties, new FooBar());

        final Outgoing<byte[]> actual = getActualBytes(pingRequest);
        assertThat(actual.getHeader("Accept-Encoding")).contains("gzip;q=1.0, deflate;q=0.9, *;q=0.5");
    }

    private Outgoing.Response<String, FooBar> getFooBarResponse(String acceptedEncoding) {
        final Envelope envelope = new Envelope(0L, false, "exchange", "my.routing.key");
        final AMQP.BasicProperties requestProperties = new AMQP.BasicProperties.Builder().deliveryMode(1)
                .replyTo("auto-generated-reply-queue")
                .correlationId("myCorrelationID")
                .headers(Map.of("Accept-Encoding", acceptedEncoding))
                .build();
        final var request = new Incoming.Request<>("queue", envelope, requestProperties, "ping");

        final AMQP.BasicProperties responseProperties = new AMQP.BasicProperties.Builder().deliveryMode(1)
                .correlationId("myCorrelationID")
                .build();
        return new Outgoing.Response<>(responseProperties, new FooBar(), request);
    }

    private Outgoing<byte[]> getActualBytes(Outgoing<FooBar> outgoingMessage) throws IOException {
        try (ByteArrayOutputStream boas = new ByteArrayOutputStream()) {
            final MessageBuilder<OutputStream, ?> messageBuilder = outgoingMessage.builder()
                    .setType(OutputStream.class)
                    .setContent(boas);
            gzipWriter.write(outgoingMessage, messageBuilder);
            return messageBuilder.setType(byte[].class).setContent(boas.toByteArray()).build();
        }
    }

    private static class FooBar {}

    @Selected
    static class FooBarWriter implements MessageWriter<FooBar> {
        protected FooBarWriter() {
        }

        @Override
        public void write(Outgoing<FooBar> originalMessage, MessageBuilder<OutputStream, ?> serializedMessage)
                throws IOException {
            try (var content = serializedMessage.content()) {
                content.write(BYTES);
            }
        }
    }
}