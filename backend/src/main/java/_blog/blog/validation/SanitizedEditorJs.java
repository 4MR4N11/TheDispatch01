package _blog.blog.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * ✅ SECURITY FIX: Custom validation annotation for Editor.js content
 *
 * This annotation validates and sanitizes Editor.js JSON content to prevent XSS attacks.
 * It parses the JSON and sanitizes all HTML content within the blocks.
 *
 * Usage:
 * @SanitizedEditorJs
 * private String content;
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = SanitizedEditorJsValidator.class)
@Documented
public @interface SanitizedEditorJs {
    String message() default "Editor.js content contains unsafe HTML";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
