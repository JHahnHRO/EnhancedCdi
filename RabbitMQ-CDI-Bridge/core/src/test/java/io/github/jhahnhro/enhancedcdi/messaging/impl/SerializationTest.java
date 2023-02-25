package io.github.jhahnhro.enhancedcdi.messaging.impl;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import javax.enterprise.context.Dependent;
import javax.enterprise.util.TypeLiteral;
import javax.inject.Inject;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Delivery;
import com.rabbitmq.client.Envelope;
import io.github.jhahnhro.enhancedcdi.messaging.Configuration;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Incoming;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Outgoing;
import io.github.jhahnhro.enhancedcdi.messaging.serialization.ByteArrayReaderWriter;
import io.github.jhahnhro.enhancedcdi.messaging.serialization.MessageReader;
import io.github.jhahnhro.enhancedcdi.messaging.serialization.MessageTooLargeException;
import io.github.jhahnhro.enhancedcdi.messaging.serialization.MessageWriter;
import io.github.jhahnhro.enhancedcdi.messaging.serialization.PlainTextReaderWriter;
import io.github.jhahnhro.enhancedcdi.util.BeanHelper;
import org.jboss.weld.junit.MockBean;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldJunit5Extension;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@ExtendWith(WeldJunit5Extension.class)
class SerializationTest {

    static final int MAX_MESSAGE_SIZE = 100;
    static final Configuration CONFIGURATION = new Configuration(new ConnectionFactory(), Configuration.Retry.NO_RETRY,
                                                                 MAX_MESSAGE_SIZE);

    int destructionCounter = 0;
    @WeldSetup
    WeldInitiator w = WeldInitiator.from(Serialization.class, BeanHelper.class, PlainTextReaderWriter.class,
                                         PlainTextReaderWriter.class).addBeans(
            // configuration bean for the maximal message size
            MockBean.of(CONFIGURATION, Configuration.class),

            // mock bean that does basically the same as PlainTextReaderWriter, but has higher priority
            MockBean.builder()
                    .types(new TypeLiteral<MessageWriter<String>>() {}.getType(),
                           new TypeLiteral<MessageReader<String>>() {}.getType())
                    .scope(Dependent.class)
                    .creating(new StringTestCodec(1))
                    .destroy((instance, ctx) -> destructionCounter++)
                    .build(),
            // mock bean that should never be selected, but is also dependent
            MockBean.builder()
                    .types(new TypeLiteral<MessageWriter<Byte>>() {}.getType())
                    .scope(Dependent.class)
                    .creating(new ByteTestCodec(1))
                    .destroy((instance, ctx) -> destructionCounter++)
                    .build()).build();


    @Inject
    Serialization serialization;

    //region Serialization
    @Test
    void givenStringMessage_whenGetMessageWriterType_thenReturnCorrect() {
        final Type expectedType = new TypeLiteral<MessageWriter<? super String>>() {}.getType();
        final Type actualType = Serialization.getMessageWriterType(String.class);

        assertThat(actualType).isEqualTo(expectedType);
    }

    @Test
    void givenStringMessage_whenSerialize_thenHigherPriorityWriterIsSelected() throws IOException {
        Outgoing<String> outgoingMessage = createOutgoingMessage();

        final Outgoing<byte[]> serializedMessage = serialization.serialize(outgoingMessage);

        assertThat(serializedMessage.content()).isEqualTo("pong".getBytes(StandardCharsets.UTF_8));
        // verify that the StringTestCodec was selected
        assertThat(serializedMessage.properties().getType()).isEqualTo("test");
    }

    @Test
    void givenStringMessage_whenSerialize_thenDestroyDependentWriters() throws IOException {
        Outgoing<String> outgoingMessage = createOutgoingMessage();

        serialization.serialize(outgoingMessage);

        // verify that the dependent MockBean of type StringTestCodec was destroyed, but ByteTestCodec was not
        assertThat(destructionCounter).isEqualTo(1);
    }

