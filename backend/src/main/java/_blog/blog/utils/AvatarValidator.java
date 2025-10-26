package _blog.blog.utils;

import java.util.Arrays;
import java.util.List;

import org.springframework.web.multipart.MultipartFile;

public class AvatarValidator {

    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList("jpg", "jpeg", "png", "gif", "webp");
    private static final List<String> ALLOWED_MIME_TYPES = Arrays.asList(
            "image/jpeg", "image/png", "image/gif", "image/webp"
    );

    /** 
     * Validate avatar from a URL string.
     */
    public static boolean isValidAvatarUrl(String avatar) {
        if (avatar == null || avatar.isBlank()) return true; // optional

        String lowerCase = avatar.toLowerCase();
        return ALLOWED_EXTENSIONS.stream().anyMatch(lowerCase::endsWith);
    }

    /** 
     * Validate avatar from uploaded file.
     */
    public static boolean isValidAvatarFile(MultipartFile file) {
        if (file == null || file.isEmpty()) return true; // optional

        // Check MIME type
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType)) {
            return false;
        }

        // Optional: check extension
        String filename = file.getOriginalFilename();
        if (filename != null) {
            String lowerCase = filename.toLowerCase();
            return ALLOWED_EXTENSIONS.stream().anyMatch(lowerCase::endsWith);
        }

        return true;
    }
}
