package _blog.blog.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

/**
 * ✅ SECURITY FIX: Validator for @NoHtml annotation
 *
 * This validator prevents stored XSS attacks by ensuring that user input
 * does not contain HTML tags.
 *
 * Implementation:
 * 1. Uses Jsoup to sanitize the input (removes all HTML tags)
 * 2. Compares sanitized version with original
 * 3. Validation fails if they differ (meaning HTML was present)
 *
 * This prevents attacks like:
 * - <script>alert(1)</script>
 * - <img src=x onerror=alert(1)>
 * - <iframe src="evil.com"></iframe>
 */
public class NoHtmlValidator implements ConstraintValidator<NoHtml, String> {

    @Override
    public void initialize(NoHtml constraintAnnotation) {
        // No initialization needed
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Null values are considered valid (use @NotNull for null checks)
        if (value == null) {
            return true;
        }

        // Empty strings are valid
        if (value.trim().isEmpty()) {
            return true;
        }

        // Use Jsoup to remove all HTML tags (Safelist.none() removes everything)
        String sanitized = Jsoup.clean(value, Safelist.none());

        // If sanitized version equals original, there was no HTML
        return sanitized.equals(value);
    }
}
