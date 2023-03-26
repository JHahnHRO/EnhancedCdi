package io.github.jhahnhro.enhancedcdi.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import javax.enterprise.context.ContextNotActiveException;
import javax.enterprise.context.Dependent;
import javax.enterprise.context.Destroyed;
import javax.enterprise.context.Initialized;
import javax.enterprise.context.RequestScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Produces;
import javax.enterprise.util.TypeLiteral;
import javax.inject.Inject;
import javax.inject.Singleton;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.Envelope;
import io.github.jhahnhro.enhancedcdi.messaging.impl.RabbitMqExtension;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Acknowledgment;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Message;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Outgoing;
import io.github.jhahnhro.enhancedcdi.pooled.BlockingPool;
import io.github.jhahnhro.enhancedcdi.util.EnhancedInstance;
import org.jboss.weld.environment.se.Weld;
import org.jboss.weld.junit5.EnableWeld;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests basic functionality of all the public CDI beans without actually touching RabbitMQ.
 */
@SuppressWarnings("CdiManagedBeanInconsistencyInspection")
@ExtendWith(MockitoExtension.class)
@Tag("integration-test")
        //@Disabled
class WeldIntegrationTest {

    static final TypeLiteral<io.github.jhahnhro.enhancedcdi.messaging.messages.Incoming<?>> INCOMING_TYPE =
            new TypeLiteral<>() {};
    static final TypeLiteral<Outgoing.Response.Builder<?, ?>> RESPONSE_BUILDER_TYPE = new TypeLiteral<>() {};
    static final ConnectionFactory CONNECTION_FACTORY = createConnectionFactoryMock();
    @Produces
    @Dependent
    static final Configuration configuration = new Configuration(CONNECTION_FACTORY, Retry.NO_RETRY);

    @Produces
    static final AMQP.Exchange.Declare EXCHANGE = new AMQP.Exchange.Declare.Builder().exchange("exchange")
            .type(BuiltinExchangeType.TOPIC.getType())
            .durable(true)
            .build();
    @Produces
    static final AMQP.Queue.Declare QUEUE = new AMQP.Queue.Declare.Builder().queue("queue").durable(false).build();
    public static final String ROUTING_KEY = "routing.key";
    @Produces
    static final AMQP.Queue.Bind QUEUE_BINDING = new AMQP.Queue.Bind.Builder().exchange("exchange")
            .queue("queue")
            .routingKey(ROUTING_KEY)
            .build();

    Weld weld = new Weld().disableDiscovery()
            .addPackage(true, Incoming.class)
            .addExtension(new RabbitMqExtension())
            .addBeanClass(EnhancedInstance.class)
            .addBeanClasses(WeldIntegrationTest.class, Observer.class);


    private static ConnectionFactory createConnectionFactoryMock() {
        final ConnectionFactory connectionFactory = Mockito.mock(ConnectionFactory.class);

        when(connectionFactory.clone()).thenReturn(connectionFactory);
        when(connectionFactory.isTopologyRecoveryEnabled()).thenReturn(true);
        when(connectionFactory.isAutomaticRecoveryEnabled()).thenReturn(true);

        return connectionFactory;
    }

    @BeforeEach
    void setUp() {
        reset(CONNECTION_FACTORY);
    }

    @Singleton
    private static class Observer {
        private final BlockingQueue<Object> incomingMessagePayload = new LinkedBlockingQueue<>();
        private final AtomicInteger requestContextCreated = new AtomicInteger();
        private final AtomicInteger requestContextDestroyed = new AtomicInteger();

        void observeMessage(@Observes @Incoming Object payload) {
            incomingMessagePayload.add(payload);
        }

        void observeRequestContextCreated(@Observes @Initialized(RequestScoped.class) Object ignored) {
            requestContextCreated.incrementAndGet();
        }

        void observeRequestContextDestroyed(@Observes @Destroyed(RequestScoped.class) Object ignored) {
            requestContextDestroyed.incrementAndGet();
        }

