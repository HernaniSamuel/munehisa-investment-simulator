package com.munehisa.backend.infra.openapi;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Baseline drift guard from issue #32: every endpoint must at least attempt to document its
 * responses. This does not verify the documented status codes match real behavior (that would
 * require enabling springdoc in the test profile and diffing against integration-test results,
 * which is out of scope per the issue's cost/benefit call) - it only catches an endpoint added
 * with zero response documentation, which is the concrete gap the #23 audit found.
 *
 * <p>Only scans each controller's declared methods, so an endpoint inherited from a shared base
 * controller would be silently skipped rather than flagged. No such base class exists today, but
 * this is the same silent-drift failure mode the check exists to prevent, and it'll need
 * revisiting the moment a shared abstract controller is introduced.
 */
class ApiResponseDocumentationTest {

    private static final String CONTROLLERS_PACKAGE = "com.munehisa.backend.controllers";

    @Test
    void everyControllerEndpointDocumentsAtLeastOneApiResponse() throws ClassNotFoundException {
        List<String> undocumented = new ArrayList<>();

        for (Class<?> controller : findRestControllers(CONTROLLERS_PACKAGE)) {
            for (Method method : controller.getDeclaredMethods()) {
                if (!isEndpoint(method)) {
                    continue;
                }
                if (!hasApiResponseDocumentation(method)) {
                    undocumented.add(controller.getSimpleName() + "#" + method.getName());
                }
            }
        }

        assertTrue(undocumented.isEmpty(),
                "endpoints missing @ApiResponses/@ApiResponse documentation: " + undocumented);
    }

    private static boolean isEndpoint(Method method) {
        return AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class);
    }

    private static boolean hasApiResponseDocumentation(Method method) {
        return AnnotatedElementUtils.hasAnnotation(method, ApiResponses.class)
                || AnnotatedElementUtils.hasAnnotation(method, ApiResponse.class);
    }

    private static List<Class<?>> findRestControllers(String basePackage) throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        ClassLoader classLoader = ApiResponseDocumentationTest.class.getClassLoader();
        List<Class<?>> controllers = new ArrayList<>();
        for (BeanDefinition beanDefinition : scanner.findCandidateComponents(basePackage)) {
            // initialize=false: this scanner only reads annotations reflectively, so there's no
            // need to run each controller's static initializers.
            controllers.add(Class.forName(beanDefinition.getBeanClassName(), false, classLoader));
        }
        return controllers;
    }
}
