package archunit;

import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.metaAnnotatedWith;
import static com.tngtech.archunit.lang.conditions.ArchConditions.beAnnotatedWith;
import static com.tngtech.archunit.lang.conditions.ArchConditions.haveRawType;
import static com.tngtech.archunit.lang.conditions.ArchPredicates.are;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

import java.util.Optional;
import java.util.function.Predicate;
import javax.enterprise.context.NormalScope;
import javax.enterprise.inject.Produces;
import javax.inject.Singleton;
import javax.interceptor.InterceptorBinding;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.core.domain.JavaMember;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;


/**
 * Checks classes that use CDI-features against the necessities of the CDI spec to ensure that the classes can be used
 * under Weld / Wildfly.
 */
@SuppressWarnings("unused") // IntelliJ does not detect @ArchTests
@AnalyzeClasses(packages = {"io.github.jhahnhro"}, importOptions = ImportOption.DoNotIncludeTests.class)
public class CdiSpecArchUnitTest {

    private static final DescribedPredicate<JavaClass> PROXYABLE = new DescribedPredicate<>("proxyable") {

        @Override
        public boolean test(JavaClass javaClass) {
            // From the CDI spec:
            //   The container uses proxies to provide certain functionality. Certain legal bean types cannot be proxied
            //   by the container:
            //
            //   * classes which don’t have a non-private constructor with no parameters,
            //   * classes which are declared final,
            //   * classes which have non-static, final methods with public, protected or default visibility,
            //   * sealed classes and sealed interfaces,
            //   * primitive types,
            //   * array types.
            return hasANonPrivateNoArgsConstructor(javaClass) && isNotFinal(javaClass)
                   && containsNoFinalNonPrivateNonStaticMethods(javaClass) && isNotSealed(javaClass)
                   && !javaClass.isPrimitive() && !javaClass.isArray();
        }

        private boolean isNotSealed(JavaClass javaClass) {
            // TODO: use ArchUnit method once https://github.com/TNG/ArchUnit/issues/394 is resolved
            return !javaClass.reflect().isSealed();
        }

        private boolean containsNoFinalNonPrivateNonStaticMethods(JavaClass javaClass) {
            return javaClass.getAllMethods()
                    .stream()
                    .filter(Predicate.not(m -> m.getOwner().getName().equals("java.lang.Object")))
                    .map(JavaMember::getModifiers)
                    .filter(m -> !m.contains(JavaModifier.PRIVATE) && !m.contains(JavaModifier.STATIC))
                    .noneMatch(m -> m.contains(JavaModifier.FINAL));
        }

        private boolean isNotFinal(JavaClass javaClass) {
            return !javaClass.getModifiers().contains(JavaModifier.FINAL);
        }

        private boolean hasANonPrivateNoArgsConstructor(JavaClass javaClass) {
            final Optional<JavaConstructor> noArgsCtor = javaClass.tryGetConstructor();
            return noArgsCtor.isPresent() && !noArgsCtor.get().getModifiers().contains(JavaModifier.PRIVATE);
        }
    };
    private static final ArchCondition<JavaClass> BE_PROXYABLE = ArchCondition.from(PROXYABLE);

    private static final String SINGLETON_SCOPE_NOT_COMPATIBLE_WITH_BEAN_DISCOVERY_ANNOTATED =
            "to be compatible with bean-discovery-mode \"annotated\"; @Singleton beans are not necessarily recognized "
            + "in this mode. Use @Dependent or @ApplicationScoped instead.";

    // most bean types need to be proxyable
    @ArchTest
    private final ArchRule classesAnnotatedWithApplicationScoped_mustBeProxyable = classes().that()
            .areMetaAnnotatedWith(NormalScope.class)
            .and()
            .areNotAnnotations() // excludes stereotype annotations
            .should(BE_PROXYABLE)
            .allowEmptyShould(true);

    @ArchTest
    private final ArchRule producerFieldsWithNormalScope_mustBeProxyable = fields().that()
            .areAnnotatedWith(Produces.class)
            .and()
            .areMetaAnnotatedWith(NormalScope.class)
            .should(haveRawType(PROXYABLE))
            .allowEmptyShould(true);
    @ArchTest
    private final ArchRule producerMethodsWithNormalScope_mustBeProxyable = fields().that()
            .areAnnotatedWith(Produces.class)
            .and()
            .areMetaAnnotatedWith(NormalScope.class)
            .should(haveRawType(PROXYABLE))
            .allowEmptyShould(true);

    @ArchTest
    private final ArchRule classesWithInterceptorBinding_mustBeProxyable = classes().that()
            .areMetaAnnotatedWith(InterceptorBinding.class)
            .and()
            .areNotAnnotations() // exclude stereotypes annotations
            .should(BE_PROXYABLE)
            .allowEmptyShould(true);
    @ArchTest
    private final ArchRule classesWithMethodsWithInterceptorBinding_mustBeProxyable = classes().that()
            .containAnyMethodsThat(are(metaAnnotatedWith(InterceptorBinding.class)))
            .or()
            .containAnyConstructorsThat(are(metaAnnotatedWith(InterceptorBinding.class)))
            .should(BE_PROXYABLE)
            .allowEmptyShould(true);
    //endregion

    //region @Singleton pseudoscope
    @ArchTest
    private final ArchRule beanClassesShouldNotUseSingletonPseudoScope = noClasses().should(
                    beAnnotatedWith(Singleton.class))
            .allowEmptyShould(true)
            .because(SINGLETON_SCOPE_NOT_COMPATIBLE_WITH_BEAN_DISCOVERY_ANNOTATED);
    @ArchTest
    private final ArchRule producerMethodsShouldNotUseSingletonPseudoScope = noMethods().should(
                    beAnnotatedWith(Singleton.class))
            .allowEmptyShould(true)
            .because(SINGLETON_SCOPE_NOT_COMPATIBLE_WITH_BEAN_DISCOVERY_ANNOTATED);
    @ArchTest
    private final ArchRule producerFieldsShouldNotUseSingletonPseudoScope = noFields().should(
                    beAnnotatedWith(Singleton.class))
            .allowEmptyShould(true)
            .because(SINGLETON_SCOPE_NOT_COMPATIBLE_WITH_BEAN_DISCOVERY_ANNOTATED);
    //endregion
}
