package io.github.jhahnhro.enhancedcdi.messaging.impl;

import static io.github.jhahnhro.enhancedcdi.messaging.impl.Serialization.HIGHEST_PRIORITY_FIRST;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Inject;

import io.github.jhahnhro.enhancedcdi.messaging.messages.Incoming;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Outgoing;
import io.github.jhahnhro.enhancedcdi.messaging.serialization.ExceptionMapper;
import io.github.jhahnhro.enhancedcdi.types.ParameterizedTypeImpl;
import io.github.jhahnhro.enhancedcdi.types.WildcardTypeImpl;
import io.github.jhahnhro.enhancedcdi.util.BeanInstance;

@ApplicationScoped
class ExceptionMapping {

    private final Map<Class<?>, List<BeanInstance<ExceptionMapper<?, ?>>>> mappers = new ConcurrentHashMap<>();
    @Inject
    BeanManager beanManager;

    private <E extends Throwable> List<BeanInstance<ExceptionMapper<?, ?>>> getMappers(Class<E> clazz) {
        final ParameterizedType parameterizedType = new ParameterizedTypeImpl(ExceptionMapping.class, null, clazz,
                                                                              new WildcardTypeImpl(new Type[0],
                                                                                                   new Type[0]));
        return beanManager.getBeans(parameterizedType, Any.Literal.INSTANCE)
                .stream()
                .map(bean -> BeanInstance.createContextualReference(beanManager, (Bean<ExceptionMapper<?, ?>>) bean,
                                                                    parameterizedType))
                .sorted(Comparator.comparing(BeanInstance::instance, HIGHEST_PRIORITY_FIRST))
                .toList();
    }

    @SuppressWarnings("unchecked")
    public <REQ, E extends Throwable> Optional<Outgoing.Response<REQ, Object>> applyExceptionMapper(Incoming.Request<REQ> request, E exception) {
        for (Class<? super E> clazz = (Class<? super E>) exception.getClass(); Throwable.class.isAssignableFrom(clazz);
             clazz = clazz.getSuperclass()) {
            Optional<Outgoing.Response<REQ, Object>> mapper = applyMapperIfExists(request, exception,
                                                                                  clazz.asSubclass(Throwable.class));
            if (mapper.isPresent()) {
                return mapper;
            }
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private <REQ, E extends E1, E1 extends Throwable> Optional<Outgoing.Response<REQ, Object>> applyMapperIfExists(Incoming.Request<REQ> request, E exception, Class<E1> clazz) {
        var beanInstances = mappers.computeIfAbsent(clazz, ignored -> getMappers(clazz));
        if (beanInstances.isEmpty()) {
            return Optional.empty();
        }

        final ExceptionMapper<E1, ?> mapper = (ExceptionMapper<E1, ?>) beanInstances.get(0).instance();
        return Optional.ofNullable((Outgoing.Response<REQ, Object>) mapper.apply(request, exception));
    }
}
