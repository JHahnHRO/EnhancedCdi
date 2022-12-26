package io.github.jhahnhro.enhancedcdi.messaging.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import javax.enterprise.context.Dependent;
import javax.enterprise.context.RequestScoped;
import javax.enterprise.event.Event;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Delivery;
import com.rabbitmq.client.Envelope;
import io.github.jhahnhro.enhancedcdi.messaging.Incoming;
import io.github.jhahnhro.enhancedcdi.messaging.Publisher;
import io.github.jhahnhro.enhancedcdi.messaging.impl.producers.ResponseBuilderProducer;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Outgoing;
import io.github.jhahnhro.enhancedcdi.messaging.rpc.RpcEndpoint;
import io.github.jhahnhro.enhancedcdi.messaging.rpc.RpcNotActiveException;
import org.assertj.core.api.Assertions;
import org.jboss.weld.environment.se.Weld;
import org.jboss.weld.junit.MockBean;
import org.jboss.weld.junit5.EnableWeld;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldJunit5Extension;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(WeldJunit5Extension.class)
class RpcResultPublishingInterceptorTest {

    Weld weld = new Weld().addBeanClasses(RpcResultPublishingInterceptor.class, ResponseBuilderProducer.class)
            .interceptors(RpcResultPublishingInterceptor.class)
            .addBeanClass(RpcEventObserver.class);


    @Inject
    @Incoming
    Event<String> incomingString;
    @Inject
    @Incoming
    Event<Integer> incomingInteger;
    @Inject
    @Incoming
    Event<Consumer<Integer>> incomingConsumer;
    @Inject
    RpcEventObserver rpcEventObserver;

    @Inject
    Publisher publisherMock;

    private io.github.jhahnhro.enhancedcdi.messaging.messages.Incoming.Request<String> createIncomingRequest() {
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
        Consumer<Integer> acceptNumber(@Observes @Incoming Consumer<Integer> input) {
            input.accept(42);
            return input;
        }
    }

    @Nested
    @EnableWeld
    class TestNonRpcHandling {

        @WeldSetup
        WeldInitiator w = WeldInitiator.from(weld)
                .addBeans(MockBean.of(Mockito.mock(Publisher.class), Publisher.class))
                .build();

        @Test
        void givenRequestContextNotActiveAndModeEqualsProceed_whenRpcEvent_thenCallTheMethod() {
            incomingString.fire("ping");
            final String result = rpcEventObserver.pingpong("ping");

            verifyNoInteractions(publisherMock);
            assertThat(result).isEqualTo("pong");
        }

        @Test
        void givenRequestContextNotActiveAndModeEqualsThrow_whenRpcEvent_thenThrowException() {
            assertThatThrownBy(() -> incomingInteger.fire(42)).isInstanceOf(RpcNotActiveException.class);
            assertThatThrownBy(() -> rpcEventObserver.timesTwo(42)).isInstanceOf(RpcNotActiveException.class);

            verifyNoInteractions(publisherMock);
        }


        @Test
        void givenRequestContextNotActiveAndModeEqualsDoNotProceed_whenRpcEvent_thenDoNotProceed() {
            Consumer<Integer> consumerMock = mock(Consumer.class);

            incomingConsumer.fire(consumerMock);
            final Consumer<Integer> result = rpcEventObserver.acceptNumber(consumerMock);

            verifyNoInteractions(publisherMock);
            verifyNoInteractions(consumerMock);

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
                .addBeans(MockBean.of(Mockito.mock(Publisher.class), Publisher.class))
                .build();
        @Captor
        ArgumentCaptor<Outgoing.Response<String, String>> response;

        @Inject
        ResponseBuilderProducer responseBuilderProducer;

        @Test
        void givenNoResponseBuilder_whenRpcEvent_thenHandleNonRpcCall() {
            incomingString.fire("ping");
            Mockito.verifyNoInteractions(publisherMock);
        }

        @Test
        void givenResponseBuilderExists_whenRpcEvent_thenHandleRpcCall() throws IOException, InterruptedException {
            responseBuilderProducer.createResponseBuilderFor(createIncomingRequest());
            incomingString.fire("ping");
            verify(publisherMock).send(response.capture(), eq(String.class));
            Assertions.assertThat(response.getValue().content()).isEqualTo("pong");
        }

    }
}