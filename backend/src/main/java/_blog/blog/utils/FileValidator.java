package _blog.blog.utils;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Set;

import javax.imageio.ImageIO;

import org.springframework.web.multipart.MultipartFile;

public class FileValidator {

    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS =
        Set.of("jpg", "jpeg", "png", "gif", "webp", "svg");

    private static final Set<String> ALLOWED_VIDEO_EXTENSIONS =
        Set.of("mp4", "webm", "ogg", "mov", "avi");

    public static final long MAX_IMAGE_SIZE = 10 * 1024 * 1024;  // 10MB
    public static final long MAX_VIDEO_SIZE = 50 * 1024 * 1024;  // 50MB

    public static String sanitizeFilename(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }

        String sanitized = filename
            .replaceAll("[/\\\\]", "")
            .replace("\0", "")
            .replaceAll("\\.\\.", "");

        return sanitized.length() > 255 ? sanitized.substring(0, 255) : sanitized;
    }

    public static String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }

        String sanitized = sanitizeFilename(filename);
        int lastDot = sanitized.lastIndexOf(".");

        return (lastDot == -1 || lastDot == sanitized.length() - 1)
            ? ""
            : sanitized.substring(lastDot + 1).toLowerCase();
    }

    public static boolean isValidImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return false;
        }

        String extension = getExtension(file.getOriginalFilename());
        return ALLOWED_IMAGE_EXTENSIONS.contains(extension) && verifyImageContent(file);
    }

    // Verify image by parsing it with ImageIO (prevents fake file uploads)
    private static boolean verifyImageContent(MultipartFile file) {
        try {
            if ("svg".equals(getExtension(file.getOriginalFilename()))) {
                return validateSvgFile(file);
            }

            BufferedImage image = ImageIO.read(file.getInputStream());
            if (image == null) {
                return false;
            }

            int width = image.getWidth();
            int height = image.getHeight();

            // Prevent DoS: reject oversized or invalid dimensions
            return width > 0 && height > 0 && width <= 10000 && height <= 10000;

        } catch (Exception e) {
            return false;
        }
    }

    // Validate SVG files to prevent XSS attacks
    private static boolean validateSvgFile(MultipartFile file) {
        try {
            byte[] bytes = new byte[(int) Math.min(file.getSize(), 1024)];
            int read = file.getInputStream().read(bytes);

            if (read <= 0) {
                return false;
            }

            String content = new String(bytes, 0, read);
            String lower = content.toLowerCase();

            boolean validStart = content.trim().startsWith("<") &&
                                (content.contains("<svg") || content.contains("<?xml"));

            boolean noScripts = !lower.contains("<script") &&
                               !lower.contains("javascript:") &&
                               !lower.contains("onerror=") &&
                               !lower.contains("onload=");

            return validStart && noScripts;

        } catch (IOException e) {
            return false;
        }
    }

    public static String getMediaType(String extension) {
        if (ALLOWED_IMAGE_EXTENSIONS.contains(extension)) return "image";
        if (ALLOWED_VIDEO_EXTENSIONS.contains(extension)) return "video";
        // Audio only supported via external URLs (e.g., YouTube, Spotify)
        if (extension.matches("mp3|wav|ogg|m4a|flac|aac")) return "audio";
        return "unknown";
    }
}
