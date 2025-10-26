package _blog.blog.controller;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import _blog.blog.utils.AvatarValidator;
import _blog.blog.utils.FileValidator;

@RestController
@RequestMapping("/media")
public class MediaController {

    @Value("${upload.path:uploads}")
    private String uploadPath;

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadFile(@RequestParam("file") MultipartFile file) {
        // Validate file is not empty
        if (file == null || file.isEmpty()) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "File is required");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        // Validate file type and size
        if (!FileValidator.isValidMediaFile(file)) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Invalid file. Only images (JPG, PNG, GIF, WebP, SVG), videos (MP4, WebM, OGG, MOV, AVI), and audio (MP3, WAV, OGG, M4A) files are allowed with appropriate size limits.");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        try {
            // Create uploads directory if it doesn't exist
            Path uploadDir = Paths.get(uploadPath).toAbsolutePath().normalize();
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }

            // Get sanitized extension
            String extension = FileValidator.getExtension(file.getOriginalFilename());
            if (extension.isEmpty()) {
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("error", "File must have a valid extension");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            // Generate unique filename
            String filename = UUID.randomUUID().toString() + "." + extension;

            // Resolve file path and ensure it's within upload directory (prevent path traversal)
            Path filePath = uploadDir.resolve(filename).normalize();
            if (!filePath.startsWith(uploadDir)) {
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("error", "Invalid file path");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            // Save file with try-with-resources to ensure InputStream is closed
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);
            }

            // Return URL
            String fileUrl = "/uploads/" + filename;

            Map<String, String> response = new HashMap<>();
            response.put("url", fileUrl);
            response.put("filename", filename);
            response.put("originalFilename", FileValidator.sanitizeFilename(file.getOriginalFilename()));
            response.put("mediaType", FileValidator.getMediaType(extension));

            return ResponseEntity.ok(response);

        } catch (IOException e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to upload file: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    @PostMapping("/upload-avatar")
    public ResponseEntity<Map<String, String>> uploadAvatar(@RequestParam("file") MultipartFile file) {
        // Validate file is not empty
        if (file == null || file.isEmpty()) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Avatar file is required");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        // Validate avatar file
        if (!AvatarValidator.isValidAvatarFile(file)) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Invalid avatar file. Only JPG, PNG, GIF, and WebP images are allowed.");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        // Check file size (max 5MB)
        if (file.getSize() > FileValidator.MAX_AVATAR_SIZE) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Avatar file size must be less than 5MB");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        // Additional validation using FileValidator
        if (!FileValidator.isValidImage(file)) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Invalid image file. File content does not match the extension.");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        try {
            // Create uploads directory if it doesn't exist
            Path uploadDir = Paths.get(uploadPath).toAbsolutePath().normalize();
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }

            // Get sanitized extension
            String extension = FileValidator.getExtension(file.getOriginalFilename());
            if (extension.isEmpty()) {
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("error", "Avatar file must have a valid extension");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            // Generate unique filename
            String filename = "avatar_" + UUID.randomUUID().toString() + "." + extension;

            // Resolve file path and ensure it's within upload directory (prevent path traversal)
            Path filePath = uploadDir.resolve(filename).normalize();
            if (!filePath.startsWith(uploadDir)) {
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("error", "Invalid file path");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            // Save file with try-with-resources to ensure InputStream is closed
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);
            }

            // Return URL
            String fileUrl = "/uploads/" + filename;

            Map<String, String> response = new HashMap<>();
            response.put("url", fileUrl);
            response.put("filename", filename);

            return ResponseEntity.ok(response);

        } catch (IOException e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to upload avatar: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
}
