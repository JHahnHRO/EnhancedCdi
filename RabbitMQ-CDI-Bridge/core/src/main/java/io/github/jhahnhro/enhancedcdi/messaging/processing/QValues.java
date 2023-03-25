package io.github.jhahnhro.enhancedcdi.messaging.processing;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents a collection of "weighted" values.
 */
public record QValues(List<QValue> acceptableValues, Set<String> unacceptableValues) {

    /**
     * @param acceptableValues   The acceptable values, ordered highest weight first. Must not be null, must not contain
     *                           null. Must not contain elements that are {@link QValue#isAcceptable() unacceptable} or
     *                           whose {@link QValue#value() name} is contained in {@code unacceptableValues}.
     * @param unacceptableValues the unacceptable values. Must not be null, must not contain null.
     * @throws NullPointerException     if either collection is null or contains null.
     * @throws IllegalArgumentException if the list of acceptable values contains elements that are
     *                                  {@link QValue#isAcceptable() unacceptable} or whose {@link QValue#value() name}
     *                                  is contained in {@code unacceptableValues}.
     */
    public QValues {
        acceptableValues = merge(acceptableValues);
        unacceptableValues = Set.copyOf(unacceptableValues);

        if (acceptableValues.stream().anyMatch(qValue -> !qValue.isAcceptable()) || acceptableValues.stream()
                .map(QValue::value)
                .anyMatch(unacceptableValues::contains)) {
            throw new IllegalArgumentException("The given list of QValues contains unacceptable values");
        }
    }

    /**
     * Parses a QValues instance from a string.
     *
     * @param qValueString
     * @return
     * @throws IllegalArgumentException if the string cannot be parsed.
     */
    public static QValues parse(String qValueString) {
        return of(parseToList(qValueString));
    }

    private static List<QValue> parseToList(String qValueString) {
        return Arrays.stream(qValueString.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(QValue::parse)
                .toList();
    }

    /**
     * Constructs a QValues instance from a collection of individual QValue instances. The result will be
     * duplicate-free, QValue instances with the same name will be merged (the result will contain the higher weight)
     * and separated into acceptable and unacceptable.
     *
     * @param qValues a collection of {@link QValue}s.
     * @return the corresponding {@link QValues} object.
     */
    public static QValues of(Collection<QValue> qValues) {
        final Map<Boolean, List<QValue>> isAcceptable = qValues.stream()
                .collect(Collectors.partitioningBy(QValue::isAcceptable));

        return new QValues(isAcceptable.get(true),
                           isAcceptable.get(false).stream().map(QValue::value).collect(Collectors.toSet()));
    }

    private List<QValue> merge(Collection<QValue> qValues) {
        final Map<String, Float> nameWeightMap = qValues.stream()
                .collect(Collectors.toMap(QValue::value, QValue::weight, Float::max));

        return nameWeightMap.entrySet()
                .stream()
                .map(entry -> new QValue(entry.getKey(), entry.getValue()))
                .sorted(QValue.HIGHEST_WEIGHT_FIRST)
                .toList();
    }

    public Optional<String> preferredValue() {
        return acceptableValues.stream().map(QValue::value).findFirst();
    }

    /**
     * A string value together with a "weight" between 0.0 and 1.0
     *
     * @param value
     * @param weight
     */
    public record QValue(String value, float weight) {

        public static final Comparator<QValue> LOWEST_WEIGHT_FIRST = Comparator.comparing(QValue::weight);
        public static final Comparator<QValue> HIGHEST_WEIGHT_FIRST = LOWEST_WEIGHT_FIRST.reversed();

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

        public boolean isAcceptable() {
            return weight > 0.0f;
        }
    }
}