        public BlockingQueue<Object> getIncomingMessagePayload() {
            return incomingMessagePayload;
        }
    }

    @Nested
    @EnableWeld
    class TestBeansArePresent {

        @WeldSetup
        WeldInitiator w = WeldInitiator.of(weld);

        @Test
        void publisherBean(Publisher publisher) {
            assertThat(publisher).isNotNull();
            verifyNoInteractions(CONNECTION_FACTORY);
        }

        @Test
        void consumersBean(Consumers consumers) {
            assertThat(consumers).isNotNull();
            verifyNoInteractions(CONNECTION_FACTORY);
        }

        @Test
        void topologyBean(@Consolidated Topology topology) {
            assertThat(topology).isNotNull();
            verifyNoInteractions(CONNECTION_FACTORY);
        }

        @Test
        void connectionBean(Connection connectionBean) {
            assertThat(connectionBean).isNotNull();
            verifyNoInteractions(CONNECTION_FACTORY);
        }

        @Test
        void channelPool() {
            final BlockingPool<Channel> channelPool = w.select(new TypeLiteral<BlockingPool<Channel>>() {}).get();
            assertThat(channelPool).isNotNull();
            verifyNoInteractions(CONNECTION_FACTORY);
        }

        @Test
        void givenRequestContextNotActive_whenSelectIncomingMessage_thenThrow() {
            final var instance = w.select(INCOMING_TYPE);
            assertThatExceptionOfType(ContextNotActiveException.class).isThrownBy(instance::get);
        }

        @Test
        void givenRequestContextNotActive_whenSelectResponseBuilder_thenThrow() {
            final var instance = w.select(RESPONSE_BUILDER_TYPE);
            assertThatExceptionOfType(ContextNotActiveException.class).isThrownBy(instance::get);
        }

        @Test
        void givenRequestContextNotActive_whenSelectAcknowledgement_thenThrow() {
            final var instance = w.select(Acknowledgment.class);
            assertThatExceptionOfType(ContextNotActiveException.class).isThrownBy(instance::get);
        }
    }

    @Nested
    @EnableWeld
    class TestConnectionBean {

        @WeldSetup
        WeldInitiator w = WeldInitiator.of(weld);

        @Mock
        Connection connection;

        @Inject
        Connection connectionBean;

        @BeforeEach
        void setUp() throws IOException, TimeoutException {
            when(CONNECTION_FACTORY.newConnection()).thenReturn(connection);
        }

        @Test
        void testConnectionCreation() throws IOException, TimeoutException {
            assertThat(connectionBean).isNotNull();

            connectionBean.isOpen(); // trigger lazy initialization
            verify(CONNECTION_FACTORY).newConnection();
            verify(connection).isOpen();
        }

    }

    @Nested
    @EnableWeld
    class TestChannelPoolBean {

        @WeldSetup
        WeldInitiator w = WeldInitiator.of(weld);

        @Mock
        Connection connection;

        @Mock
        Channel channel;

        @Inject
        @Consolidated
        Topology topologyBean;

        @BeforeEach
        void setUp() throws IOException, TimeoutException {
            when(CONNECTION_FACTORY.newConnection()).thenReturn(connection);
            when(connection.openChannel()).thenReturn(Optional.ofNullable(channel));
            when(connection.getChannelMax()).thenReturn(100);
        }

        @Test
        @Timeout(value = 1, unit = TimeUnit.SECONDS)
        void testChannelPool() throws IOException, InterruptedException {
            final BlockingPool<Channel> channelPool = w.select(new TypeLiteral<BlockingPool<Channel>>() {}).get();

            assertThat(channelPool).isNotNull();
            assertThat(channelPool.size()).isZero();
            assertThat(channelPool.capacity()).isEqualTo(100 - topologyBean.queueDeclarations().size());

            // no channels created yet
            verify(connection, never()).openChannel();

            // Declare a non-durable, auto-delete, auto-named, exclusive queue
            channelPool.run(Channel::queueDeclare);

            // now there's a channel
            verify(connection).openChannel();
            verify(channel).queueDeclare();
        }
    }

