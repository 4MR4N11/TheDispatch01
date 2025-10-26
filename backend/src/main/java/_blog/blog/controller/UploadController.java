package _blog.blog.controller;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import _blog.blog.utils.FileValidator;

@RestController
@RequestMapping("/uploads")
public class UploadController {

    // ✅ CODE QUALITY FIX: Using SLF4J logger instead of System.out.println
    private static final Logger log = LoggerFactory.getLogger(UploadController.class);

    @Value("${upload.path:uploads}")
    private String uploadPath;

    // Generic upload endpoint (supports any file type)
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadFile(@RequestParam("file") MultipartFile file) {
        return handleFileUpload(file, "file");
    }

    // Image upload endpoint (EditorJS and frontend expect this)
    @PostMapping("/image")
    public ResponseEntity<Map<String, Object>> uploadImage(@RequestParam(value = "image", required = false) MultipartFile imageParam,
                                                            @RequestParam(value = "file", required = false) MultipartFile fileParam) {
        MultipartFile file = imageParam != null ? imageParam : fileParam;

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", 0, "error", "Image file is required"));
        }

        if (!FileValidator.isValidImage(file)) {
            return ResponseEntity.badRequest().body(Map.of("success", 0, "error", "Invalid image type"));
        }

        return handleFileUpload(file, "image");
    }

    // Avatar upload endpoint
    @PostMapping("/avatar")
    public ResponseEntity<Map<String, Object>> uploadAvatar(@RequestParam(value = "avatar", required = false) MultipartFile avatarParam,
                                                              @RequestParam(value = "file", required = false) MultipartFile fileParam) {
        MultipartFile file = avatarParam != null ? avatarParam : fileParam;

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Avatar file is required"));
        }

        if (!FileValidator.isValidImage(file)) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Invalid image type"));
        }

        if (file.getSize() > FileValidator.MAX_AVATAR_SIZE) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Avatar too large (max 5MB)"));
        }

        return handleFileUpload(file, "avatar");
    }

    // Helper method to handle file uploads
    private ResponseEntity<Map<String, Object>> handleFileUpload(MultipartFile file, String type) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", 0, "error", "File is required"));
        }

        // Validate file size based on type
        long maxSize = type.equals("avatar") ? FileValidator.MAX_AVATAR_SIZE : 10 * 1024 * 1024;
        if (file.getSize() > maxSize) {
            return ResponseEntity.badRequest().body(Map.of("success", 0, "error", "File too large"));
        }

        try {
            Path uploadDir = Paths.get(uploadPath).toAbsolutePath().normalize();
            Files.createDirectories(uploadDir);

            String ext = FileValidator.getExtension(file.getOriginalFilename());
            String prefix = type.equals("avatar") ? "avatar_" : "";
            String filename = prefix + UUID.randomUUID() + "." + ext;
            Path filePath = uploadDir.resolve(filename).normalize();

            // ✅ CODE QUALITY FIX: Using logger instead of System.out.println
            log.debug("Generated filename: {}", filename);
            log.debug("Full path: {}", filePath);

            try (InputStream in = file.getInputStream()) {
                Files.copy(in, filePath, StandardCopyOption.REPLACE_EXISTING);
            }

            log.info("File saved successfully: {}", filename);

            // Return full URL with backend host for frontend to access
            String fileUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path("/uploads/")
                    .path(filename)
                    .toUriString();

            log.debug("Returning URL: {}", fileUrl);

            return ResponseEntity.ok(Map.of(
                "success", 1,
                "file", Map.of("url", fileUrl),
                "url", fileUrl
            ));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", 0,
                "error", "Failed to upload file: " + e.getMessage()
            ));
        }
    }

    @PostMapping("/video")
    public ResponseEntity<Map<String, Object>> uploadVideo(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", 0, "error", "Video file is required"));
        }

        // Validate file type
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("video/")) {
            return ResponseEntity.badRequest().body(Map.of("success", 0, "error", "Invalid video file type"));
        }

        // Validate file size (max 100MB)
        if (file.getSize() > FileValidator.MAX_VIDEO_SIZE) {
            return ResponseEntity.badRequest().body(Map.of("success", 0, "error", "Video too large (max 100MB)"));
        }

        // Validate extension
        String ext = FileValidator.getExtension(file.getOriginalFilename());
        if (ext.isEmpty() || FileValidator.getMediaType(ext).equals("unknown")) {
            return ResponseEntity.badRequest().body(Map.of("success", 0, "error", "Invalid video file extension"));
        }

        try {
            Path uploadDir = Paths.get(uploadPath).toAbsolutePath().normalize();
            Files.createDirectories(uploadDir);

            String filename = UUID.randomUUID() + "." + ext;
            Path filePath = uploadDir.resolve(filename).normalize();

            try (InputStream in = file.getInputStream()) {
                Files.copy(in, filePath, StandardCopyOption.REPLACE_EXISTING);
            }

            String fileUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path("/uploads/")
                    .path(filename)
                    .toUriString();

            return ResponseEntity.ok(Map.of(
                "success", 1,
                "file", Map.of("url", fileUrl)
            ));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", 0,
                "error", "Failed to upload video: " + e.getMessage()
            ));
        }
    }
}
