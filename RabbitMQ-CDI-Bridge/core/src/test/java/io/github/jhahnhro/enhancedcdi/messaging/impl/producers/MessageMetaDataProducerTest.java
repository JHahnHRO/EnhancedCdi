package io.github.jhahnhro.enhancedcdi.messaging.impl.producers;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.mock;

import java.lang.annotation.Annotation;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.stream.Stream;
import javax.enterprise.context.RequestScoped;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.Instance;
import javax.enterprise.util.AnnotationLiteral;
import javax.enterprise.util.TypeLiteral;
import javax.inject.Inject;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BasicProperties;
import com.rabbitmq.client.Delivery;
import com.rabbitmq.client.Envelope;
import io.github.jhahnhro.enhancedcdi.messaging.Exchange;
import io.github.jhahnhro.enhancedcdi.messaging.Header;
import io.github.jhahnhro.enhancedcdi.messaging.Queue;
import io.github.jhahnhro.enhancedcdi.messaging.RoutingKey;
import io.github.jhahnhro.enhancedcdi.messaging.Serialized;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Acknowledgment;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Incoming;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Outgoing;
import org.jboss.weld.junit5.EnableWeld;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@EnableWeld
class MessageMetaDataProducerTest {

    public static final byte[] CONTENT = new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8};
    public static final Envelope ENVELOPE = new Envelope(4711L, false, "exchange", "routing.key");
    private static final AMQP.BasicProperties PROPERTIES = new AMQP.BasicProperties.Builder().replyTo("reply-to-queue")
            .correlationId("correlation-id")
            // @formatter:off
            .headers(Map.ofEntries(
                    entry("bool",       true                 ),
                    entry("byte",       (byte) 42               ),
                    entry("short",      (short) 42              ),
                    entry("int",        42                   ),
                    entry("long",       42L                  ),
                    entry("float",      47.11f               ),
                    entry("double",     47.11                ),
                    entry("String",     "Hello World"        ),
                    entry("Instant",    Date.from(Instant.EPOCH)),
                    entry("BigDecimal", BigDecimal.valueOf(42)  )
            ))
            // @formatter:on
            .build();
    private static final Delivery DELIVERY = new Delivery(ENVELOPE, PROPERTIES, CONTENT);
    private static final Incoming.Request<byte[]> REQUEST = new Incoming.Request<>(DELIVERY, "queue", CONTENT);
    public static final Incoming.Cast<byte[]> CAST = new Incoming.Cast<>(DELIVERY, "queue", CONTENT);
    private static final Acknowledgment ACKNOWLEDGMENT = mock(Acknowledgment.class);
    @WeldSetup
    WeldInitiator w = WeldInitiator.from(MessageMetaDataProducer.class).activate(RequestScoped.class).build();

    @Inject
    MessageMetaDataProducer metaData;
    @Inject
    @Any
    Instance<Object> instance;

    static Stream<Arguments> beans() {
        return Stream.of(
                //@formatter:off
                arguments("Bool header",       true,                      boolean.class, new Header.Literal("bool")),
                arguments("Byte header",       (byte) 42,                    byte.class, new Header.Literal("byte")),
                arguments("Short header",      (short) 42,                  short.class, new Header.Literal("short")),
                arguments("Integer header",    42,                            int.class, new Header.Literal("int")),
                arguments("Long header",       42L,                          long.class, new Header.Literal("long")),
                arguments("Float header",      47.11f,                      float.class, new Header.Literal("float")),
                arguments("Double header",     47.11,                      double.class, new Header.Literal("double")),
                arguments("String header",     "Hello World",              String.class, new Header.Literal("String")),
                arguments("Instant header",    Instant.EPOCH,             Instant.class, new Header.Literal("Instant")),
                arguments("BigDecimal header", BigDecimal.valueOf(42), BigDecimal.class, new Header.Literal("BigDecimal")),
                arguments("Unknown Bool header",       null,     Boolean.class, new Header.Literal("unknown")),
                arguments("Unknown Byte header",       null,        Byte.class, new Header.Literal("unknown")),
                arguments("Unknown Short header",      null,       Short.class, new Header.Literal("unknown")),
                arguments("Unknown Integer header",    null,     Integer.class, new Header.Literal("unknown")),
                arguments("Unknown Long header",       null,        Long.class, new Header.Literal("unknown")),
                arguments("Unknown Float header",      null,       Float.class, new Header.Literal("unknown")),
                arguments("Unknown Double header",     null,      Double.class, new Header.Literal("unknown")),
                arguments("Unknown String header",     null,      String.class, new Header.Literal("unknown")),
                arguments("Unknown Instant header",    null,     Instant.class, new Header.Literal("unknown")),
                arguments("Unknown BigDecimal header", null,  BigDecimal.class, new Header.Literal("unknown")),
                arguments("Exchange Name",   "exchange",             String.class, new AnnotationLiteral<Exchange>() {}),
                arguments("Queue Name",      "queue",                String.class, new AnnotationLiteral<Queue>() {}),
                arguments("routing key",     "routing.key",          String.class, new AnnotationLiteral<RoutingKey>() {}),
                arguments("BasicProperties", PROPERTIES,    BasicProperties.class, Default.Literal.INSTANCE),
                arguments("Acknowledgement", ACKNOWLEDGMENT, Acknowledgment.class, Default.Literal.INSTANCE)
                //@formatter:on
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("beans")
    <T> void givenMessage_whenInjectMetaData_thenInjectValue(String beanName, T expectedResult, Class<T> type,
                                                             Annotation qualifier) {
        metaData.setRawMessage(REQUEST, ACKNOWLEDGMENT);

        final T actualResult = instance.select(type, qualifier).get();
        assertThat(actualResult).isEqualTo(expectedResult);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("beans")
    <T> void givenNoMessage_whenInjectMetaData_thenThrowISE(String beanName, T ignored, Class<T> type,
                                                            Annotation qualifier) {
        final Instance<T> tInstance = instance.select(type, qualifier);

        assertThatIllegalStateException().isThrownBy(tInstance::get);
    }


    @Test
    void givenMessage_whenInjectResponseBuilder_thenSucceed() {
        metaData.setRawMessage(REQUEST, ACKNOWLEDGMENT);

        final Outgoing.Response.Builder<byte[], String> builder = instance.select(
                new TypeLiteral<Outgoing.Response.Builder<byte[], String>>() {}).get();

        assertThat(builder.getRequest()).isEqualTo(REQUEST);
    }

    @Test
    void givenNonRequestMessage_whenInjectResponseBuilder_thenSucceed() {
        metaData.setRawMessage(CAST, ACKNOWLEDGMENT);

        final Instance<Outgoing.Response.Builder<byte[], String>> builderInstance = instance.select(
                new TypeLiteral<>() {});
        assertThatIllegalStateException().isThrownBy(builderInstance::get);
    }

    @Test
    void givenNoMessage_whenInjectResponseBuilder_thenSucceed() {
        final Instance<Outgoing.Response.Builder<byte[], String>> builderInstance = instance.select(
                new TypeLiteral<>() {});
        assertThatIllegalStateException().isThrownBy(builderInstance::get);
    }

    @Test
    void givenMessage_whenInjectRawMessage_thenInjectCorrectMessage() {
        metaData.setRawMessage(REQUEST, ACKNOWLEDGMENT);
        final Incoming<byte[]> actualValue = instance.select(new TypeLiteral<Incoming<byte[]>>() {},
                                                             Serialized.Literal.INSTANCE).get();

        assertThat(actualValue).isEqualTo(REQUEST);
    }

    @Test
    void givenNoMessage_whenInjectRawMessage_thenThrowISE() {
        final Instance<Incoming<byte[]>> incomingInstance = instance.select(new TypeLiteral<>() {},
                                                                            Serialized.Literal.INSTANCE);
        assertThatIllegalStateException().isThrownBy(incomingInstance::get);
    }

    @Test
    void givenRawMessage_whenInjectTypedMessage_thenThrowISE() {
        metaData.setRawMessage(REQUEST, ACKNOWLEDGMENT);
        final Instance<Incoming<String>> incomingInstance = instance.select(new TypeLiteral<>() {});
        assertThatIllegalStateException().isThrownBy(incomingInstance::get);
    }

    @Test
    void givenTypedMessage_whenInjectTypedMessage_thenInjectCorrect() {
        metaData.setRawMessage(REQUEST, ACKNOWLEDGMENT);
        final Incoming<String> deserialized = REQUEST.withContent("Hello World");
        metaData.setDeserializedMessage(deserialized);

        final Incoming<String> actual = instance.select(new TypeLiteral<Incoming<String>>() {}).get();
        assertThat(actual).isEqualTo(deserialized);
    }
}