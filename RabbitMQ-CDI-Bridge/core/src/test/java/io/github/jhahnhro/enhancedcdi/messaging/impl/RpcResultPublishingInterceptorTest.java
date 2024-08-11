package io.github.jhahnhro.enhancedcdi.messaging.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.io.IOException;
import java.lang.reflect.Type;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Envelope;
import io.github.jhahnhro.enhancedcdi.messaging.Incoming;
import io.github.jhahnhro.enhancedcdi.messaging.Publisher;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Incoming.Request;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Outgoing;
import io.github.jhahnhro.enhancedcdi.messaging.rpc.RpcEndpoint;
import io.github.jhahnhro.enhancedcdi.messaging.rpc.RpcException;
import io.github.jhahnhro.enhancedcdi.messaging.rpc.RpcNotActiveException;
import io.github.jhahnhro.enhancedcdi.messaging.serialization.SerializationException;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.util.TypeLiteral;
import jakarta.inject.Inject;
import org.jboss.weld.environment.se.Weld;
import org.jboss.weld.junit.MockBean;
import org.jboss.weld.junit5.EnableWeld;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

class RpcResultPublishingInterceptorTest {

    private static final Type INCOMING_TYPE =
            new TypeLiteral<io.github.jhahnhro.enhancedcdi.messaging.messages.Incoming<String>>() {}.getType();
    private static final Type INCOMING_REQUEST_TYPE = new TypeLiteral<Request<String>>() {}.getType();

    private static final Type RESPONSE_BUILDER_TYPE =
            new TypeLiteral<Outgoing.Response.Builder<String, Object>>() {}.getType();
    private static final Request<String> INCOMING_REQUEST = createIncomingRequest();

    Weld weld = new Weld().disableDiscovery()
            .addBeanClasses(RpcResultPublishingInterceptor.class)
            .interceptors(RpcResultPublishingInterceptor.class)
            .addBeanClass(RpcEventObserver.class);

    @Inject
    @Incoming
    Event<Object> incomingEvent;

    @Inject
    RpcEventObserver rpcEventObserver;

    @Inject
    Publisher publisherMock;

    private static Request<String> createIncomingRequest() {
        final Envelope envelope = new Envelope(0L, false, "exchange", "routing.key");
        final AMQP.BasicProperties requestProperties = new AMQP.BasicProperties.Builder().deliveryMode(1)
                .replyTo("auto-generated-reply-queue")
                .correlationId("myCorrelationID")
                .build();

        return new Request<>("queue", envelope, requestProperties, "ping");
    }

    @Dependent
    static class RpcEventObserver {

        protected RpcEventObserver() {}

        @RpcEndpoint
        String pingpong(@Observes @Incoming String input) {
            return "pong";
        }

        @RpcEndpoint
        Outgoing.Response<String, Instant> plusThreeHours(@Observes @Incoming Instant input) {
            final var responseBuilder = INCOMING_REQUEST.newResponseBuilder()
                    .setContent(input.plus(3, ChronoUnit.HOURS));
            responseBuilder.propertiesBuilder().deliveryMode(1).type("special");
            return responseBuilder.build();
        }

        @RpcEndpoint
        Outgoing.Response<LocalTime, LocalTime> responseToWrongRequest(@Observes @Incoming LocalTime input) {
            return new Outgoing.Response.Builder<>(INCOMING_REQUEST.withContent(input)).setContent(
                    input.plus(3, ChronoUnit.HOURS)).build();
        }

        @RpcEndpoint
        Outgoing.Response<LocalDate, Object> responseNull(@Observes @Incoming LocalDate input) {
            return null;
        }
    }

    @Nested
    @EnableWeld
    class TestNonRpcHandling {

        @WeldSetup
        WeldInitiator w = WeldInitiator.from(weld)
                .addBeans(MockBean.builder().addType(RESPONSE_BUILDER_TYPE).create(creationalContext -> {
                    throw new RpcNotActiveException();
                }).build(), MockBean.of(Mockito.mock(Publisher.class), Publisher.class))
                .build();

        @Test
        void givenNoRequest_whenRpcEvent_thenCallTheMethod() {
            incomingEvent.select(String.class).fire("ping");
            final String result = rpcEventObserver.pingpong("ping");

            verifyNoInteractions(publisherMock);
            assertThat(result).isEqualTo("pong");
        }

    }

    @Nested
    @EnableWeld
    @ExtendWith(MockitoExtension.class)
    class TestRpcHandling {

        @WeldSetup
        WeldInitiator w = WeldInitiator.from(weld)
                .activate(RequestScoped.class)
                .addBeans(MockBean.of(INCOMING_REQUEST, INCOMING_REQUEST_TYPE, INCOMING_TYPE),
                          MockBean.of(new Outgoing.Response.Builder<>(INCOMING_REQUEST), RESPONSE_BUILDER_TYPE),
                          MockBean.of(Mockito.mock(Publisher.class), Publisher.class))
                .build();
        @Captor
        ArgumentCaptor<Outgoing<?>> response;

        @Test
        void givenRequestExists_whenRpcEvent_thenHandleRpcCall()
                throws IOException, InterruptedException, SerializationException {
            incomingEvent.select(String.class).fire("ping");
            verify(publisherMock).publish(response.capture());
            assertThat(response.getValue().content()).isEqualTo("pong");
        }

        @Test
        void givenRpcMethodThatReturnsOutgoing_whenRpcEvent_thenSendReturnedObject()
                throws IOException, InterruptedException, SerializationException {

            incomingEvent.select(Instant.class).fire(Instant.now());
            verify(publisherMock).publish(response.capture());
            // verify that the returned object is sent, not the manually constructed
            assertThat(response.getValue().properties().getType()).isEqualTo("special");
        }

        @Test
        void givenRpcMethodThatReturnsOutgoingResponseToWrongRequest_whenRpcEvent_thenThrowRpcException() {
            final Event<LocalTime> localTimeEvent = incomingEvent.select(LocalTime.class);
            final LocalTime localTime = LocalTime.now();

            assertThatExceptionOfType(RpcException.class).isThrownBy(() -> localTimeEvent.fire(localTime));

            verifyNoInteractions(publisherMock);
        }

        @Test
        void givenRpcMethodThatReturnsNull_whenRpcEvent_thenThrowRpcException() {
            final Event<LocalDate> localDateEvent = incomingEvent.select(LocalDate.class);
            final LocalDate localDate = LocalDate.now();

            assertThatExceptionOfType(RpcException.class).isThrownBy(() -> localDateEvent.fire(localDate));

            verifyNoInteractions(publisherMock);
        }
    }
}