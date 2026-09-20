package com.ecommerce.common.web;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * Makes this library self-wiring.
 *
 * <p>A service's {@code @SpringBootApplication} only scans its own package
 * ({@code com.ecommerce.catalog}), so beans in {@code com.ecommerce.common.web}
 * would otherwise be invisible. Registering them through auto-configuration -
 * declared in {@code META-INF/spring/...AutoConfiguration.imports} - means adding
 * the dependency is all a service has to do. No {@code scanBasePackages} widening,
 * which would drag in anything else that ever lands on the classpath.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CommonWebAutoConfiguration {

    @Bean
    public CorrelationIdFilter correlationIdFilter() {
        return new CorrelationIdFilter();
    }

    @Bean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
}
