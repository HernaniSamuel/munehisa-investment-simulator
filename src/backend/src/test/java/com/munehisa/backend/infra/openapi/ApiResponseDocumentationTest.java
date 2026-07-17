package com.munehisa.backend.infra.openapi;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.classreading.SimpleMetadataReaderFactory;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
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
 */
class ApiResponseDocumentationTest {

    private static final String CONTROLLERS_PACKAGE = "com.munehisa.backend.controllers";

    @Test
    void everyControllerEndpointDocumentsAtLeastOneApiResponse() throws IOException, ClassNotFoundException {
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

    private static List<Class<?>> findRestControllers(String basePackage) throws IOException, ClassNotFoundException {
        ClassPathScanner scanner = new ClassPathScanner();
        List<Class<?>> controllers = new ArrayList<>();
        for (String className : scanner.scan(basePackage)) {
            controllers.add(Class.forName(className));
        }
        return controllers;
    }

    /**
     * Minimal stand-in for Spring's component-scanning classpath scanner, restricted to classes
     * annotated with @RestController, without needing a running ApplicationContext.
     */
    private static class ClassPathScanner {
        private final MetadataReaderFactory metadataReaderFactory = new SimpleMetadataReaderFactory();
        private final AnnotationTypeFilter filter = new AnnotationTypeFilter(RestController.class);

        List<String> scan(String basePackage) throws IOException {
            List<String> matches = new ArrayList<>();
            String packagePath = ClassUtils.convertClassNameToResourcePath(basePackage);
            String pattern = "classpath*:" + packagePath + "/**/*.class";

            org.springframework.core.io.support.PathMatchingResourcePatternResolver resolver =
                    new org.springframework.core.io.support.PathMatchingResourcePatternResolver();
            org.springframework.core.io.Resource[] resources = resolver.getResources(pattern);

            for (org.springframework.core.io.Resource resource : resources) {
                if (!resource.isReadable()) {
                    continue;
                }
                MetadataReader reader = metadataReaderFactory.getMetadataReader(resource);
                if (filter.match(reader, metadataReaderFactory)) {
                    matches.add(reader.getClassMetadata().getClassName());
                }
            }
            return matches;
        }
    }
}
