package _blog.blog.utils;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.springframework.web.multipart.MultipartFile;

public class FileValidator {

    // Allowed image extensions
    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS = new HashSet<>(
            Arrays.asList("jpg", "jpeg", "png", "gif", "webp", "svg")
    );

    // Allowed video extensions
    private static final Set<String> ALLOWED_VIDEO_EXTENSIONS = new HashSet<>(
            Arrays.asList("mp4", "webm", "ogg", "mov", "avi")
    );

    // Allowed audio extensions
    private static final Set<String> ALLOWED_AUDIO_EXTENSIONS = new HashSet<>(
            Arrays.asList("mp3", "wav", "ogg", "m4a")
    );

    // Maximum file sizes
    public static final long MAX_IMAGE_SIZE = 10 * 1024 * 1024; // 10MB
    public static final long MAX_VIDEO_SIZE = 100 * 1024 * 1024; // 100MB
    public static final long MAX_AUDIO_SIZE = 50 * 1024 * 1024; // 50MB
    public static final long MAX_AVATAR_SIZE = 5 * 1024 * 1024; // 5MB

    // Magic bytes for file type verification
    private static final byte[] JPEG_MAGIC = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47};
    private static final byte[] GIF_MAGIC = new byte[]{0x47, 0x49, 0x46};
    private static final byte[] WEBP_MAGIC = new byte[]{0x52, 0x49, 0x46, 0x46};

    /**
     * Sanitize filename to prevent path traversal attacks
     */
    public static String sanitizeFilename(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }

        // Remove path separators and null bytes
        String sanitized = filename.replaceAll("[/\\\\]", "");

        // Remove null bytes separately
        sanitized = sanitized.replace("\0", "");

        // Remove any parent directory references
        sanitized = sanitized.replaceAll("\\.\\.", "");

        // Limit length
        if (sanitized.length() > 255) {
            sanitized = sanitized.substring(0, 255);
        }

        return sanitized;
    }

    /**
     * Get file extension from filename
     */
    public static String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }

        String sanitized = sanitizeFilename(filename);
        int lastDot = sanitized.lastIndexOf(".");

        if (lastDot == -1 || lastDot == sanitized.length() - 1) {
            return "";
        }

        return sanitized.substring(lastDot + 1).toLowerCase();
    }

    /**
     * Validate if file is an allowed media type (image, video, or audio)
     */
    public static boolean isValidMediaFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return false;
        }

        String extension = getExtension(file.getOriginalFilename());

        // Check if extension is in allowed lists
        boolean isAllowedExtension = ALLOWED_IMAGE_EXTENSIONS.contains(extension) ||
                                     ALLOWED_VIDEO_EXTENSIONS.contains(extension) ||
                                     ALLOWED_AUDIO_EXTENSIONS.contains(extension);

        if (!isAllowedExtension) {
            return false;
        }

        // Check file size based on type
        long maxSize = getMaxSizeForExtension(extension);
        if (file.getSize() > maxSize) {
            return false;
        }

        // Verify magic bytes for images
        if (ALLOWED_IMAGE_EXTENSIONS.contains(extension)) {
            return verifyImageMagicBytes(file);
        }

        return true;
    }

    /**
     * Validate if file is a valid image
     */
    public static boolean isValidImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return false;
        }

        String extension = getExtension(file.getOriginalFilename());

        if (!ALLOWED_IMAGE_EXTENSIONS.contains(extension)) {
            return false;
        }

        return verifyImageMagicBytes(file);
    }

    /**
     * Verify image file by checking magic bytes
     */
    private static boolean verifyImageMagicBytes(MultipartFile file) {
        try {
            byte[] bytes = new byte[12]; // Read first 12 bytes
            int read = file.getInputStream().read(bytes);

            if (read < 3) {
                return false;
            }

            // Check for JPEG
            if (bytes[0] == JPEG_MAGIC[0] && bytes[1] == JPEG_MAGIC[1] && bytes[2] == JPEG_MAGIC[2]) {
                return true;
            }

            // Check for PNG
            if (read >= 4 && bytes[0] == PNG_MAGIC[0] && bytes[1] == PNG_MAGIC[1] &&
                bytes[2] == PNG_MAGIC[2] && bytes[3] == PNG_MAGIC[3]) {
                return true;
            }

            // Check for GIF
            if (bytes[0] == GIF_MAGIC[0] && bytes[1] == GIF_MAGIC[1] && bytes[2] == GIF_MAGIC[2]) {
                return true;
            }

            // Check for WEBP (RIFF....WEBP)
            if (read >= 12 && bytes[0] == WEBP_MAGIC[0] && bytes[1] == WEBP_MAGIC[1] &&
                bytes[2] == WEBP_MAGIC[2] && bytes[3] == WEBP_MAGIC[3] &&
                bytes[8] == 0x57 && bytes[9] == 0x45 && bytes[10] == 0x42 && bytes[11] == 0x50) {
                return true;
            }

            // SVG is XML-based, harder to validate by magic bytes, rely on extension
            String extension = getExtension(file.getOriginalFilename());
            return "svg".equals(extension);

        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Get maximum allowed file size for a given extension
     */
    private static long getMaxSizeForExtension(String extension) {
        if (ALLOWED_IMAGE_EXTENSIONS.contains(extension)) {
            return MAX_IMAGE_SIZE;
        } else if (ALLOWED_VIDEO_EXTENSIONS.contains(extension)) {
            return MAX_VIDEO_SIZE;
        } else if (ALLOWED_AUDIO_EXTENSIONS.contains(extension)) {
            return MAX_AUDIO_SIZE;
        }
        return MAX_IMAGE_SIZE; // Default
    }

    /**
     * Get media type from extension
     */
    public static String getMediaType(String extension) {
        if (ALLOWED_IMAGE_EXTENSIONS.contains(extension)) {
            return "image";
        } else if (ALLOWED_VIDEO_EXTENSIONS.contains(extension)) {
            return "video";
        } else if (ALLOWED_AUDIO_EXTENSIONS.contains(extension)) {
            return "audio";
        }
        return "unknown";
    }
}
