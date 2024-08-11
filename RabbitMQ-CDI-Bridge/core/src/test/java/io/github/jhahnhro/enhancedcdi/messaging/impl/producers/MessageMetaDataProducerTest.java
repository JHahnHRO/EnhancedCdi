package io.github.jhahnhro.enhancedcdi.messaging.impl.producers;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.mock;

import java.lang.annotation.Annotation;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.stream.Stream;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BasicProperties;
import com.rabbitmq.client.Envelope;
import io.github.jhahnhro.enhancedcdi.messaging.Exchange;
import io.github.jhahnhro.enhancedcdi.messaging.Header;
import io.github.jhahnhro.enhancedcdi.messaging.Queue;
import io.github.jhahnhro.enhancedcdi.messaging.RoutingKey;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Acknowledgement;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Incoming;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Outgoing;
import io.github.jhahnhro.enhancedcdi.messaging.rpc.RpcNotActiveException;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.enterprise.util.TypeLiteral;
import jakarta.inject.Inject;
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
    private static final AMQP.BasicProperties PROPERTIES = new AMQP.BasicProperties.Builder().deliveryMode(1)
            .replyTo("reply-to-queue")
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
    public static final String QUEUE = "queue";
    public static final Incoming.Cast<byte[]> CAST = new Incoming.Cast<>(QUEUE, ENVELOPE, PROPERTIES, CONTENT);
    private static final Incoming.Request<byte[]> REQUEST = new Incoming.Request<>(QUEUE, ENVELOPE, PROPERTIES,
                                                                                   CONTENT);
    private static final Acknowledgement ACKNOWLEDGEMENT = mock(Acknowledgement.class);
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
                arguments("Queue Name",      QUEUE,                  String.class, new AnnotationLiteral<Queue>() {}),
                arguments("routing key",     "routing.key",          String.class, new AnnotationLiteral<RoutingKey>() {}),
                arguments("BasicProperties", PROPERTIES,    BasicProperties.class, Default.Literal.INSTANCE),
                arguments("Acknowledgement", ACKNOWLEDGEMENT, Acknowledgement.class, Default.Literal.INSTANCE)
                //@formatter:on
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("beans")
    <T> void givenMessage_whenInjectMetaData_thenInjectValue(String beanName, T expectedResult, Class<T> type,
                                                             Annotation qualifier) {
        metaData.setDelivery(REQUEST.queue(), ENVELOPE, PROPERTIES, ACKNOWLEDGEMENT);

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
        metaData.setDelivery(QUEUE, ENVELOPE, PROPERTIES, ACKNOWLEDGEMENT);
        metaData.setMessage(REQUEST);

        final Outgoing.Response.Builder<byte[], String> builder = instance.select(
                new TypeLiteral<Outgoing.Response.Builder<byte[], String>>() {}).get();

        assertThat(builder.getRequest()).isEqualTo(REQUEST);
    }

    @Test
    void givenNonRequestMessage_whenInjectResponseBuilder_thenThrowRpcNotActiveException() {
        metaData.setDelivery(QUEUE, ENVELOPE, PROPERTIES, ACKNOWLEDGEMENT);
        metaData.setMessage(CAST);

        final Instance<Outgoing.Response.Builder<byte[], String>> builderInstance = instance.select(
                new TypeLiteral<>() {});
        assertThatExceptionOfType(RpcNotActiveException.class).isThrownBy(builderInstance::get);
    }

    @Test
    void givenNoMessage_whenInjectResponseBuilder_thenThrowISE() {
        final Instance<Outgoing.Response.Builder<byte[], String>> builderInstance = instance.select(
                new TypeLiteral<>() {});
        assertThatIllegalStateException().isThrownBy(builderInstance::get);
    }

}