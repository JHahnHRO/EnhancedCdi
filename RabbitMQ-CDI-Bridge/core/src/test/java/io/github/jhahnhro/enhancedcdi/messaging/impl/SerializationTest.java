package io.github.jhahnhro.enhancedcdi.messaging.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Envelope;
import io.github.jhahnhro.enhancedcdi.messaging.Configuration;
import io.github.jhahnhro.enhancedcdi.messaging.Retry;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Incoming;
import io.github.jhahnhro.enhancedcdi.messaging.messages.MessageBuilder;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Outgoing;
import io.github.jhahnhro.enhancedcdi.messaging.serialization.MessageTooLargeException;
import io.github.jhahnhro.enhancedcdi.messaging.serialization.MessageWriter;
import io.github.jhahnhro.enhancedcdi.messaging.serialization.Selected;
import io.github.jhahnhro.enhancedcdi.util.EnhancedInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

@ExtendWith(MockitoExtension.class)
class SerializationTest {

    @Mock
    SelectedMessageReader selectedMessageReader;
    @Mock
    EnhancedInstance<Object> enhancedInstance;
    @InjectMocks
    Serialization serialization;

    private Outgoing.Response<byte[], String> createOutgoingMessage() {
        final AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder().deliveryMode(1)
                .correlationId("myCorrelationID")
                .build();
        return new Outgoing.Response<>(properties, "pong", createPingRequest());
    }

    @Nested
    class TestSerialization {

        @Mock
        private MessageWriter<String> messageWriter;
        @Captor
        private ArgumentCaptor<MessageBuilder<OutputStream, ?>> streamCaptor;

        @BeforeEach
        void mockEnhancedInstance() {
            when(enhancedInstance.select(any(Type.class), eq(Selected.Literal.INSTANCE))).thenReturn(enhancedInstance);
        }

        private void mockSelectedMessageWriter() {
            when(enhancedInstance.get()).thenReturn(messageWriter);
        }

        @Test
        void givenOutgoingMessageWithAvailableMessageWriter_whenSerialize_thenBuilderIsInitializedWithOutgoingMessage()
                throws IOException {
            mockSelectedMessageWriter();

            Outgoing<String> outgoingMessage = createOutgoingMessage();
            serialization.serialize(outgoingMessage);

            verify(messageWriter).write(eq(outgoingMessage), streamCaptor.capture());
            final MessageBuilder<OutputStream, ?> builder = streamCaptor.getValue();

            assertThat(builder.exchange()).isEqualTo(outgoingMessage.exchange());
            assertThat(builder.routingKey()).isEqualTo(outgoingMessage.routingKey());
            assertThat(builder.properties()).isEqualTo(outgoingMessage.properties());
        }

        @Test
        void givenOutgoingMessageWithAvailableMessageWriter_whenSerialize_thenMessageWriterIsDestroyedAfterWrite()
                throws IOException {
            mockSelectedMessageWriter();

            Outgoing<String> outgoingMessage = createOutgoingMessage();
            serialization.serialize(outgoingMessage);

            final InOrder inOrder = inOrder(messageWriter, enhancedInstance);
            inOrder.verify(messageWriter).write(any(), any());
            inOrder.verify(enhancedInstance).destroy(messageWriter);
        }

        @Test
        void givenOutgoingMessageWithoutAnyMessageWriter_whenSerialize_thenThroeISE() {
            final Exception exception = new IllegalStateException();
            when(enhancedInstance.get()).thenThrow(exception);

            final Outgoing<String> outgoingMessage = createOutgoingMessage();
            assertThatThrownBy(() -> serialization.serialize(outgoingMessage)).isSameAs(exception);
        }

        @Test
        void givenOutgoingMessageWithoutApplicableMessageWriter_whenSerialize_thenThroeISE() throws IOException {
            mockSelectedMessageWriter();

            final Outgoing<String> outgoingMessage = createOutgoingMessage();
            final Exception exception = new IllegalStateException();
            doThrow(exception).when(messageWriter).write(eq(outgoingMessage), any());

            assertThatThrownBy(() -> serialization.serialize(outgoingMessage)).isSameAs(exception);
        }

        @Nested
        class TestMaxMessageSize {

            private static final int MAX_MESSAGE_SIZE = 100;

            @BeforeEach
            void setUp() {
                mockSelectedMessageWriter();
                serialization.setMaxMessageSize(
                        new Configuration(new ConnectionFactory(), Retry.NO_RETRY, MAX_MESSAGE_SIZE));
            }

            @Test
            void givenOutgoingMessageIsNotTooLarge_whenSerialize_thenSucceed() throws IOException {
                final Outgoing<String> outgoing = createOutgoingMessage();
                doAnswer(writeBytes(new byte[MAX_MESSAGE_SIZE])).when(messageWriter).write(eq(outgoing), any());

                assertThatNoException().isThrownBy(() -> serialization.serialize(outgoing));
            }

            @Test
            void givenOutgoingMessageIsTooLarge_whenSerialize_thenThrowMessageTooLargeException() throws IOException {
                final Outgoing<String> outgoing = createOutgoingMessage();
                doAnswer(writeBytes(new byte[MAX_MESSAGE_SIZE + 1])).when(messageWriter).write(eq(outgoing), any());

                assertThatThrownBy(() -> serialization.serialize(outgoing)).isInstanceOf(
                        MessageTooLargeException.class);
            }

            private Answer<Void> writeBytes(final byte[] bytes) {
                return invocation -> {
                    final MessageBuilder<OutputStream, ?> builder = invocation.getArgument(1);
                    builder.content().write(bytes);
                    return null; // void method
                };
            }
        }
    }
    //endregion

    @Nested
    class TestDeserialization {

        @Test
        void givenStringMessage_whenDeserialize_thenSucceed() throws IOException {
            Incoming<byte[]> incoming = createPingRequest();
            when(selectedMessageReader.read(any())).thenReturn("ping");

            final Incoming<?> actual = serialization.deserialize(incoming);

            final Incoming<String> expected = incoming.withContent("ping");
            assertThat(actual).isEqualTo(expected);
        }

        @Test
        void givenMessageWithoutReader_whenSerialize_thenThrowISE() throws IOException {
            Exception ex = new IllegalStateException();
            when(selectedMessageReader.read(any())).thenThrow(ex);

            Incoming<byte[]> incoming = createPingRequest();
            assertThatThrownBy(() -> serialization.deserialize(incoming)).isSameAs(ex);
        }
    }

    private Incoming.Request<byte[]> createPingRequest() {
        byte[] bytes = "ping".getBytes(StandardCharsets.UTF_8);
        final Envelope envelope = new Envelope(0L, false, "exchange", "routing.key");
        final AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder().deliveryMode(1)
                .contentType("text/plain; charset=UTF-8")
                .replyTo("autogenerated-reply-queue")
                .correlationId("myCorrelationID")
                .build();
        return new Incoming.Request<>("queue", envelope, properties, bytes);
    }
}