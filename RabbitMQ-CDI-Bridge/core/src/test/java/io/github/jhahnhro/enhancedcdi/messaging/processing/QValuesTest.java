package io.github.jhahnhro.enhancedcdi.messaging.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import java.util.Set;

import io.github.jhahnhro.enhancedcdi.messaging.processing.QValues.QValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class QValuesTest {

    @Test
    void testParse() {
        final QValues qValues = QValues.parse("a, b;q=0.9, , c;q=0");

        assertThat(qValues.preferredValue()).contains("a");
        assertThat(qValues.acceptableValues()).isEqualTo(List.of(new QValue("a", 1.0f), new QValue("b", 0.9f)));
        assertThat(qValues.unacceptableValues()).isEqualTo(Set.of("c"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"a;q=-1", "a;q=2", "a;q=abc", "a;q~1.0", "a;q=1;s=0"})
    void testUnparseable(final String qValueString) {
        assertThatIllegalArgumentException().isThrownBy(() -> QValues.parse(qValueString));
    }
}