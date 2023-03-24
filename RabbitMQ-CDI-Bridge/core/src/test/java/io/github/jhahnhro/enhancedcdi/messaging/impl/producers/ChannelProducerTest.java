package io.github.jhahnhro.enhancedcdi.messaging.impl.producers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.enterprise.util.AnnotationLiteral;
import javax.inject.Inject;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import io.github.jhahnhro.enhancedcdi.messaging.Consolidated;
import io.github.jhahnhro.enhancedcdi.messaging.Topology;
import io.github.jhahnhro.enhancedcdi.pooled.BlockingPool;
import org.jboss.weld.junit.MockBean;
import org.jboss.weld.junit5.EnableWeld;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@EnableWeld
@ExtendWith(MockitoExtension.class)
class ChannelProducerTest {

    private static final int MAX_CHANNEL_NR = 100;

    @WeldSetup
    WeldInitiator w = WeldInitiator.from(ChannelProducer.class)
            .addBeans(MockBean.builder()
                              .creating(new Topology.Builder().build())
                              .types(Topology.class)
                              .addQualifier(new AnnotationLiteral<Consolidated>() {})
                              .build())
            .addBeans(MockBean.of(mock(Connection.class), Connection.class))
            .build();

    @Inject
    Connection connection;

    @Inject
    BlockingPool<Channel> channelPool;

    @Mock
    Channel channel;

    @BeforeEach
    void setUp() throws IOException {
        when(connection.openChannel()).thenReturn(Optional.of(channel));
        when(connection.getChannelMax()).thenReturn(MAX_CHANNEL_NR);
    }

    @Test
    void channelPoolExists() {
        assertThat(channelPool).isNotNull();
    }

    @Test
    void initialSizeIsZero() {
        assertThat(channelPool.size()).isZero();
    }

    @Test
    void capacity() {
        assertThat(channelPool.capacity()).isEqualTo(MAX_CHANNEL_NR);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testAcquiringChannels() throws IOException, InterruptedException {
        // TODO this should be a unit test for LazyBlockingPool

        when(channel.isOpen()).thenReturn(true);

        assertThat(channelPool.size()).isZero();

        CountDownLatch afterStart = new CountDownLatch(2);
        CountDownLatch beforeEnd = new CountDownLatch(1);
        final Runnable runnable = () -> {
            try {
                channelPool.run(channel -> {
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
        final int sizeWhenInUse = channelPool.size();
        // make sure threads become unblocked and wait for them to shut down
        beforeEnd.countDown();
        t1.join(2000);
        t2.join(2000);

        verify(connection, times(2)).openChannel();
        assertThat(sizeWhenInUse).isGreaterThanOrEqualTo(2);
    }
}