    @Test
    void givenMessageWithoutWriter_whenSerialize_thenThrowISE() {
        Outgoing<Integer> outgoingMessage = createOutgoingMessage().builder()
                .setContent(42)
                .setType(Integer.class)
                .build();

        assertThatIllegalStateException().isThrownBy(() -> serialization.serialize(outgoingMessage));
    }

    @Test
    void givenLargeMessage_whenSerialize_thenThrowMessageTooLargeException() {
        final Outgoing<String> outgoing = createOutgoingMessage().builder()
                .setContent("reeeeeeeeeeeeeeeeeeeeeeeeeeeeeeaaaaaaaaaaaaaaaaaaaaaaaaaaaaaally"
                            + "looooooooooooooooooooooooooooooooooooooooooooooooooooooooooooo"
                            + "ooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooong message")
                .build();

        assertThatThrownBy(() -> serialization.serialize(outgoing)).isInstanceOf(
                MessageTooLargeException.class);
    }

    //endregion

    //region Deserialization
    @Test
    void givenStringIntMessage_whenGetMessageReaderType_thenReturnCorrect() {
        final Type expectedType = new TypeLiteral<MessageReader<? extends CharSequence>>() {}.getType();
        final Type actualType = Serialization.getMessageReaderType(CharSequence.class);

        assertThat(actualType).isEqualTo(expectedType);
    }

    @Test
    void givenStringMessage_whenDeserialize_thenSucceed() throws IOException {
        Incoming<byte[]> incoming = createPingRequest();

        final Incoming<String> serializedMessage = serialization.deserialize(incoming);

        assertThat(serializedMessage.content()).isEqualTo("ping");
    }

    @Test
    void givenStringMessage_whenDeserialize_thenDestroyDependentReaders() throws IOException {
        Incoming<byte[]> incoming = createPingRequest();

        serialization.deserialize(incoming);

        // verify that the dependent MockBean of type StringTestCodec was destroyed, but ByteTestCodec was not
        assertThat(destructionCounter).isEqualTo(1);
    }

    @Test
    void givenMessageWithoutReader_whenSerialize_thenThrowISE() {
        byte[] bytes = new byte[]{42};
        final Envelope envelope = new Envelope(0L, false, "exchange", "routing.key");
        final AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder().contentType("unsupportedContentType")
                .build();
        final Delivery delivery = new Delivery(envelope, properties, bytes);
        Incoming<byte[]> incoming = new Incoming.Cast<>(delivery, "queue", bytes);

        assertThatIllegalStateException().isThrownBy(() -> serialization.deserialize(incoming));
    }
    //endregion

    private Outgoing.Response<byte[], String> createOutgoingMessage() {
        final AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder().correlationId("myCorrelationID")
                .build();
        return new Outgoing.Response<>(properties, "pong", createPingRequest());
    }

    private Incoming.Request<byte[]> createPingRequest() {
        byte[] bytes = "ping".getBytes(StandardCharsets.UTF_8);
        final Envelope envelope = new Envelope(0L, false, "exchange", "routing.key");
        final AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder().contentType(
                        "text/plain; charset=UTF-8")
                .replyTo("autogenerated-reply-queue")
                .correlationId("myCorrelationID")
                .build();
        final Delivery delivery = new Delivery(envelope, properties, bytes);
        return new Incoming.Request<>(delivery, "queue", bytes);
    }

    static class StringTestCodec extends PlainTextReaderWriter {

        private final int priority;

        StringTestCodec(int priority) {
            this.priority = priority;
        }

        @Override
        public void write(Outgoing<String> originalMessage, Outgoing.Builder<OutputStream> serializedMessage)
                throws IOException {
            super.write(originalMessage, serializedMessage);
            serializedMessage.propertiesBuilder().type("test");
        }

        @Override
        public int getPriority() {
            return this.priority;
        }
    }

    static class ByteTestCodec extends ByteArrayReaderWriter {

        private final int priority;

        ByteTestCodec(int priority) {
            this.priority = priority;
        }

        @Override
        public int getPriority() {
            return this.priority;
        }
    }
}