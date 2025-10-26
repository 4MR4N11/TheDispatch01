package _blog.blog.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

/**
 * ✅ SECURITY FIX: Validator for strong password requirements
 *
 * Validates that passwords meet security requirements:
 * - At least 8 characters
 * - Contains uppercase and lowercase letters
 * - Contains at least one digit
 * - Contains at least one special character
 */
public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    // Password must contain:
    // - At least one uppercase letter (?=.*[A-Z])
    // - At least one lowercase letter (?=.*[a-z])
    // - At least one digit (?=.*\d)
    // - At least one special character (?=.*[@$!%*?&])
    // - Minimum 8 characters {8,}
    private static final String PASSWORD_PATTERN =
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$";

    private static final Pattern pattern = Pattern.compile(PASSWORD_PATTERN);

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null) {
            return false;
        }

        // Check if password matches the pattern
        if (!pattern.matcher(password).matches()) {
            return false;
        }

        // Additional check: prevent common weak passwords
        String lowerPassword = password.toLowerCase();
        String[] commonPasswords = {
            "password", "12345678", "qwerty123", "abc123456",
            "password1", "welcome1", "admin123"
        };

        for (String common : commonPasswords) {
            if (lowerPassword.equals(common)) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(
                    "This password is too common. Please choose a stronger password."
                ).addConstraintViolation();
                return false;
            }
        }

        return true;
    }
}
