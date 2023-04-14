package io.github.jhahnhro.enhancedcdi.messaging.impl;

import static io.github.jhahnhro.enhancedcdi.messaging.impl.Serialization.HIGHEST_PRIORITY_FIRST;

import java.lang.reflect.ParameterizedType;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.spi.Bean;
import javax.inject.Inject;

import io.github.jhahnhro.enhancedcdi.messaging.messages.Outgoing;
import io.github.jhahnhro.enhancedcdi.messaging.serialization.ExceptionMapper;
import io.github.jhahnhro.enhancedcdi.util.BeanInstance;
import io.github.jhahnhro.enhancedcdi.util.EnhancedInstance;

@ApplicationScoped
class ExceptionMapping {

    private Map<Class<?>, ExceptionMapper<?, ?>> mappers;

    @Inject
    void setMappers(EnhancedInstance<ExceptionMapper<?, ?>> enhancedInstance) {
        this.mappers = enhancedInstance.safeStream()
                .sorted(Comparator.comparing(BeanInstance::instance, HIGHEST_PRIORITY_FIRST))
                .collect(Collectors.toMap(this::extractExceptionType, BeanInstance::instance, (a, b) -> a));
    }

    private Class<? extends Throwable> extractExceptionType(BeanInstance<ExceptionMapper<?, ?>> beanInstance) {
        final Bean<ExceptionMapper<?, ?>> bean = (Bean<ExceptionMapper<?, ?>>) beanInstance.contextual();
        final ParameterizedType exceptionMapperType = bean.getTypes()
                .stream()
                .filter(type -> type instanceof ParameterizedType parameterizedType
                                && parameterizedType.getRawType() == ExceptionMapper.class)
                .map(ParameterizedType.class::cast)
                .findAny()
                .orElseThrow();
        return (Class<? extends Throwable>) exceptionMapperType.getActualTypeArguments()[0];
    }

    public Optional<Outgoing.Response<byte[], Object>> applyExceptionMapper(Throwable exception) {
        return Stream.<Class<?>>iterate(exception.getClass(), Throwable.class::isAssignableFrom, Class::getSuperclass)
                .map(mappers::get)
                .filter(Objects::nonNull)
                .findFirst()
                .map(mapper -> applyMapper(exception, mapper));
    }

    private <E extends Throwable> Outgoing.Response<byte[], Object> applyMapper(Throwable exception,
                                                                                ExceptionMapper<E, ?> mapper) {
        return (Outgoing.Response<byte[], Object>) mapper.apply((E) exception);
    }
}
