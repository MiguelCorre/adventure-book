package com.adventurebook.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Allows the Angular dev server to call the API directly.
 *
 * <p>{@code npm start} proxies {@code /api} to this application, so in the normal
 * workflow the browser sees a single origin and CORS never comes into play. This exists
 * so that running the frontend without the proxy still works during development.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:4200")
                .allowedMethods("GET", "POST", "DELETE");
    }
}
