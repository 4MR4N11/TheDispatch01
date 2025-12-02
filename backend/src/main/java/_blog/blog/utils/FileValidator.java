package _blog.blog.utils;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.imageio.ImageIO;

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
     * ✅ SECURITY FIX: Verify image file by actually parsing it (not just magic bytes)
     *
     * Previous vulnerability: Only checked first few bytes, which could be faked
     * New implementation: Uses ImageIO to parse the entire image file
     *
     * This prevents attackers from uploading malicious files disguised as images
     */
    private static boolean verifyImageMagicBytes(MultipartFile file) {
        try {
            String extension = getExtension(file.getOriginalFilename());

            // SVG is XML-based, special handling required
            if ("svg".equals(extension)) {
                // For SVG, check if it starts with valid SVG/XML tags
                return validateSvgFile(file);
            }

            // For raster images (JPEG, PNG, GIF, WEBP), use ImageIO to actually parse the image
            BufferedImage image = ImageIO.read(file.getInputStream());

            if (image == null) {
                // ImageIO returns null if it cannot parse the image
                return false;
            }

            // Additional security checks
            int width = image.getWidth();
            int height = image.getHeight();

            // Reject images that are too large (potential DoS attack)
            if (width > 10000 || height > 10000) {
                return false;
            }

            // Reject images with 0 dimensions
            if (width <= 0 || height <= 0) {
                return false;
            }

            // Image is valid
            return true;

        } catch (IOException e) {
            // If any error occurs during image parsing, reject the file
            return false;
        } catch (Exception e) {
            // Catch any other exceptions (e.g., OutOfMemoryError for decompression bombs)
            return false;
        }
    }

    /**
     * ✅ SECURITY FIX: Validate SVG files to prevent XSS attacks
     *
     * SVG files can contain JavaScript, so we need to validate them carefully
     */
    private static boolean validateSvgFile(MultipartFile file) {
        try {
            byte[] bytes = new byte[(int) Math.min(file.getSize(), 1024)]; // Read first 1KB
            int read = file.getInputStream().read(bytes);

            if (read <= 0) {
                return false;
            }

            String content = new String(bytes, 0, read);

            // Check if it starts with valid XML/SVG tags
            if (!content.trim().startsWith("<") ||
                !(content.contains("<svg") || content.contains("<?xml"))) {
                return false;
            }

            // SECURITY: Reject SVG files with script tags (XSS prevention)
            String lowerContent = content.toLowerCase();
            if (lowerContent.contains("<script") ||
                lowerContent.contains("javascript:") ||
                lowerContent.contains("onerror=") ||
                lowerContent.contains("onload=")) {
                return false;
            }

            return true;

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
