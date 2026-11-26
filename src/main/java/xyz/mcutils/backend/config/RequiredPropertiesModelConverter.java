package xyz.mcutils.backend.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.oas.models.media.Schema;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.type.TypeFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Marks every property of our own models as required in the generated OpenAPI spec, except fields
 * annotated {@code @Nullable} or {@code @JsonInclude(NON_NULL)}. The app omits null properties, so an
 * absent field must be declared optional or generated clients would type it as always present.
 */
@Component
public class RequiredPropertiesModelConverter implements ModelConverter {
    private static final String MODEL_PACKAGE = "xyz.mcutils.backend";

    @Override
    @SuppressWarnings("rawtypes")
    public Schema resolve(AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
        if (!chain.hasNext()) {
            return null;
        }
        Schema schema = chain.next().resolve(type, context, chain);
        if (schema == null) {
            return null;
        }

        // Resolving as a reference returns only a $ref; the model itself lives in the context.
        Schema model = schema;
        if (schema.getProperties() == null && schema.get$ref() != null) {
            String ref = schema.get$ref();
            model = context.getDefinedModels().get(ref.substring(ref.lastIndexOf('/') + 1));
        }
        if (model == null || model.getProperties() == null) {
            return schema;
        }

        Class<?> raw = type.getType() instanceof JavaType javaType
                ? javaType.getRawClass()
                : TypeFactory.createDefaultInstance().constructType(type.getType()).getRawClass();
        if (!raw.getName().startsWith(MODEL_PACKAGE)) {
            return schema;
        }

        List<String> required = new ArrayList<>();
        for (Object name : model.getProperties().keySet()) {
            Field field = findField(raw, (String) name);
            if (field == null || !isOptional(field)) {
                required.add((String) name);
            }
        }
        model.setRequired(required.isEmpty() ? null : required);
        return schema;
    }

    private static boolean isOptional(Field field) {
        JsonInclude include = field.getAnnotation(JsonInclude.class);
        if (include != null && include.value() == JsonInclude.Include.NON_NULL) {
            return true;
        }
        return hasNullable(field.getAnnotations()) || hasNullable(field.getAnnotatedType().getAnnotations());
    }

    private static boolean hasNullable(Annotation[] annotations) {
        return Arrays.stream(annotations).anyMatch(a -> a.annotationType().getSimpleName().equals("Nullable"));
    }

    private static Field findField(Class<?> type, String name) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (field.getName().equals(name)) {
                    return field;
                }
            }
            for (Field field : c.getDeclaredFields()) {
                if (field.isAnnotationPresent(JsonUnwrapped.class)) {
                    Field nested = findField(field.getType(), name);
                    if (nested != null) {
                        return nested;
                    }
                }
            }
        }
        return null;
    }
}
