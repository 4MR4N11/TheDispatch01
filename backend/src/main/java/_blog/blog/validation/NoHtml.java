package _blog.blog.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * ✅ SECURITY FIX: Custom validation annotation to prevent XSS attacks
 *
 * This annotation validates that a string does not contain HTML tags.
 * It uses Jsoup to sanitize the input and compares it with the original value.
 *
 * Usage:
 * @NoHtml
 * private String firstname;
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = NoHtmlValidator.class)
@Documented
public @interface NoHtml {
    String message() default "HTML content is not allowed";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
