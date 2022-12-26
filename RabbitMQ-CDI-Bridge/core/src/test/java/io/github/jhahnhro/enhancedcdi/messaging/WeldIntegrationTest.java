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
import org.junit.jupiter.api.Disabled;
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
    Weld weld = new Weld().addPackage(true, Incoming.class)
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
        when(CONNECTION_FACTORY.newConnection()).thenReturn(connection);
    }

    @Test
    void testBeansArePresent() throws IOException, TimeoutException {
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
    }

    @Test
    @Disabled("Some mysterious bug to do with weld's proxy class for BlockingPool, AutoCloseable and modules")
    void testChannelPool() throws IOException {
        when(connection.createChannel()).thenAnswer(Answers.RETURNS_MOCKS);
        when(connection.getChannelMax()).thenReturn(100);
        final BlockingPool<Channel> channelPool = w.select(new TypeLiteral<BlockingPool<Channel>>() {}).get();

        assertThat(channelPool).isNotNull();
        assertThat(channelPool.size()).isZero();
        assertThat(channelPool.capacity()).isEqualTo(100);

        CountDownLatch cdl = new CountDownLatch(1);
        final Runnable runnable = () -> {
            try {
                channelPool.withItem(channel -> {
                    cdl.await();
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        };

        // start two thread that both occupy one channel.
        new Thread(runnable).start();
        new Thread(runnable).start();

        verify(connection, times(2)).createChannel();

        cdl.countDown();
    }
}
