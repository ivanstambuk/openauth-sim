package io.openauth.sim.rest;

import io.swagger.v3.oas.models.media.Schema;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenApiSchemaCustomization {

    @Bean
    @SuppressWarnings("rawtypes")
    OpenApiCustomizer schemaNormalizationCustomizer() {
        return openApi -> {
            if (openApi.getComponents() == null) {
                return;
            }
            Map<String, Schema> schemas = openApi.getComponents().getSchemas();
            if (schemas == null) {
                return;
            }
            Schema<?> fieldSchema = schemas.get("Field");
            if (fieldSchema == null || fieldSchema.getProperties() == null) {
                return;
            }
            Object valueProperty = fieldSchema.getProperties().get("value");
            if (!(valueProperty instanceof Schema<?> valueSchema)) {
                return;
            }
            if ("object".equals(valueSchema.getType())
                    && valueSchema.getOneOf() != null
                    && !valueSchema.getOneOf().isEmpty()) {
                valueSchema.setType(null);
            }
        };
    }
}
