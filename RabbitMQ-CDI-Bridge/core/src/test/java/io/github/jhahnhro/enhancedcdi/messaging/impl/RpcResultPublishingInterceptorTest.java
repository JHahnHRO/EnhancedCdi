package io.github.jhahnhro.enhancedcdi.messaging.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import javax.enterprise.context.Dependent;
import javax.enterprise.context.RequestScoped;
import javax.enterprise.event.Event;
import javax.enterprise.event.Observes;
import javax.enterprise.util.TypeLiteral;
import javax.inject.Inject;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Delivery;
import com.rabbitmq.client.Envelope;
import io.github.jhahnhro.enhancedcdi.messaging.Incoming;
import io.github.jhahnhro.enhancedcdi.messaging.Publisher;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Outgoing;
import io.github.jhahnhro.enhancedcdi.messaging.rpc.RpcEndpoint;
import io.github.jhahnhro.enhancedcdi.messaging.rpc.RpcNotActiveException;
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
    private static final Type INCOMING_REQUEST_TYPE =
            new TypeLiteral<io.github.jhahnhro.enhancedcdi.messaging.messages.Incoming.Request<String>>() {}.getType();

    private static final Type RESPONSE_BUILDER_TYPE =
            new TypeLiteral<Outgoing.Response.Builder<String, Object>>() {}.getType();
    private static final io.github.jhahnhro.enhancedcdi.messaging.messages.Incoming.Request<String> INCOMING_REQUEST
            = createIncomingRequest();

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

    private static io.github.jhahnhro.enhancedcdi.messaging.messages.Incoming.Request<String> createIncomingRequest() {
        final Envelope envelope = new Envelope(0L, false, "exchange", "routing.key");
        final AMQP.BasicProperties requestProperties = new AMQP.BasicProperties.Builder().replyTo(
                "auto-generated-reply-queue").correlationId("myCorrelationID").build();
        final Delivery delivery = new Delivery(envelope, requestProperties, "ping".getBytes(StandardCharsets.UTF_8));
        return new io.github.jhahnhro.enhancedcdi.messaging.messages.Incoming.Request<>(delivery, "queue", "ping");
    }

    @Dependent
    static class RpcEventObserver {

        protected RpcEventObserver() {}

        @RpcEndpoint(nonRpcInvocations = RpcEndpoint.NonRpcInvocation.PROCEED)
        String pingpong(@Observes @Incoming String input) {
            return "pong";
        }

        @RpcEndpoint(nonRpcInvocations = RpcEndpoint.NonRpcInvocation.THROW)
        Integer timesTwo(@Observes @Incoming Integer input) {
            return 2 * input;
        }

        @RpcEndpoint(nonRpcInvocations = RpcEndpoint.NonRpcInvocation.DO_NOT_PROCEED)
        Boolean negate(@Observes @Incoming Boolean input) {
            return !input;
        }

        @RpcEndpoint
        Outgoing.Response<String, Instant> plusThreeHours(@Observes @Incoming Instant input) {
            final var responseBuilder = new Outgoing.Response.Builder<String, Instant>(INCOMING_REQUEST).setContent(
                    input.plus(3, ChronoUnit.HOURS));
            responseBuilder.propertiesBuilder().type("special");
            return responseBuilder.build();
        }

        @RpcEndpoint
        Outgoing.Response<LocalTime, LocalTime> responseToWrongRequest(@Observes @Incoming LocalTime input) {
            return new Outgoing.Response.Builder<>(INCOMING_REQUEST.withContent(input)).setContent(
                    input.plus(3, ChronoUnit.HOURS)).build();
        }
    }

    @Nested
    @EnableWeld
    class TestNonRpcHandling {

        @WeldSetup
        WeldInitiator w = WeldInitiator.from(weld)
                .addBeans(MockBean.of(null, INCOMING_REQUEST_TYPE, INCOMING_TYPE),
                          MockBean.of(null, RESPONSE_BUILDER_TYPE),
                          MockBean.of(Mockito.mock(Publisher.class), Publisher.class))
                .build();

        @Test
        void givenNoRequestAndModeEqualsProceed_whenRpcEvent_thenCallTheMethod() {
            incomingEvent.select(String.class).fire("ping");
            final String result = rpcEventObserver.pingpong("ping");

            verifyNoInteractions(publisherMock);
            assertThat(result).isEqualTo("pong");
        }

        @Test
        void givenNoRequestAndModeEqualsThrow_whenRpcEvent_thenThrowException() {
            final Event<Integer> incomingInteger = incomingEvent.select(Integer.class);
            assertThatThrownBy(() -> incomingInteger.fire(42)).isInstanceOf(RpcNotActiveException.class);
            assertThatThrownBy(() -> rpcEventObserver.timesTwo(42)).isInstanceOf(RpcNotActiveException.class);

            verifyNoInteractions(publisherMock);
        }


        @Test
        void givenNoRequestAndModeEqualsDoNotProceed_whenRpcEvent_thenDoNotProceed() {
            incomingEvent.select(Boolean.class).fire(false);
            final Boolean result = rpcEventObserver.negate(false);

            verifyNoInteractions(publisherMock);

            assertThat(result).isNull();
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
        void givenRequestExists_whenRpcEvent_thenHandleRpcCall() throws IOException, InterruptedException {
            incomingEvent.select(String.class).fire("ping");
            verify(publisherMock).send(response.capture());
            assertThat(response.getValue().content()).isEqualTo("pong");
        }

        @Test
        void givenRpcMethodThatReturnsOutgoing_whenRpcEvent_thenSendReturnedObject()
                throws IOException, InterruptedException {

            incomingEvent.select(Instant.class).fire(Instant.now());
            verify(publisherMock).send(response.capture());
            // verify that the returned object is sent, not the manually constructed
            assertThat(response.getValue().properties().getType()).isEqualTo("special");
        }

        @Test
        void givenRpcMethodThatReturnsOutgoingResponseToWrongRequest_whenRpcEvent_thenThrowIllegalStateException() {
            final Event<LocalTime> localTimeEvent = incomingEvent.select(LocalTime.class);

            assertThatIllegalStateException().isThrownBy(() -> localTimeEvent.fire(LocalTime.now()));

            verifyNoInteractions(publisherMock);
        }
    }
}