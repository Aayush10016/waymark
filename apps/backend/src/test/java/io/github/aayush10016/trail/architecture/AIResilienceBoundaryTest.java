package io.github.aayush10016.trail.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "io.github.aayush10016.trail")
class AIResilienceBoundaryTest {

    @ArchTest
    static final ArchRule captureSolutionAndRetrievalDoNotDependOnProviderAdapters =
            noClasses()
                    .that()
                    .resideInAnyPackage("..capture..", "..problem..", "..retrieval..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("..intelligence.adapter.out.ai..", "..intelligence.adapter.out.embedding..")
                    .because("capture, exact solution, and deterministic search must survive AI provider outages");
}
