package com.adventurebook.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.DeserializationFeature;

/**
 * HTTP request-body conventions.
 *
 * <p>Jackson 3 rejects a missing JSON property that maps onto a Java primitive. For this
 * API an omitted flag means "no", so {@code {"bookSlug":"x"}} is a complete request and
 * {@code fromSave} defaults to false rather than failing the call.
 *
 * <p>Expressed as a bean rather than a {@code spring.jackson.*} property on purpose: the
 * test classpath supplies its own {@code application.yml}, which shadows the main one
 * entirely, so a property set there would silently not apply during tests.
 */
@Configuration
public class JacksonConfig {

    @Bean
    JsonMapperBuilderCustomizer omittedPrimitivesTakeTheirDefault() {
        return builder -> builder.disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
    }
}