    @Nested
    @EnableWeld
    class TestConsumersBean {

        private final String consumerTag = "server-generated-consumer-tag";
        @WeldSetup
        WeldInitiator w = WeldInitiator.of(weld);
        @Mock
        Connection connection;
        @Mock
        Channel channel;
        @Inject
        Observer observer;
        @Inject
        Consumers consumers;

        @BeforeEach
        void setUp() throws IOException, TimeoutException {
            when(CONNECTION_FACTORY.newConnection()).thenReturn(connection);
            when(connection.openChannel()).thenReturn(Optional.ofNullable(channel));
            mockBasicConsume();

            observer.getIncomingMessagePayload().clear();
        }

        private void mockBasicConsume() throws IOException {
            when(channel.basicConsume(anyString(), anyBoolean(), anyString(), anyBoolean(), anyBoolean(), any(),
                                      any(Consumer.class))).thenAnswer(invocationOnMock -> {
                Consumer consumer = invocationOnMock.getArgument(6);
                consumer.handleConsumeOk(consumerTag);
                return consumerTag;
            });
        }

        @Test
        void testStartAndStop() throws IOException {
            consumers.startReceiving(QUEUE.getQueue());

            verify(channel).exchangeDeclare(EXCHANGE.getExchange(), EXCHANGE.getType(), EXCHANGE.getDurable(),
                                            EXCHANGE.getAutoDelete(), EXCHANGE.getArguments());
            verify(channel).queueDeclare(QUEUE.getQueue(), QUEUE.getDurable(), QUEUE.getExclusive(),
                                         QUEUE.getAutoDelete(), QUEUE.getArguments());
            verify(channel).queueBind(QUEUE_BINDING.getQueue(), QUEUE_BINDING.getExchange(),
                                      QUEUE_BINDING.getRoutingKey(), QUEUE_BINDING.getArguments());
            verify(channel).basicConsume(eq(QUEUE.getQueue()), anyBoolean(), anyString(), anyBoolean(), anyBoolean(),
                                         any(), any(Consumer.class));

            consumers.stopReceiving(QUEUE.getQueue());

            verify(channel).basicCancel(consumerTag);
        }

        @Nested
        class TestReceiving {
            @WeldSetup
            WeldInitiator w = WeldInitiator.of(weld);

            @BeforeEach
            void startReceiving() throws IOException {
                consumers.startReceiving(QUEUE.getQueue());
            }

            @AfterEach
            void stopReceiving() throws IOException {
                consumers.stopReceiving(QUEUE.getQueue());

                assertThat(observer.getIncomingMessagePayload()).as("More incoming messages than expected").isEmpty();
            }

            @Test
            void testReceivingByteArrayMessage() throws IOException, InterruptedException {
                byte[] payload = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9};
                deliverMessage(payload, new AMQP.BasicProperties.Builder().deliveryMode(1)
                        .contentType("application/octet-stream")
                        .build());

                verifyThatMessageWasReceived(payload);
            }

            @Test
            void testReceivingStringMessage() throws IOException, InterruptedException {
                String payload = "Hello world! äöüß";
                deliverMessage(payload.getBytes(StandardCharsets.UTF_8),
                               new AMQP.BasicProperties.Builder().deliveryMode(1).contentType("text/plain").build());

                verifyThatMessageWasReceived(payload);
            }

            @Test
            void testReceivingActivatesRequestScope() throws IOException, InterruptedException {
                byte[] payload = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9};
                deliverMessage(payload, new AMQP.BasicProperties.Builder().deliveryMode(1)
                        .contentType("application/octet-stream")
                        .build());

                waitForIncomingMessage();

