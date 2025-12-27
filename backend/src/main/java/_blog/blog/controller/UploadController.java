package _blog.blog.controller;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import _blog.blog.utils.FileValidator;

@RestController
@RequestMapping("/uploads")
public class UploadController {

    @Value("${upload.path:uploads}")
    private String uploadPath;

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadFile(@RequestParam("file") MultipartFile file) {
        return handleFileUpload(file, "file");
    }

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

    // Helper method to handle file uploads
    private ResponseEntity<Map<String, Object>> handleFileUpload(MultipartFile file, String type) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", 0, "error", "File is required"));
        }

        // Validate file size (max 50MB for videos, 10MB for images)
        if (file.getSize() > 50 * 1024 * 1024) {
            return ResponseEntity.badRequest().body(Map.of("success", 0, "error", "File too large (max 50MB)"));
        }

        try {
            Path uploadDir = Paths.get(uploadPath).toAbsolutePath().normalize();
            Files.createDirectories(uploadDir);

            String ext = FileValidator.getExtension(file.getOriginalFilename());
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

    // Delete uploaded file endpoint
    @DeleteMapping("/delete")
    public ResponseEntity<Map<String, Object>> deleteFile(@RequestBody Map<String, String> request) {
        String url = request.get("url");

        if (url == null || url.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "URL is required"));
        }

        try {
            // Extract filename from URL (e.g., http://localhost:8080/uploads/abc123.jpg -> abc123.jpg)
            String filename = url.substring(url.lastIndexOf("/") + 1);

            // Validate filename to prevent path traversal attacks
            if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Invalid filename"));
            }

            Path uploadDir = Paths.get(uploadPath).toAbsolutePath().normalize();
            Path filePath = uploadDir.resolve(filename).normalize();

            // Ensure the file is within the upload directory (prevent path traversal)
            if (!filePath.startsWith(uploadDir)) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Invalid file path"));
            }

            if (Files.exists(filePath)) {
                Files.delete(filePath);
                return ResponseEntity.ok(Map.of("success", true, "message", "File deleted successfully"));
            } else {
                return ResponseEntity.ok(Map.of("success", true, "message", "File already deleted or not found"));
            }
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "error", "Failed to delete file: " + e.getMessage()
            ));
        }
    }
}
