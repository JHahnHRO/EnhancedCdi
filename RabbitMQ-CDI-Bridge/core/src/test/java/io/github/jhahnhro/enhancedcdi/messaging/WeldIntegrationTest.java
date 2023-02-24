package io.github.jhahnhro.enhancedcdi.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import javax.enterprise.context.Dependent;
import javax.enterprise.inject.Produces;
import javax.enterprise.util.AnnotationLiteral;
import javax.enterprise.util.TypeLiteral;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import io.github.jhahnhro.enhancedcdi.messaging.impl.RabbitMqExtension;
import io.github.jhahnhro.enhancedcdi.pooled.BlockingPool;
import io.github.jhahnhro.enhancedcdi.util.BeanHelper;
import org.jboss.weld.environment.se.Weld;
import org.jboss.weld.junit5.EnableWeld;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@EnableWeld
class WeldIntegrationTest {

    static final ConnectionFactory CONNECTION_FACTORY = createConnectionFactoryMock();
    @Produces
    @Dependent
    static final Configuration configuration = new Configuration(CONNECTION_FACTORY, Configuration.Retry.NO_RETRY);
    Weld weld = new Weld().disableDiscovery()
            .addPackage(true, Incoming.class)
            .addExtension(new RabbitMqExtension())
            .addBeanClass(BeanHelper.class)
            .addBeanClasses(WeldIntegrationTest.class);
    @WeldSetup
    WeldInitiator w = WeldInitiator.from(weld).build();
    @Mock
    Connection connection;

    private static ConnectionFactory createConnectionFactoryMock() {
        final ConnectionFactory connectionFactory = Mockito.mock(ConnectionFactory.class);

        when(connectionFactory.clone()).thenReturn(connectionFactory);
        when(connectionFactory.isTopologyRecoveryEnabled()).thenReturn(true);
        when(connectionFactory.isAutomaticRecoveryEnabled()).thenReturn(true);

        return connectionFactory;
    }

    @BeforeEach
    void setUp() throws IOException, TimeoutException {
        reset(CONNECTION_FACTORY);
        when(CONNECTION_FACTORY.newConnection()).thenReturn(connection);
    }

    @Test
    void testBeansArePresent() {
        final Publisher publisher = w.select(Publisher.class).get();
        assertThat(publisher).isNotNull();

        final Consumers consumers = w.select(Consumers.class).get();
        assertThat(consumers).isNotNull();

        final Topology topology = w.select(Topology.class, new AnnotationLiteral<Consolidated>() {}).get();
        assertThat(topology).isNotNull();

        verifyNoInteractions(connection);
    }

    @Test
    void testConnectionBean() throws IOException, TimeoutException {
        final Connection actualConnection = w.select(Connection.class).get();

        assertThat(actualConnection).isNotNull();

        actualConnection.isOpen(); // trigger lazy initialization
        verify(CONNECTION_FACTORY).newConnection();
        verify(connection).isOpen();
    }

    @Test
    void testChannelPool() throws IOException, InterruptedException {
        when(connection.createChannel()).thenAnswer(Answers.RETURNS_MOCKS);
        when(connection.getChannelMax()).thenReturn(100);
        final BlockingPool<Channel> channelPool = w.select(new TypeLiteral<BlockingPool<Channel>>() {}).get();

        assertThat(channelPool).isNotNull();
        assertThat(channelPool.size()).isZero();
        assertThat(channelPool.capacity()).isEqualTo(100);

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
                throw new RuntimeException(e);
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
