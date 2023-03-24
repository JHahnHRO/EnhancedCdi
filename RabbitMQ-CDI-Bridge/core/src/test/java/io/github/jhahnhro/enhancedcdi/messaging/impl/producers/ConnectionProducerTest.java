package io.github.jhahnhro.enhancedcdi.messaging.impl.producers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeoutException;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.ShutdownListener;
import com.rabbitmq.client.ShutdownSignalException;
import io.github.jhahnhro.enhancedcdi.messaging.Configuration;
import io.github.jhahnhro.enhancedcdi.messaging.Retry;
import org.jboss.weld.junit.MockBean;
import org.jboss.weld.junit5.EnableWeld;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@EnableWeld
@ExtendWith(MockitoExtension.class)
class ConnectionProducerTest {

    static final Retry RETRY_IMMEDIATELY = Retry.after(Duration.ofMillis(1))
            .giveUpAfter(Duration.of(5, ChronoUnit.SECONDS))
            .giveUpAfterAttemptNr(5);

    @WeldSetup
    WeldInitiator w = WeldInitiator.from(ConnectionProducer.class)
            .addBeans(MockBean.of(createConfiguration(), Configuration.class))
            .build();
    @Mock
    Connection connectionMock;
    @Inject
    Connection connectionBean;
    @Inject
    Instance<Connection> connectionInstance;
    ConnectionFactory connectionFactory;

    private Configuration createConfiguration() {
        return new Configuration(createConnectionFactoryMock(), RETRY_IMMEDIATELY);
    }

    private ConnectionFactory createConnectionFactoryMock() {
        final ConnectionFactory connectionFactory = Mockito.mock(ConnectionFactory.class);

        when(connectionFactory.clone()).thenReturn(connectionFactory);
        when(connectionFactory.isTopologyRecoveryEnabled()).thenReturn(true);
        when(connectionFactory.isAutomaticRecoveryEnabled()).thenReturn(true);

        return connectionFactory;
    }

    @BeforeEach
    void setUp(@Default Configuration configuration) throws IOException {
        this.connectionFactory = configuration.connectionFactory();

        mockShutdownBehaviour();
    }

    private void mockShutdownBehaviour() throws IOException {
        final ShutdownListener[] shutdownListener = new ShutdownListener[1];

        // lenient because the shutdown listener does not get added when creating the connection fails completely
        lenient().doAnswer(invocationOnMock -> {
            shutdownListener[0] = invocationOnMock.getArgument(0);
            return null; // void method
        }).when(connectionMock).addShutdownListener(any());

        // must be lenient, even though it will be called in all tests: The call happens in the disposer method which
        // is called by the WeldJUnitExtension's afterEach method, not by the test method itself.
        lenient().doAnswer(invocationOnMock -> {
            final ShutdownListener listener = shutdownListener[0];
            if (listener != null) {
                listener.shutdownCompleted(
                        new ShutdownSignalException(true, true, new AMQP.Connection.Close.Builder().build(),
                                                    connectionMock));
            }
            return null; // void method
        }).when(connectionMock).close();
    }

    @Test
    void connectionBeanExists() {
        assertThat(connectionBean).isNotNull();
    }

    @Test
    void testConnectionCreation() throws IOException, TimeoutException {
        mockForSuccessfulCreation();

        initializeConnectionBean();

        verify(connectionFactory).newConnection();
        verify(connectionMock).isOpen();
    }

    private void mockForSuccessfulCreation() throws IOException, TimeoutException {
        when(connectionFactory.newConnection()).thenReturn(connectionMock);
    }

    private void initializeConnectionBean() {
        // call any method on the client proxy to trigger lazy initialization
        connectionBean.isOpen();
    }

    @Test
    void testRetry() throws IOException, TimeoutException {
        when(connectionFactory.newConnection()).thenThrow(new TimeoutException("test-exception-1"))
                .thenThrow(new IOException("test-exception-2"))
                .thenReturn(connectionMock);

        initializeConnectionBean();

        verify(connectionFactory, times(3)).newConnection();
    }

    @Test
    void givenConnectionOpen_whenDestroy_thenSucceed() throws IOException, TimeoutException {
        mockForSuccessfulCreation();
        initializeConnectionBean();

        assertThatNoException().isThrownBy(() -> connectionInstance.destroy(connectionBean));
        verify(connectionMock).close();
    }

    @Test
    void givenConnectionAlreadyClosed_whenDestroy_thenSucceed() throws IOException, TimeoutException {
        mockForSuccessfulCreation();
        final var sse = new ShutdownSignalException(true, true, new AMQP.Connection.Close.Builder().build(),
                                                    connectionMock);
        doThrow(new AlreadyClosedException(sse)).when(connectionMock).close();

        initializeConnectionBean();

        assertThatNoException().isThrownBy(() -> connectionInstance.destroy(connectionBean));
        verify(connectionMock).close();
    }
}