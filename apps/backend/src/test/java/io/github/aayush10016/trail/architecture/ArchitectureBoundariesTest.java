package io.github.aayush10016.trail.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@AnalyzeClasses(packages = "io.github.aayush10016.trail")
class ArchitectureBoundariesTest {

    @ArchTest
    static final ArchRule springBootApplicationLivesOnlyInBootstrap =
            classes()
                    .that()
                    .areAnnotatedWith(SpringBootApplication.class)
                    .should()
                    .resideInAPackage("..bootstrap..");

    @ArchTest
    static final ArchRule domainClassesDoNotDependOnFrameworks =
            classes()
                    .that()
                    .resideInAPackage("..domain..")
                    .should()
                    .onlyDependOnClassesThat()
                    .resideOutsideOfPackages("org.springframework..", "jakarta.persistence..", "com.fasterxml.jackson..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule applicationClassesDoNotDependOnAdapters =
            classes()
                    .that()
                    .resideInAPackage("..application..")
                    .should()
                    .onlyDependOnClassesThat()
                    .resideOutsideOfPackage("..adapter..")
                    .allowEmptyShould(true);
}
