package io.github.jhahnhro.enhancedcdi.multiton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.jhahnhro.enhancedcdi.multiton.impl.BeanClassData;
import io.github.jhahnhro.enhancedcdi.multiton.impl.BeanData;
import io.github.jhahnhro.enhancedcdi.multiton.impl.BeanParameterProducer;
import io.github.jhahnhro.enhancedcdi.multiton.impl.MapBean;
import io.github.jhahnhro.enhancedcdi.multiton.impl.ProducerMethodData;
import io.github.jhahnhro.enhancedcdi.multiton.impl.Validator;
import io.github.jhahnhro.enhancedcdi.multiton.impl.WithParameter;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.Vetoed;
import jakarta.enterprise.inject.spi.*;
import jakarta.enterprise.inject.spi.configurator.BeanConfigurator;
import jakarta.enterprise.util.AnnotationLiteral;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * If a bean class has the {@link ParametrizedBean} annotation, then this extension will getOrCreate multiple instances of
 * {@link AnnotatedType} with that {@link AnnotatedType#getJavaClass() underlying type}. Every parametrized bean must
 * have a unique instance field annotated with {@link BeanParameter} and its type must be an enum class {@code E}. The
 * {@link Class#getEnumConstants() enum constants} of that enum class are the parameters. The original {@code
 * AnnotatedType} will be replaced by {@code |E|} many new annotated types, one for each enum constant, and the field
 * annotated {@code BeanParameter} of the resulting beans will each contain exactly one of the enum constant.
 * <p>
 * Annotations of elements (the class itself, its fields, constructors, methods and each of their parameters) of such a
 * class can be parametrized with the {@link ParametrizedAnnotation} annotation. In each of the copies of the bean
 * class, each of those elements, will have an annotation of the type defined in {@code ParametrizedAnnotation},
 * instantiated with the enum constant corresponding to that particular copy. {@code ParametrizedAnnotation} is
 * repeatable, multiple annotations will be generated accordingly. Instantiation of annotation instances will happen
 * with an appropriate constructor or public, static factory method which accepts a single argument of a type assignable
 * from the enum class, see the documentation of {@link ParametrizedAnnotation} for details.
 * <p>
 * This makes it possible to define a different qualifier for each of the copies of the bean which avoid {@link
 * jakarta.enterprise.inject.AmbiguousResolutionException AmbiguousResolutionException}s. If a parametrized bean is
 * defined like so
 * <pre>{@code
 * enum Color{ RED, GREEN, BLUE }
 *
 * @Qualifier
 * @Retention(RetentionPolicy.RUNTIME)
 * @interface MyQualifier {
 *     Color value();
 *     // inner class for Literals omitted
 * }
 *
 * @RequestScoped
 * @ParametrizedBean
 * @ParametrizedAnnotation(MyQualifier.class)
 * public class MyBean {
 *      @Inject
 *      @BeanParameter
 *      Color myColor;
 *
 *      // ...
 * }
 * }</pre>
 * then the container will generate three beans with request scope and of type {@code MyBean}: One will behave as if its
 * bean class had been declared with {@code @MyQualifier(Color.RED)}, one will behave as if its bean class had been
 * declared with {@code @MyQualifier(Color.GREEN)} and one will behave as if its bean class had been declared with
 * {@code @MyQualifier(Color.BLUE)}.
 * <p>
 * In particular, code like
 * <pre>{@code
 * class SomeOtherBean{
 * @Inject @MyQualifier(Color.RED)
 * MyBean myBean;
 * }
 * }</pre>
 * will work.
 * <p>
 * Parametrized annotations can also be used on {@link Observes observer methods}:
 * <pre>{@code
 *  enum Color{ RED, GREEN, BLUE }
 *
 *  @ParametrizedBean
 *  @ParametrizedAnnotation(MyQualifier.class)
 *  public class MyBean {
 *      @Inject
 *      @BeanParameter
 *      Color myColor;
 *
 *      private void myObserverMethod(@Observes @ParametrizedAnnotation(MyQualifier.class) MyEvent event)
 *          // ...
 *      }
 *  }
 * }</pre>
 * In this scenario, the method of the "red bean" will only listen to events with the "red qualifier" etc.
 * <p>
 * The assignment of the enum constant to the {@code @BeanParameter} field happens after the contextual instance of the
 * bean is {@link InjectionTarget#produce(CreationalContext) created} by the container, but before its dependencies are
 * {@link InjectionTarget#inject(Object, CreationalContext) injected}. In other words: The class constructor can not yet
 * access the value, but initializer and post construct methods can.
 */
public class ParametrizedBeanExtension implements Extension {

    private static final Config APP_CONFIG = ConfigProvider.getConfig();

    private final Validator validator = new Validator(APP_CONFIG);

    // a specializer for every parameter class occurring in a parametrized bean
    private Map<Class<?>, AnnotationSpecializer<?>> specializers = new HashMap<>();
    // parametrized managed beans and their parameter classes
    private List<BeanClassData<?, ?>> parametrizedBeanClasses = new ArrayList<>();
    // beans that contain (non-static) parametrized producer method beans
    private List<ProducerMethodData<?, ?, ?, ?>> parametrizedProducerMethods = new ArrayList<>();

    //region Phase 1: TypeDiscovery
    <T> void discoverParametrizedBeanClasses(@Observes @WithAnnotations(ParametrizedBean.class) ProcessAnnotatedType<T> pat, BeanManager beanManager) {
        AnnotatedType<T> type = pat.getAnnotatedType();
        if (type.isAnnotationPresent(Vetoed.class) // ignore vetoed types
            || type.isAnnotationPresent(WithParameter.class) // ignore synthetic type generated by this extension
            || !type.isAnnotationPresent(ParametrizedBean.class) // ignore the annotation on Producer methods
        ) {
            return;
        }

        BeanClassData<?, T> data = validator.validateParametrizedBeanClass(type,
                                                                           beanManager.createBeanAttributes(type));
        createSpecializedTypes(data);

        // veto the original type, but store it for later usage during AfterTypeDiscovery
        pat.veto();
        this.parametrizedBeanClasses.add(data);
    }
    //endregion

    //region Phase 2: AfterTypeDiscovery

    /**
     * Add synthetic annotated types for the parametrized bean classes. This will lead to the creation of many beans for
     * each of the {@link BeanClassData} previously discovered.
     * <p>
     * This approach is chosen over {@code AfterBeanDiscovery#addBean(...)} because, the resulting beans will be
     * ordinary managed beans instead of synthetic beans. In particular they will be scanned for producer methods which
     * will be needed if someone declares a parametrized producer method inside a parametrized bean class.
     *
     * @param atd
     */
    void addSpecializedTypes(@Observes AfterTypeDiscovery atd) {
        // each class with the @ParametrizedBean annotation was vetoed during type discovery.
        // here we add these types back multiple times
        for (BeanClassData<?, ?> data : parametrizedBeanClasses) {
            addSpecializedTypes(atd, data);
        }
    }

    private <P, T> void addSpecializedTypes(AfterTypeDiscovery atd, BeanClassData<P, T> data) {

        for (var entry : data.parameters().entrySet()) {
            String parameterString = entry.getKey();
            P parameter = entry.getValue();

            AnnotatedType<T> type = data.resultingTypes().get(parameter);
            atd.addAnnotatedType(type, getTypeId(type, parameterString));
        }
    }

    private <P, T> void createSpecializedTypes(BeanClassData<P, T> beanClassData) {

        AnnotationSpecializer<P> specializer = getSpecializer(beanClassData.parameterClass());

        for (var entry : beanClassData.parameters().entrySet()) {
            String parameterString = entry.getKey();
            P parameter = entry.getValue();

            AnnotatedType<T> newType = specializer.configureParametrizedType(beanClassData.annotatedType(), parameter)
                    .add(new WithParameter.Literal<>(beanClassData.parameterClass(), parameterString))
                    .build();

            beanClassData.resultingTypes().put(parameter, newType);
        }
    }
    //endregion

    //region Phase 3: BeanDiscovery

    /**
     * Validates the @BeanParameter injection points, including the ones on observer methods etc. that were not
     * previously considered.
     *
     * @param pip
     * @param <T> the bean class of the bean that declares the injection point
     * @param <X> the declared type of the injection point.
     */
    <T, X> void validateBeanParameterInjectionPoints(@Observes ProcessInjectionPoint<T, X> pip) {
        InjectionPoint injectionPoint = pip.getInjectionPoint();
        if (injectionPoint.getQualifiers().stream().noneMatch(q -> q.annotationType().equals(BeanParameter.class))) {
            // not a @BeanParameter qualified injection point => nothing to do
            return;
        }

        final Annotated annotated = injectionPoint.getAnnotated();
        if (annotated instanceof AnnotatedParameter) {
            AnnotatedCallable<?> declaringCallable = ((AnnotatedParameter<?>) annotated).getDeclaringCallable();
            if (declaringCallable instanceof AnnotatedMethod && declaringCallable.isAnnotationPresent(Produces.class)
                && declaringCallable.isAnnotationPresent(ParametrizedBean.class)
                && !declaringCallable.isAnnotationPresent(WithParameter.class)) {
                // This producer method is annotated @ParametrizedBean, but has not been specialized yet (will happen
                // during ProcessManagedBean). Thus, it will soon (during ProcessBeanAttributes) be vetoed and
                // replaced with synthetic beans => ignore
                return;
            }
        }
        validator.validateBeanParameterInjectionPoint(injectionPoint);
    }

    /**
     * Vetoes parametrized producer methods. Parametrized bean classes were already vetoed in {@link
     * #discoverParametrizedBeanClasses(ProcessAnnotatedType, BeanManager)}
     *
     * @param pba
     * @param <T> the type of the bean
     */
    <T> void vetoParametrizedProducerMethods(@Observes ProcessBeanAttributes<T> pba) {
        Annotated m = pba.getAnnotated();
        if (m instanceof AnnotatedMethod && m.isAnnotationPresent(ParametrizedBean.class)) {
            pba.veto();
        }
    }

    /**
     * Scans managed beans for parametrized producer methods and stores all parametrized beans.
     *
     * @param pmb
     * @param beanManager
     * @param <X>         The class of the managed bean
     */
    <X> void processManagedBeans(@Observes ProcessManagedBean<X> pmb, BeanManager beanManager) {
        discoverParametrizedProducerMethods(pmb, beanManager);

        // store all the managed beans that resulted from the synthetic annotated types we added in #addSpecializedTypes
        AnnotatedType<X> annotatedBeanClass = pmb.getAnnotatedBeanClass();
        if (annotatedBeanClass.isAnnotationPresent(ParametrizedBean.class)) {
            updateBeanClassData(pmb);
        }
    }

    private <X> void discoverParametrizedProducerMethods(ProcessManagedBean<X> pmb, BeanManager beanManager) {
        AnnotatedType<X> annotatedBeanClass = pmb.getAnnotatedBeanClass();
        for (AnnotatedMethod<? super X> method : annotatedBeanClass.getMethods()) {
            if (method.isAnnotationPresent(Produces.class) && method.isAnnotationPresent(ParametrizedBean.class)
                && !method.isAnnotationPresent(Vetoed.class)) {
                final Bean<X> declaringBean = pmb.getBean();

                ProducerMethodData<?, ?, X, ? super X> producerMethodData =
                        validator.validateParametrizedProducerMethod(
                        declaringBean, method, beanManager.createBeanAttributes(method));

                createSpecializedBeans(beanManager, producerMethodData);

                parametrizedProducerMethods.add(producerMethodData);
            }
        }
    }

    private <P, T, X extends Y, Y> void createSpecializedBeans(BeanManager beanManager, ProducerMethodData<P, T, X,
            Y> data) {
        Class<P> parameterClass = data.parameterClass();
        AnnotationSpecializer<P> specializer = getSpecializer(parameterClass);

        for (var entry : data.parameters().entrySet()) {
            String parameterString = entry.getKey();
            P parameter = entry.getValue();

            AnnotatedMethod<Y> configuredMethod = specializer.configureAnnotatedMethod(data.producerMethod(), parameter)
                    .add(new WithParameter.Literal<>(parameterClass, parameterString))
                    .build();
            data.resultingProducerMethods().put(parameter, configuredMethod);

            Bean<T> producerMethodBean = createProducerMethodBean(beanManager, configuredMethod, data.declaringBean());
            data.resultingBeans().put(parameter, producerMethodBean);
        }
    }

    private <T, X extends Y, Y> Bean<T> createProducerMethodBean(BeanManager beanManager,
                                                                 AnnotatedMethod<Y> producerMethod,
                                                                 Bean<X> declaringBean) {
        BeanAttributes<T> producerBeanAttributes = (BeanAttributes<T>) beanManager.createBeanAttributes(producerMethod);
        ProducerFactory<X> producerFactory = beanManager.getProducerFactory(producerMethod, declaringBean);

        return beanManager.createBean(producerBeanAttributes, (Class<X>) declaringBean.getBeanClass(), producerFactory);
    }

    private <X> void updateBeanClassData(ProcessManagedBean<X> pmb) {
        final AnnotatedType<X> annotatedBeanClass = pmb.getAnnotatedBeanClass();
        final Bean<X> bean = pmb.getBean();
        parametrizedBeanClasses.stream()
                .filter(data -> data.annotatedType().getJavaClass().equals(bean.getBeanClass()))
                .forEach(data -> updateBeanClassData(annotatedBeanClass, (BeanClassData<?, X>) data, bean));
    }

    private <P, T> void updateBeanClassData(AnnotatedType<T> annotatedType, BeanData<P, T> data, Bean<T> bean) {
        WithParameter annotation = annotatedType.getAnnotation(WithParameter.class);
        P parameterValue = data.parameters().get(annotation.stringRepresentation());
        data.resultingBeans().put(parameterValue, bean);
    }


    //endregion

    //region Phase 4: AfterBeanDiscovery

    /**
     * Adds the synthetic producer methods, the various beans with the @BeanParameter qualifier as well as the beans
     * with type {@code Map<P,T>} to the container.
     *
     * @param abd
     * @param beanManager
     */
    void addSyntheticBeans(@Observes AfterBeanDiscovery abd, BeanManager beanManager) {
        addProducerMethods(abd);
        addBeanParameterBeans(abd);
        addMapBeans(abd, beanManager);
    }

    private void addProducerMethods(AfterBeanDiscovery abd) {
        for (ProducerMethodData<?, ?, ?, ?> data : parametrizedProducerMethods) {
            data.resultingBeans().values().forEach(abd::addBean);
        }
    }

    private void addMapBeans(AfterBeanDiscovery abd, BeanManager beanManager) {
        parametrizedBeanClasses.stream().map(data -> new MapBean<>(data, beanManager)).forEach(abd::addBean);
        parametrizedProducerMethods.stream().map(data -> new MapBean<>(data, beanManager)).forEach(abd::addBean);
    }

    private void addBeanParameterBeans(AfterBeanDiscovery abd) {
        Set<Class<?>> parameterClasses = new HashSet<>();
        parametrizedBeanClasses.stream().map(BeanData::parameterClass).forEach(parameterClasses::add);
        parametrizedProducerMethods.stream().map(BeanData::parameterClass).forEach(parameterClasses::add);

        parameterClasses.forEach(parameterClass -> configureBeanParameterBean(abd.addBean(), parameterClass));
    }

    private <E> void configureBeanParameterBean(BeanConfigurator<Object> beanConfigurator, Class<E> parameterClass) {
        beanConfigurator.scope(Dependent.class)
                .addTransitiveTypeClosure(parameterClass)
                .addQualifier(BeanParameter.Literal.INSTANCE)
                .addQualifier(new ConfigPropertyDefaultLiteral())
                .produceWith(new BeanParameterProducer<E>(validator));
    }

    private static class ConfigPropertyDefaultLiteral extends AnnotationLiteral<ConfigProperty>
            implements ConfigProperty {

        @Override
        public String name() {
            return "";
        }

        @Override
        public String defaultValue() {
            return ConfigProperty.UNCONFIGURED_VALUE;
        }
    }
    //endregion

    //region Phase 5: AfterDeploymentValidation
    private void cleanup(@Observes AfterDeploymentValidation adv) {
        // we do not need to keep these references around once the application is up and running. The GC can have them.
        this.specializers = null;
        this.parametrizedBeanClasses = null;
        this.parametrizedProducerMethods = null;
    }
    //endregion


    // region helpers
    private <P> AnnotationSpecializer<P> getSpecializer(Class<P> parameterClass) {
        //noinspection unchecked
        AnnotationSpecializer<P> specializer = (AnnotationSpecializer<P>) this.specializers.get(parameterClass);
        if (specializer == null) {
            specializer = new AnnotationSpecializer<>(parameterClass);
            this.specializers.put(parameterClass, specializer);
        }
        return specializer;
    }

    private <T> String getTypeId(AnnotatedType<T> annotatedType, String parameterString) {
        return annotatedType.getJavaClass().getCanonicalName() + ";parameter=" + parameterString;
    }


    //endregion
}