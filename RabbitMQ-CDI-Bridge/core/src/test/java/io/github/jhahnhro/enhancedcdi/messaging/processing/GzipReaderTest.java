package io.github.jhahnhro.enhancedcdi.messaging.processing;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;
import javax.inject.Inject;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Delivery;
import com.rabbitmq.client.Envelope;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Incoming;
import io.github.jhahnhro.enhancedcdi.messaging.serialization.InvalidMessageException;
import io.github.jhahnhro.enhancedcdi.messaging.serialization.MessageReader;
import io.github.jhahnhro.enhancedcdi.messaging.serialization.Selected;
import org.jboss.weld.junit5.EnableWeld;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.Test;

@EnableWeld
class GzipReaderTest {

    private static final byte[] BYTES = {0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1};
    private static final byte[] GZIPPED_BYTES = createCompressedBytes();
    private static final byte[] DEFLATED_BYTES = createDeflatedBytes();

    @WeldSetup
    WeldInitiator w = WeldInitiator.from(GzipReader.class, FooBarReader.class).build();

    @Inject
    @Selected
    MessageReader<Object> gzipReader;

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
    void givenOtherEncodedIncomingMessage_whenRead_thenDecompress() throws IOException {
        final Incoming.Cast<InputStream> incoming = getIncoming("other", BYTES);

        final Object fooBar = gzipReader.read(incoming);
        assertThat(fooBar).isNotNull().isInstanceOf(FooBar.class);
    }

    @Test
    void givenGzipEncodedIncomingMessage_whenRead_thenDecompress() throws IOException {
        final Incoming.Cast<InputStream> incoming = getIncoming("gzip", GZIPPED_BYTES);

        final Object fooBar = gzipReader.read(incoming);
        assertThat(fooBar).isNotNull().isInstanceOf(FooBar.class);
    }

    @Test
    void givenDeflateEncodedIncomingMessage_whenRead_thenDecompress() throws IOException {
        final Incoming.Cast<InputStream> incoming = getIncoming("deflate", DEFLATED_BYTES);

        final Object fooBar = gzipReader.read(incoming);
        assertThat(fooBar).isNotNull().isInstanceOf(FooBar.class);
    }

    private Incoming.Cast<InputStream> getIncoming(String encoding, byte[] bytes) {
        final var properties = new AMQP.BasicProperties.Builder().contentEncoding(encoding).build();
        final var delivery = new Delivery(createEnvelope(), properties, bytes);
        final InputStream body = new ByteArrayInputStream(bytes);
        return new Incoming.Cast<>(delivery, "queue", body);
    }

    private Envelope createEnvelope() {
        return new Envelope(123456789L, false, "exchange", "my.routing.key");
    }

    private static class FooBar {}

    @Selected
    static class FooBarReader implements MessageReader<Object> {

        protected FooBarReader() {
        }

        @Override
        public FooBar read(Incoming<InputStream> message) throws InvalidMessageException, IOException {
            try (InputStream inputStream = message.content()) {
                final byte[] bytes = inputStream.readAllBytes();
                if (Arrays.equals(bytes, BYTES)) {
                    return new FooBar();
                } else {
                    return null;
                }
            }
        }
    }
}
