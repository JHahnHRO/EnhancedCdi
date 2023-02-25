package io.github.jhahnhro.enhancedcdi.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.enterprise.context.Dependent;
import javax.enterprise.context.RequestScoped;
import javax.enterprise.context.spi.Context;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.BeanManager;
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
import io.github.jhahnhro.enhancedcdi.messaging.messages.Outgoing;
import io.github.jhahnhro.enhancedcdi.pooled.BlockingPool;
import io.github.jhahnhro.enhancedcdi.util.BeanHelper;
import org.jboss.weld.environment.se.Weld;
import org.jboss.weld.junit5.EnableWeld;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
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
class WeldIntegrationTest {

    static final TypeLiteral<io.github.jhahnhro.enhancedcdi.messaging.messages.Incoming<?>> INCOMING_TYPE =
            new TypeLiteral<>() {};
    static final TypeLiteral<Outgoing.Response.Builder<?, ?>> RESPONSE_BUILDER_TYPE = new TypeLiteral<>() {};
    static final ConnectionFactory CONNECTION_FACTORY = createConnectionFactoryMock();
    @Produces
    @Dependent
    static final Configuration configuration = new Configuration(CONNECTION_FACTORY, Configuration.Retry.NO_RETRY);

    @Produces
    static final AMQP.Exchange.Declare exchange = new AMQP.Exchange.Declare.Builder().exchange("exchange")
            .type(BuiltinExchangeType.TOPIC.getType())
            .durable(true)
            .build();
    @Produces
    static final AMQP.Queue.Declare queue = new AMQP.Queue.Declare.Builder().queue("queue").durable(false).build();
    public static final String ROUTING_KEY = "routing.key";
    @Produces
    static final AMQP.Queue.Bind queueBinding = new AMQP.Queue.Bind.Builder().exchange("exchange")
            .queue("queue")
            .routingKey(ROUTING_KEY)
            .build();

    Weld weld = new Weld().disableDiscovery()
            .addPackage(true, Incoming.class)
            .addExtension(new RabbitMqExtension())
            .addBeanClass(BeanHelper.class)
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

