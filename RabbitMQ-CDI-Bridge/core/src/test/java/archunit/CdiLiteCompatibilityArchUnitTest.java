package archunit;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

import javax.decorator.Decorator;
import javax.enterprise.inject.spi.Extension;
import javax.interceptor.Interceptors;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@SuppressWarnings("unused")
@AnalyzeClasses(packages = "io.github.jhahnhro", importOptions = ImportOption.DoNotIncludeTests.class)
public class CdiLiteCompatibilityArchUnitTest {

    public static final String IT_IS_NOT_SUPPORTED_IN_CDI_LITE = "it is not supported in CDI Lite";
    @ArchTest
    private final ArchRule noDecorators = noClasses().should()
            .beAnnotatedWith(Decorator.class)
            .because(IT_IS_NOT_SUPPORTED_IN_CDI_LITE)
            .allowEmptyShould(true);

    @ArchTest
    private final ArchRule noOldSchoolInterceptorAtClasses = noClasses().should()
            .beAnnotatedWith(Interceptors.class)
            .because(IT_IS_NOT_SUPPORTED_IN_CDI_LITE)
            .allowEmptyShould(true);
    @ArchTest
    private final ArchRule noOldSchoolInterceptorAtConstructors = noConstructors().should()
            .beAnnotatedWith(Interceptors.class)
            .because(IT_IS_NOT_SUPPORTED_IN_CDI_LITE)
            .allowEmptyShould(true);
    @ArchTest
    private final ArchRule noOldSchoolInterceptorAtMethods = noMethods().should()
            .beAnnotatedWith(Interceptors.class)
            .because(IT_IS_NOT_SUPPORTED_IN_CDI_LITE)
            .allowEmptyShould(true);

    @ArchTest
    private final ArchRule noPortableExtensions = noClasses().should()
            .implement(Extension.class)
            .because(IT_IS_NOT_SUPPORTED_IN_CDI_LITE)
            .allowEmptyShould(true);

    // TODO : BeanManager vs BeanContainer
}