                assertThat(observer.requestContextCreated.get()).isEqualTo(1);
                assertThat(observer.requestContextDestroyed.get()).isEqualTo(1);
            }

            private void verifyThatMessageWasReceived(Object payload) throws InterruptedException {
                final Object receivedObject = waitForIncomingMessage();
                assertThat(receivedObject).as("Wrong object received").isEqualTo(payload);
            }

            private Object waitForIncomingMessage() throws InterruptedException {
                final BlockingQueue<Object> receivedObjects = observer.getIncomingMessagePayload();
                final Object receivedObject = receivedObjects.poll(5, TimeUnit.SECONDS);
                assertThat(receivedObject).as("No de-serialized object received after 5s").isNotNull();

                return receivedObject;
            }

            private void deliverMessage(final byte[] payload, final AMQP.BasicProperties properties)
                    throws IOException {
                Consumer consumer = captureConsumer();
                deliverMessage(consumer, properties, payload);
            }

            private Consumer captureConsumer() throws IOException {
                ArgumentCaptor<Consumer> consumerCaptor = ArgumentCaptor.forClass(Consumer.class);
                verify(channel).basicConsume(eq(QUEUE.getQueue()), anyBoolean(), anyString(), anyBoolean(),
                                             anyBoolean(), any(), consumerCaptor.capture());
                return consumerCaptor.getValue();
            }

            private void deliverMessage(Consumer consumer, final AMQP.BasicProperties properties, final byte[] body)
                    throws IOException {
                final Envelope envelope = new Envelope(0L, false, "exchange", ROUTING_KEY);
                consumer.handleDelivery(consumerTag, envelope, properties, body);
            }
        }
    }

    @Nested
    @EnableWeld
    class TestPublisherBean {
        @WeldSetup
        WeldInitiator w = WeldInitiator.of(weld);
        @Mock
        Connection connection;
        @Mock
        Channel channel;
        @Inject
        Publisher publisher;

        @BeforeEach
        void setUp() throws IOException, TimeoutException {
            when(CONNECTION_FACTORY.newConnection()).thenReturn(connection);
            when(connection.openChannel()).thenReturn(Optional.ofNullable(channel));
            when(connection.getChannelMax()).thenReturn(100);
        }

        @Test
        void testPublishingMessagesDeclaresExchanges() throws IOException, InterruptedException {
            Outgoing<String> cast = createSimpleCast("Hello World");

            publisher.send(cast);

            verify(channel).exchangeDeclare(EXCHANGE.getExchange(), EXCHANGE.getType(), EXCHANGE.getDurable(),
                                            EXCHANGE.getAutoDelete(), EXCHANGE.getArguments());
        }

        private <T> Outgoing<T> createSimpleCast(T content) {
            return new Outgoing.Cast.Builder<>(EXCHANGE.getExchange(), ROUTING_KEY,
                                               Message.DeliveryMode.PERSISTENT).setContent(content).build();
        }

        @Test
        void testPublishingStringMessage() throws IOException, InterruptedException {
            final String content = "Hello World";
            Outgoing<String> cast = createSimpleCast(content);

            publisher.send(cast);

            verifyActualPayloadIs(content.getBytes(StandardCharsets.UTF_8));
        }

        @Test
        void testPublishingByteArrayMessage() throws IOException, InterruptedException {
            final byte[] content = new byte[]{12, 3, 4, 5, 6, 7, 8, 9};
            Outgoing<byte[]> cast = createSimpleCast(content);

            publisher.send(cast);

            verifyActualPayloadIs(content);
        }

        private void verifyActualPayloadIs(byte[] content) throws IOException {
            ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
            verify(channel).basicPublish(anyString(), anyString(), any(AMQP.BasicProperties.class),
                                         payloadCaptor.capture());
            final byte[] actualPayload = payloadCaptor.getValue();

            assertThat(actualPayload).isEqualTo(content);
        }
    }
}
