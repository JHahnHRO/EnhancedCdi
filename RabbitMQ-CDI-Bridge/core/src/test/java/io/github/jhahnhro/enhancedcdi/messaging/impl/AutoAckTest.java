package io.github.jhahnhro.enhancedcdi.messaging.impl;

import static org.assertj.core.api.Assertions.*;

import io.github.jhahnhro.enhancedcdi.messaging.messages.Acknowledgment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AutoAckTest {

    @Test
    void ack() {
        assertThatNoException().isThrownBy(AutoAck.INSTANCE::ack);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void reject(boolean requeue) {
        assertThatIllegalStateException().isThrownBy(() -> AutoAck.INSTANCE.reject(requeue));
    }

    @Test
    void getState() {
        assertThat(AutoAck.INSTANCE.getState()).isEqualTo(Acknowledgment.State.ACKNOWLEDGED);
    }
}