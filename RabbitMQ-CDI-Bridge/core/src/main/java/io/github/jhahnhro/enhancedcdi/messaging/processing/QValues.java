package io.github.jhahnhro.enhancedcdi.messaging.processing;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;

public class QValues {
    /**
     * The acceptable values, ordered highest weight first
     */
    private final List<QValue> acceptableValues;
    private final Set<String> unacceptableValues;

    private QValues(Collection<QValue> qValues) {
        final Map<Boolean, List<QValue>> qValuesMap = qValues.stream()
                .collect(Collectors.partitioningBy(qValue -> qValue.weight() > 0.0));

        this.acceptableValues = qValuesMap.get(true)
                .stream()
                .sorted(Comparator.comparing(QValue::weight).reversed())
                .toList();

        this.unacceptableValues = qValuesMap.get(false).stream().map(QValue::value).collect(Collectors.toSet());
    }

    public static QValues parse(String qValueString) {
        final Map<String, QValue> byName = Arrays.stream(qValueString.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(QValue::parse)
                .collect(Collectors.toMap(QValue::value, Function.identity(),
                                          BinaryOperator.maxBy(Comparator.comparing(QValue::weight))));

        return new QValues(byName.values());
    }

    public List<QValue> getAcceptableValues() {
        return acceptableValues;
    }

    public Set<String> getUnacceptableValues() {
        return unacceptableValues;
    }

    public Optional<String> getPreferredValue() {
        return acceptableValues.stream().map(QValue::value).findFirst();
    }

    public record QValue(String value, float weight) {
        public QValue {
            Objects.requireNonNull(value);
            if (weight < 0.0f || weight > 1.0f) {
                throw new IllegalArgumentException("Weight must be between 0 and 1");
            }
        }

        public static QValue parse(String s) {
            final String[] split = s.split(";");
            if (split.length == 1) {
                return new QValue(split[0], 1.0f);
            } else if (split.length == 2) {
                final String s1 = split[1].trim();
                if (s1.startsWith("q=")) {
                    return new QValue(split[0], Float.parseFloat(s1.substring(2)));
                }
            }
            throw new IllegalArgumentException("Cannot parse " + s);
        }
    }
}