        void observe(@Observes @Incoming Object payload) {
            incomingMessagePayload.add(payload);
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
        void givenRequestContextNotActive_whenSelectIncomingMessage_thenThrowISE() {
            final var instance = w.select(INCOMING_TYPE);
            assertThatIllegalStateException().isThrownBy(instance::get);
        }

        @Test
        void givenRequestContextNotActive_whenSelectResponseBuilder_thenThrowISE() {
            final var instance = w.select(RESPONSE_BUILDER_TYPE);
            assertThatIllegalStateException().isThrownBy(instance::get);
        }

        @Test
        void givenRequestContextNotActive_whenSelectAcknowledgement_thenThrowISE() {
            final var instance = w.select(Acknowledgment.class);
            assertThatIllegalStateException().isThrownBy(instance::get);
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
            when(connection.createChannel()).thenReturn(channel);
            when(connection.getChannelMax()).thenReturn(100);
        }

        @Test
        void testChannelPool() throws IOException, InterruptedException {
            final BlockingPool<Channel> channelPool = w.select(new TypeLiteral<BlockingPool<Channel>>() {}).get();

            assertThat(channelPool).isNotNull();
            assertThat(channelPool.size()).isZero();
            assertThat(channelPool.capacity()).isEqualTo(100 - topologyBean.queueDeclarations().size());

            CountDownLatch afterStart = new CountDownLatch(2);
            CountDownLatch beforeEnd = new CountDownLatch(1);
            final Runnable runnable = () -> {
                try {
                    channelPool.withItem(channel -> {
                        afterStart.countDown();

                        beforeEnd.await();
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            };

            // start two thread that both occupy one channel.
            final Thread t1 = new Thread(runnable);
            t1.start();
            final Thread t2 = new Thread(runnable);
            t2.start();

            // make sure both threads have started and acquired a channel
            afterStart.await();
            // make sure threads become unblocked and wait for them to shut down
            beforeEnd.countDown();
            t1.join();
            t2.join();

            verify(connection, times(2)).createChannel();
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
            when(connection.createChannel()).thenReturn(channel);
            mockBasicConsume();

            observer.getIncomingMessagePayload().clear();
        }

        private void mockBasicConsume() throws IOException {
            when(channel.basicConsume(anyString(), anyBoolean(), any(Consumer.class))).thenAnswer(invocationOnMock -> {
                Consumer consumer = invocationOnMock.getArgument(2);
                consumer.handleConsumeOk(consumerTag);
                return consumerTag;
            });
        }

        @Test
        void testStartAndStop() throws IOException {

            consumers.startReceiving(queue.getQueue());

            verify(channel).queueDeclare(queue.getQueue(), queue.getDurable(), queue.getExclusive(),
                                         queue.getAutoDelete(), queue.getArguments());
            verify(channel).basicConsume(eq(queue.getQueue()), anyBoolean(), any(Consumer.class));

            consumers.stopReceiving(queue.getQueue());

            verify(channel).basicCancel(consumerTag);
        }


        @Test
        void testReceivingByteArrayMessage() throws IOException, InterruptedException {
            consumers.startReceiving(queue.getQueue());

            byte[] payload = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9};
            deliverMessage(payload, new AMQP.BasicProperties.Builder().contentType("application/octet-stream").build());

            final BlockingQueue<Object> receivedObjects = observer.getIncomingMessagePayload();
            final Object receivedObject = receivedObjects.poll(5, TimeUnit.SECONDS);

            assertThat(receivedObject).as("No de-serialized object received after 5s")
                    .isNotNull()
                    .as("Wrong object received")
                    .isEqualTo(payload);
            assertThat(receivedObjects).isEmpty();

            consumers.stopReceiving(queue.getQueue());
        }

        @Test
        void testReceivingStringMessage() throws IOException, InterruptedException {
            consumers.startReceiving(queue.getQueue());

            String payload = "Hello world! äöüß";
            deliverMessage(payload.getBytes(StandardCharsets.UTF_8),
                           new AMQP.BasicProperties.Builder().contentType("text/plain").build());

            final BlockingQueue<Object> receivedObjects = observer.getIncomingMessagePayload();
            final Object receivedObject = receivedObjects.poll(5, TimeUnit.SECONDS);

            assertThat(receivedObject).as("No de-serialized object received after 5s")
                    .isNotNull()
                    .as("Wrong object received")
                    .isEqualTo(payload);
            assertThat(receivedObjects).isEmpty();

            consumers.stopReceiving(queue.getQueue());
        }

        private void deliverMessage(final byte[] payload, final AMQP.BasicProperties properties) throws IOException {
            Consumer consumer = captureConsumer();
            deliverMessage(consumer, properties, payload);
        }

        private Consumer captureConsumer() throws IOException {
            ArgumentCaptor<Consumer> consumerCaptor = ArgumentCaptor.forClass(Consumer.class);
            verify(channel).basicConsume(eq(queue.getQueue()), anyBoolean(), consumerCaptor.capture());
            return consumerCaptor.getValue();
        }

        private void deliverMessage(Consumer consumer, final AMQP.BasicProperties properties, final byte[] body)
                throws IOException {
            final Envelope envelope = new Envelope(0L, false, "exchange", ROUTING_KEY);
            consumer.handleDelivery(consumerTag, envelope, properties, body);
        }

        @Disabled("The request scope is active on a different thread!")
        @Test
        void testReceivingActivatesRequestScope(BeanManager beanManager) throws IOException {
            consumers.startReceiving(queue.getQueue());

            byte[] payload = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9};
            deliverMessage(payload, new AMQP.BasicProperties.Builder().contentType("application/octet-stream").build());

            final Context context = beanManager.getContext(RequestScoped.class);
            assertThat(context).isNotNull();

            consumers.stopReceiving(queue.getQueue());
        }


        @Disabled("The request scope is active on a different thread!")
        @Test
        void testReceivingCreatesBeans() throws IOException {
            consumers.startReceiving(queue.getQueue());

            byte[] payload = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9};
            deliverMessage(payload, new AMQP.BasicProperties.Builder().contentType("application/octet-stream").build());

            final var incoming = w.select(INCOMING_TYPE).get();
            assertThat(incoming).isNotNull();

            consumers.stopReceiving(queue.getQueue());
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
            when(connection.createChannel()).thenReturn(channel);
            when(connection.getChannelMax()).thenReturn(100);
        }

        @Test
        void testPublishingMessagesDeclaresExchanges() throws IOException, InterruptedException {
            Outgoing<String> cast = createSimpleCast("Hello World");

            publisher.send(cast);

            verify(channel).exchangeDeclare(exchange.getExchange(), exchange.getType(), exchange.getDurable(),
                                            exchange.getAutoDelete(), exchange.getArguments());
        }

        private <T> Outgoing<T> createSimpleCast(T content) {
            return new Outgoing.Cast.Builder<>(exchange.getExchange(), ROUTING_KEY).setContent(content).build();
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
