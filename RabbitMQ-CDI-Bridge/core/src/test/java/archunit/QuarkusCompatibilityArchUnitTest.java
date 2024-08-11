package archunit;

import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static com.tngtech.archunit.lang.conditions.ArchPredicates.is;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.event.Observes;
import javax.enterprise.event.ObservesAsync;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.inject.Qualifier;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaParameter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * The CDI spec and Quarkus ArC's opinions about what is allowed and what is best practice differs at some points. This
 * class contains ArchUnit tests that enforce that we use slightly stricter rules than necessary, i.e.
 * <ul>
 * <li>even though Quarkus ArC allows fields to have a qualifier, but no @Inject, we want @Inject where ever
 * required by the CDI spec so that all injection points are clearly recognizable as such.</li>
 * <li>even though the CDI spec allows private fields to be injected, we want at least package-private fields to be
 * more closely aligned with Quarkus' performance goals</li>
 * <li>even though the CDI spec allows private initializer, lifecycle and/or observer methods, we want at least
 * package-private access to be more closely aligned with Quarkus' performance goals</li>
 * </ul>
 */
@AnalyzeClasses(packages = "io.github.jhahnhro", importOptions = ImportOption.DoNotIncludeTests.class)
@SuppressWarnings("unused")
public class QuarkusCompatibilityArchUnitTest {

    public static final String QUARKUS_PREFERS_PACKAGE_PRIVATE =
            "Quarkus Arc has better performance with package-private access, because it does not need"
            + " to rely on reflection";
    public static final String AT_INJECT_FOR_CLARITY =
            "while Quarkus ArC recognizes fields as injection points that only have a qualifier annotation"
            + " and not @Inject, we want to communicate more clearly where injection is expected to happen.";

    @ArchTest
    ArchRule noQualifierWithoutInjectAnnotation = fields().that()
            .areMetaAnnotatedWith(Qualifier.class)
            .and()
            .areNotAnnotatedWith(Produces.class) // producer fields are an exception of course
            .should()
            .beAnnotatedWith(Inject.class)
            .allowEmptyShould(true)
            .because(AT_INJECT_FOR_CLARITY);

    @ArchTest
    ArchRule noInjectionIntoPrivateFields = fields().that()
            .areAnnotatedWith(Inject.class)
            .should()
            .notBePrivate()
            .allowEmptyShould(true)
            .because("while the CDI spec allows injection into private fields, " + QUARKUS_PREFERS_PACKAGE_PRIVATE);

    @ArchTest
    ArchRule noPrivateLifecycleMethods = methods().that()
            .areAnnotatedWith(Inject.class)
            .or()
            .areAnnotatedWith(PostConstruct.class)
            .or()
            .areAnnotatedWith(PreDestroy.class)
            .should()
            .notBePrivate()
            .allowEmptyShould(true)
            .because("while the CDI spec allows initialization methods and lifecycle methods to be private, "
                     + QUARKUS_PREFERS_PACKAGE_PRIVATE);

    @ArchTest
    ArchRule noPrivateObserverMethods = methods().that(
                    haveParameterThat(is(annotatedWith(Observes.class))).or(is(annotatedWith(ObservesAsync.class))))
            .should()
            .notBePrivate()
            .allowEmptyShould(true)
            .because("while the CDI spec allows observer methods to be private, " + QUARKUS_PREFERS_PACKAGE_PRIVATE);

    private DescribedPredicate<JavaCodeUnit> haveParameterThat(DescribedPredicate<? super JavaParameter> parameterPredicate) {
        return new DescribedPredicate<>("have a parameter that %s", parameterPredicate.getDescription()) {
            @Override
            public boolean test(JavaCodeUnit javaMethod) {
                return javaMethod.getParameters().stream().anyMatch(parameterPredicate);
            }
        };
    }
}
