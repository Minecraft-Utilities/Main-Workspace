package xyz.mcutils.backend.config;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xyz.mcutils.backend.model.domain.dns.impl.ARecord;
import xyz.mcutils.backend.model.domain.dns.impl.SRVRecord;
import xyz.mcutils.backend.model.domain.player.FullPlayer;
import xyz.mcutils.backend.model.domain.server.bedrock.BedrockMinecraftServer;
import xyz.mcutils.backend.model.domain.server.java.JavaMinecraftServer;
import xyz.mcutils.backend.model.dto.response.ErrorResponse;

import java.util.List;

/**
 * Adds what the controller signatures cannot express to the generated spec: the concrete subtypes the
 * app returns at runtime and the error body every endpoint can answer with.
 */
@Configuration
public class OpenApiSchemaCustomizer {
    private static final List<Class<?>> RUNTIME_SUBTYPES = List.of(
            FullPlayer.class,
            JavaMinecraftServer.class,
            BedrockMinecraftServer.class,
            ARecord.class,
            SRVRecord.class,
            ErrorResponse.class
    );

    @Bean
    public OpenApiCustomizer runtimeSubtypesAndErrors() {
        return openApi -> {
            ModelConverters converters = ModelConverters.getInstance(true);
            Components components = openApi.getComponents();
            for (Class<?> type : RUNTIME_SUBTYPES) {
                converters.readAll(type).forEach(components::addSchemas);
            }

            Schema<?> errorRef = new Schema<>().$ref("#/components/schemas/ErrorResponse");
            ApiResponse error = new ApiResponse()
                    .description("Error")
                    .content(new Content().addMediaType("application/json", new MediaType().schema(errorRef)));
            openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation -> {
                if (operation.getResponses().getDefault() == null) {
                    operation.getResponses().addApiResponse("default", error);
                }
            }));
        };
    }
}
