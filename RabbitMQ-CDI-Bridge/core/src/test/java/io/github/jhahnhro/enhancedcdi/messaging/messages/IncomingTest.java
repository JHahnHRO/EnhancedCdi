package io.github.jhahnhro.enhancedcdi.messaging.messages;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.util.Map;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Envelope;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class IncomingTest {

    public static final String HEADER = "myHeader";
    public static final String HEADER_VALUE = "myHeaderValue";
    private static final String QUEUE = "queue";
    private static final AMQP.BasicProperties PROPERTIES = new AMQP.BasicProperties.Builder().deliveryMode(
            Message.DeliveryMode.TRANSIENT.nr).headers(Map.of(HEADER, HEADER_VALUE)).build();
    private static final Integer CONTENT = 4711;
    private static final String EXCHANGE = "exchange";
    private static final String ROUTING_KEY = "routing.key";
    private static final Envelope ENVELOPE = new Envelope(42, false, EXCHANGE, ROUTING_KEY);

    @Nested
    class TestCast {
        @Test
        void givenValidInput_whenWithContent_thenReturnCorrectResult() {
            final Incoming.Cast<Integer> original = new Incoming.Cast<>(QUEUE, ENVELOPE, PROPERTIES, CONTENT);

            final long newContent = -1L;
            final Incoming.Cast<Long> newCast = original.withContent(newContent);

            assertSoftly(softly -> {
                softly.assertThat(newCast.queue()).isEqualTo(QUEUE);

                softly.assertThat(newCast.envelope()).isEqualTo(ENVELOPE);
                softly.assertThat(newCast.exchange()).isEqualTo(EXCHANGE);
                softly.assertThat(newCast.routingKey()).isEqualTo(ROUTING_KEY);

                softly.assertThat(newCast.properties()).isEqualTo(PROPERTIES);
                softly.assertThat(newCast.deliveryMode()).isEqualTo(Message.DeliveryMode.TRANSIENT);
                softly.assertThat(newCast.getHeader(HEADER)).contains(HEADER_VALUE);

                softly.assertThat(newCast.content()).isEqualTo(newContent);
            });
        }

        @Nested
        class TestConstructor {

            @Test
            void givenQueueNameNull_thenThrowNPE() {
                assertThatNullPointerException().isThrownBy(
                        () -> new Incoming.Cast<>(null, ENVELOPE, PROPERTIES, CONTENT));
            }

            @Test
            void givenEnvelopeNull_thenThrowNPE() {
                assertThatNullPointerException().isThrownBy(
                        () -> new Incoming.Cast<>(QUEUE, null, PROPERTIES, CONTENT));
            }

            @Test
            void givenExchangeNull_thenThrowNPE() {
                final Envelope envelope = new Envelope(42, false, null, ROUTING_KEY);
                assertThatNullPointerException().isThrownBy(
                        () -> new Incoming.Cast<>(QUEUE, envelope, PROPERTIES, CONTENT));
            }

            @Test
            void givenRoutingKeyNull_thenDoNot() {
                final Envelope envelope = new Envelope(42, false, EXCHANGE, null);
                assertThatNoException().isThrownBy(() -> new Incoming.Cast<>(QUEUE, envelope, PROPERTIES, CONTENT));
            }

            @Test
            void givenPropertiesNull_thenThrowNPE() {
                assertThatNullPointerException().isThrownBy(() -> new Incoming.Cast<>(QUEUE, ENVELOPE, null, CONTENT));
            }

            @Test
            void givenDeliveryModeNull_thenThrowNPE() {
                final AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder().build();
                assertThatNullPointerException().isThrownBy(
                        () -> new Incoming.Cast<>(QUEUE, ENVELOPE, properties, CONTENT));
            }

            @Test
            void givenDeliverySmallerThanOneNull_thenThrowIAE() {
                final AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder().deliveryMode(0).build();
                assertThatIllegalArgumentException().isThrownBy(
                        () -> new Incoming.Cast<>(QUEUE, ENVELOPE, properties, CONTENT));
            }

            @Test
            void givenDeliveryGreaterThanTwoNull_thenThrowIAE() {
                final AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder().deliveryMode(3).build();
                assertThatIllegalArgumentException().isThrownBy(
                        () -> new Incoming.Cast<>(QUEUE, ENVELOPE, properties, CONTENT));
            }

            @Test
            void givenContentNull_thenDoNotThrow() {
                assertThatNoException().isThrownBy(() -> new Incoming.Cast<>(QUEUE, ENVELOPE, PROPERTIES, null));
            }

            @Test
            void givenValidInput_thenAllComponentsCorrect() {
                final Incoming.Cast<Integer> cast = new Incoming.Cast<>(QUEUE, ENVELOPE, PROPERTIES, CONTENT);

                assertSoftly(softly -> {
                    softly.assertThat(cast.queue()).isEqualTo(QUEUE);

                    softly.assertThat(cast.envelope()).isEqualTo(ENVELOPE);
                    softly.assertThat(cast.exchange()).isEqualTo(EXCHANGE);
                    softly.assertThat(cast.routingKey()).isEqualTo(ROUTING_KEY);

                    softly.assertThat(cast.properties()).isEqualTo(PROPERTIES);
                    softly.assertThat(cast.deliveryMode()).isEqualTo(Message.DeliveryMode.TRANSIENT);
                    softly.assertThat(cast.getHeader(HEADER)).contains(HEADER_VALUE);

                    softly.assertThat(cast.content()).isEqualTo(CONTENT);
                });
            }
        }
    }

    @Nested
    class TestRequest {
        @Test
        void givenValidInput_whenNewResponseBuilder_thenCorrectResult() {
            final String replyTo = "my-response-queue";
            final String correlationId = "0123456789abcdef";
            final AMQP.BasicProperties requestProperties = PROPERTIES.builder()
                    .correlationId(correlationId)
                    .replyTo(replyTo)
                    .build();
            final Incoming.Request<Integer> request = new Incoming.Request<>(QUEUE, ENVELOPE, requestProperties,
                                                                             CONTENT);
            final Outgoing.Response.Builder<Integer, Object> responseBuilder = request.newResponseBuilder();

            assertThat(responseBuilder.exchange()).isEmpty();
            assertThat(responseBuilder.routingKey()).isEqualTo(replyTo);

            assertThat(responseBuilder.deliveryMode()).isEqualTo(Message.DeliveryMode.TRANSIENT);
            assertThat(responseBuilder.properties().getCorrelationId()).isEqualTo(correlationId);
        }

        @Nested
        class TestConstructor {
            @Test
            void givenReplyToNull_thenThrowNPE() {
                final AMQP.BasicProperties requestProperties = PROPERTIES.builder()
                        .correlationId("0123456789abcdef")
                        .build();
                assertThatNullPointerException().isThrownBy(
                        () -> new Incoming.Request<>(QUEUE, ENVELOPE, requestProperties, CONTENT));
            }

            @Test
            void givenCorrelationIdNull_thenThrowNPE() {
                final AMQP.BasicProperties requestProperties = PROPERTIES.builder()
                        .replyTo("my-response-queue")
                        .build();
                assertThatNullPointerException().isThrownBy(
                        () -> new Incoming.Request<>(QUEUE, ENVELOPE, requestProperties, CONTENT));
            }

            @Test
            void givenValidInput_thenCorrectComponents() {
                final AMQP.BasicProperties requestProperties = PROPERTIES.builder()
                        .correlationId("0123456789abcdef")
                        .replyTo("my-response-queue")
                        .build();
                final Incoming.Request<Integer> request = new Incoming.Request<>(QUEUE, ENVELOPE, requestProperties,
                                                                                 CONTENT);
                assertSoftly(softly -> {
                    softly.assertThat(request.queue()).isEqualTo(QUEUE);

                    softly.assertThat(request.envelope()).isEqualTo(ENVELOPE);
                    softly.assertThat(request.exchange()).isEqualTo(EXCHANGE);
                    softly.assertThat(request.routingKey()).isEqualTo(ROUTING_KEY);

                    softly.assertThat(request.properties()).isEqualTo(requestProperties);
                    softly.assertThat(request.deliveryMode()).isEqualTo(Message.DeliveryMode.TRANSIENT);
                    softly.assertThat(request.getHeader(HEADER)).contains(HEADER_VALUE);

                    softly.assertThat(request.content()).isEqualTo(CONTENT);
                });
            }
        }
    }

    @Nested
    class TestResponse {

        @Nested
        class TestConstructor {
            @Test
            void givenRequestNull_thenThrowNPE() {
                assertThatNullPointerException().isThrownBy(
                        () -> new Incoming.Response<>(ENVELOPE, PROPERTIES, CONTENT, null));
            }

            @Test
            void givenValidInput_thenAllComponentsCorrect() {
                final var requestBuilder = new Outgoing.Request.Builder<>(EXCHANGE, ROUTING_KEY,
                                                                          Message.DeliveryMode.PERSISTENT).setType(
                        String.class).setContent("Halle World");
                final String replyTo = "my-reply-queue";
                requestBuilder.propertiesBuilder().replyTo(replyTo);
                final Incoming.Response<?, Integer> response = new Incoming.Response<>(ENVELOPE, PROPERTIES, CONTENT,
                                                                                       (Outgoing.Request<?>) requestBuilder.build());
                assertSoftly(softly -> {
                    softly.assertThat(response.queue()).isEqualTo(replyTo);

                    softly.assertThat(response.envelope()).isEqualTo(ENVELOPE);
                    softly.assertThat(response.exchange()).isEqualTo(EXCHANGE);
                    softly.assertThat(response.routingKey()).isEqualTo(ROUTING_KEY);

                    softly.assertThat(response.properties()).isEqualTo(PROPERTIES);
                    softly.assertThat(response.deliveryMode()).isEqualTo(Message.DeliveryMode.TRANSIENT);

                    softly.assertThat(response.content()).isEqualTo(CONTENT);
                });
            }
        }
    }
}