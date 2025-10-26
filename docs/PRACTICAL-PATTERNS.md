# Practical Implementation Patterns

This guide shows you **real patterns from "The Dispatch" codebase** with detailed explanations. Use this when you want to understand how specific features are actually built.

---

## Table of Contents

1. [Notification System - Complete Flow](#1-notification-system)
2. [File Upload with Security](#2-file-upload-system)
3. [Pagination Pattern](#3-pagination-pattern)
4. [Builder Pattern in Practice](#4-builder-pattern)
5. [Service Layer Transaction Management](#5-transaction-management)
6. [Frontend Signal Patterns](#6-frontend-signals)
7. [Security Best Practices](#7-security-patterns)

---

## 1. Notification System

### How It Works

The notification system has **5 types** of notifications:
- `NEW_FOLLOWER` - Someone follows you
- `POST_LIKE` - Someone likes your post
- `POST_COMMENT` - Someone comments on your post
- `COMMENT_REPLY` - Someone replies to your comment
- `MENTION` - Someone mentions you

### Backend Architecture

**NotificationService.java** (Interface)
```java
public interface NotificationService {
    // Core method - creates any notification
    void createNotification(User user, User actor, NotificationType type,
                          String message, Post post, Comment comment);

    // Retrieve notifications
    Page<NotificationDto> getUserNotifications(Long userId, Pageable pageable);
    List<NotificationDto> getAllUserNotifications(Long userId);

    // Mark as read
    void markAsRead(Long notificationId, Long userId);
    void markAllAsRead(Long userId);

    // Helper methods for specific notification types
    void notifyNewFollower(User follower, User followed);
    void notifyPostLike(User liker, Post post);
    void notifyPostComment(User commenter, Post post, Comment comment);
    void notifyCommentReply(User replier, Comment parentComment, Comment reply);
}
```

**Key Pattern**: Interface-based design allows:
- Easy testing (mock the interface)
- Flexibility (swap implementations)
- Clear contract (what methods are available)

### Implementation Details

**NotificationServiceImpl.java:33-50**
```java
@Override
@Transactional
public void createNotification(User user, User actor, NotificationType type,
                              String message, Post post, Comment comment) {
    // IMPORTANT: Don't notify yourself
    if (user.getId().equals(actor.getId())) {
        return;  // Early return prevents self-notifications
    }

    // Builder pattern for clean object creation
    Notification notification = Notification.builder()
            .user(user)           // Who receives the notification
            .actor(actor)         // Who performed the action
            .type(type)           // What type of action
            .message(message)     // Human-readable message
            .post(post)           // Related post (can be null)
            .comment(comment)     // Related comment (can be null)
            .read(false)          // Starts as unread
            .build();

    notificationRepository.save(notification);
}
```

**Why `@Transactional`?**
- If save fails, everything rolls back
- Database consistency guaranteed
- No orphaned notifications

### Helper Methods (Real Examples)

**NotifyPostLike** (NotificationServiceImpl.java:102-106)
```java
@Override
public void notifyPostLike(User liker, Post post) {
    // Truncate title to 50 chars to prevent long notifications
    String message = liker.getUsername() + " liked your post: "
                   + truncate(post.getTitle(), 50);

    // Notify the post author
    createNotification(post.getAuthor(), liker, NotificationType.POST_LIKE,
                      message, post, null);
}
```

**NotifyCommentReply** (NotificationServiceImpl.java:114-119)
```java
@Override
public void notifyCommentReply(User replier, Comment parentComment, Comment reply) {
    String message = replier.getUsername() + " replied to your comment";

    // Notify the parent comment's author
    createNotification(parentComment.getAuthor(), replier,
                      NotificationType.COMMENT_REPLY, message,
                      parentComment.getPost(), reply);
}
```

**Utility Method** (NotificationServiceImpl.java:135-138)
```java
private String truncate(String text, int maxLength) {
    if (text == null) return "";
    return text.length() > maxLength
         ? text.substring(0, maxLength) + "..."
         : text;
}
```

### Pagination Support

**getUserNotifications** (NotificationServiceImpl.java:52-59)
```java
@Override
public Page<NotificationDto> getUserNotifications(Long userId, Pageable pageable) {
    User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));

    // Find notifications ordered by newest first
    Page<Notification> notifications = notificationRepository
            .findByUserOrderByCreatedAtDesc(user, pageable);

    // Convert each Notification entity to DTO
    return notifications.map(this::toDto);
}
```

**What is `Page`?**
- Spring Data provides `Page<T>` for pagination
- Contains: content (list of items), total pages, total elements, current page
- `.map(this::toDto)` transforms each entity to DTO

### Controller Layer

**NotificationController.java:34-45**
```java
@GetMapping
public ResponseEntity<List<NotificationDto>> getNotifications(
        Authentication auth,
        @RequestParam(defaultValue = "0") int page,      // Default: page 0
        @RequestParam(defaultValue = "20") int size) {   // Default: 20 items

    User user = userService.getUserByUsername(auth.getName());

    // Create Pageable object
    Pageable pageable = PageRequest.of(page, size);

    Page<NotificationDto> notifications = notificationService
            .getUserNotifications(user.getId(), pageable);

    // Return only the content (list), not the Page wrapper
    return ResponseEntity.ok(notifications.getContent());
}
```

**Frontend Request Example**:
```typescript
// Get page 0 (first 20 notifications)
GET /notifications?page=0&size=20

// Get page 1 (next 20 notifications)
GET /notifications?page=1&size=20
```

### Unread Count

**NotificationController.java:54-59**
```java
@GetMapping("/unread-count")
public ResponseEntity<Long> getUnreadCount(Authentication auth) {
    User user = userService.getUserByUsername(auth.getName());
    Long count = notificationService.getUnreadCount(user.getId());
    return ResponseEntity.ok(count);
}
```

**Used for**: Badge on notification icon showing "5 unread"

### Mark as Read

**NotificationController.java:61-66**
```java
@PutMapping("/{id}/read")
public ResponseEntity<Void> markAsRead(@PathVariable Long id, Authentication auth) {
    User user = userService.getUserByUsername(auth.getName());
    notificationService.markAsRead(id, user.getId());
    return ResponseEntity.ok().build();  // 200 OK with empty body
}
```

**Why pass `user.getId()`?**
- Security: Prevent user A from marking user B's notifications as read
- The service verifies the notification belongs to the user

### Frontend Toast Notifications

**notification.service.ts** (Different from backend notifications!)
```typescript
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly notifications = signal<Notification[]>([]);
  readonly notifications$ = this.notifications.asReadonly();

  private idCounter = 0;

  show(message: string, type: 'success' | 'error' | 'info' | 'warning' = 'info',
       duration: number = 5000) {
    const id = this.idCounter++;
    const notification = { id, message, type, duration };

    // Add to array
    this.notifications.update(n => [...n, notification]);

    // Auto-dismiss after duration
    if (duration > 0) {
      setTimeout(() => this.dismiss(id), duration);
    }
  }

  success(message: string, duration?: number) {
    this.show(message, 'success', duration);
  }

  error(message: string, duration?: number) {
    this.show(message, 'error', duration);
  }

  dismiss(id: number) {
    this.notifications.update(n =>
      n.filter(notification => notification.id !== id)
    );
  }
}
```

**Usage in Component**:
```typescript
constructor(private notificationService: NotificationService) {}

likePost(postId: number) {
  this.apiService.likePost(postId).subscribe({
    next: () => {
      this.notificationService.success('Post liked!');
    },
    error: (err) => {
      this.notificationService.error('Failed to like post');
    }
  });
}
```

### Complete Flow Example

**User A likes User B's post**:

1. **Frontend** (home.component.ts):
```typescript
likePost(postId: number) {
  this.apiService.likePost(postId).subscribe({
    next: () => {
      this.notificationService.success('Post liked!');
      this.loadPosts(); // Refresh to show updated like count
    }
  });
}
```

2. **API Call** (api.service.ts):
```typescript
likePost(postId: number): Observable<void> {
  return this.http.post<void>(`${this.apiUrl}/likes/${postId}`, {});
}
```

3. **Backend Controller** (LikeController.java):
```java
@PostMapping("/{postId}")
public ResponseEntity<String> likePost(@PathVariable Long postId,
                                      Authentication auth) {
    User user = userService.getUserByUsername(auth.getName());
    likeService.likePost(postId, user);
    return ResponseEntity.ok("Post liked");
}
```

4. **Service Layer** (LikeService.java):
```java
@Transactional
public void likePost(Long postId, User user) {
    Post post = postRepository.findById(postId)
            .orElseThrow(() -> new RuntimeException("Post not found"));

    // Add like
    post.getLikedBy().add(user);
    postRepository.save(post);

    // CREATE NOTIFICATION
    notificationService.notifyPostLike(user, post);
}
```

5. **Notification Created** (NotificationService):
```java
public void notifyPostLike(User liker, Post post) {
    String message = liker.getUsername() + " liked your post: "
                   + truncate(post.getTitle(), 50);
    createNotification(post.getAuthor(), liker, NotificationType.POST_LIKE,
                      message, post, null);
}
```

6. **User B sees notification**:
```
GET /notifications
Response: [
  {
    "id": 123,
    "actorUsername": "userA",
    "type": "POST_LIKE",
    "message": "userA liked your post: My Amazing Post",
    "postId": 456,
    "read": false,
    "createdAt": "2024-01-15T10:30:00"
  }
]
```

---

## 2. File Upload System

### Security-First Design

**The Dispatch** implements multiple layers of security for file uploads.

### Controller Layer

**MediaController.java:31-94**
```java
@PostMapping("/upload")
public ResponseEntity<Map<String, String>> uploadFile(
        @RequestParam("file") MultipartFile file) {

    // Layer 1: Check file exists
    if (file == null || file.isEmpty()) {
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("error", "File is required");
        return ResponseEntity.badRequest().body(errorResponse);
    }

    // Layer 2: Validate file type and size
    if (!FileValidator.isValidMediaFile(file)) {
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("error", "Invalid file. Only images, videos, and audio files allowed.");
        return ResponseEntity.badRequest().body(errorResponse);
    }

    try {
        // Create uploads directory if it doesn't exist
        Path uploadDir = Paths.get(uploadPath).toAbsolutePath().normalize();
        if (!Files.exists(uploadDir)) {
            Files.createDirectories(uploadDir);
        }

        // Layer 3: Sanitize filename
        String extension = FileValidator.getExtension(file.getOriginalFilename());
        if (extension.isEmpty()) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "File must have a valid extension");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        // Generate unique filename (prevents overwrites and conflicts)
        String filename = UUID.randomUUID().toString() + "." + extension;

        // Layer 4: Prevent path traversal attacks
        Path filePath = uploadDir.resolve(filename).normalize();
        if (!filePath.startsWith(uploadDir)) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Invalid file path");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        // Layer 5: Save with try-with-resources (auto-closes stream)
        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);
        }

        // Return metadata
        String fileUrl = "/uploads/" + filename;
        Map<String, String> response = new HashMap<>();
        response.put("url", fileUrl);
        response.put("filename", filename);
        response.put("originalFilename",
                    FileValidator.sanitizeFilename(file.getOriginalFilename()));
        response.put("mediaType", FileValidator.getMediaType(extension));

        return ResponseEntity.ok(response);

    } catch (IOException e) {
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("error", "Failed to upload file: " + e.getMessage());
        return ResponseEntity.internalServerError().body(errorResponse);
    }
}
```

### FileValidator - Magic Bytes

**Why check "magic bytes"?**
- Users can rename `virus.exe` to `cute-cat.jpg`
- Extension checking is not enough!
- Magic bytes = first few bytes of file that identify its true type

**FileValidator.java:34-37**
```java
// Magic bytes for file type verification
private static final byte[] JPEG_MAGIC = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
private static final byte[] PNG_MAGIC = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47};
private static final byte[] GIF_MAGIC = new byte[]{0x47, 0x49, 0x46}; // "GIF"
private static final byte[] WEBP_MAGIC = new byte[]{0x52, 0x49, 0x46, 0x46}; // "RIFF"
```

**Verification** (FileValidator.java:135-174):
```java
private static boolean verifyImageMagicBytes(MultipartFile file) {
    try {
        byte[] bytes = new byte[12]; // Read first 12 bytes
        int read = file.getInputStream().read(bytes);

        if (read < 3) {
            return false; // File too small
        }

        // Check for JPEG (0xFF 0xD8 0xFF)
        if (bytes[0] == JPEG_MAGIC[0] &&
            bytes[1] == JPEG_MAGIC[1] &&
            bytes[2] == JPEG_MAGIC[2]) {
            return true;
        }

        // Check for PNG (0x89 0x50 0x4E 0x47)
        if (read >= 4 &&
            bytes[0] == PNG_MAGIC[0] &&
            bytes[1] == PNG_MAGIC[1] &&
            bytes[2] == PNG_MAGIC[2] &&
            bytes[3] == PNG_MAGIC[3]) {
            return true;
        }

        // Check for GIF (0x47 0x49 0x46 = "GIF")
        if (bytes[0] == GIF_MAGIC[0] &&
            bytes[1] == GIF_MAGIC[1] &&
            bytes[2] == GIF_MAGIC[2]) {
            return true;
        }

        // Check for WEBP (RIFF....WEBP)
        if (read >= 12 &&
            bytes[0] == WEBP_MAGIC[0] && bytes[1] == WEBP_MAGIC[1] &&
            bytes[2] == WEBP_MAGIC[2] && bytes[3] == WEBP_MAGIC[3] &&
            bytes[8] == 0x57 && bytes[9] == 0x45 &&
            bytes[10] == 0x42 && bytes[11] == 0x50) {
            return true;
        }

        // SVG is XML-based, harder to validate by magic bytes
        String extension = getExtension(file.getOriginalFilename());
        return "svg".equals(extension);

    } catch (IOException e) {
        return false;
    }
}
```

### Filename Sanitization

**Prevents path traversal attacks** (FileValidator.java:42-62):
```java
public static String sanitizeFilename(String filename) {
    if (filename == null || filename.isEmpty()) {
        return "";
    }

    // Remove path separators (/ and \)
    String sanitized = filename.replaceAll("[/\\\\]", "");

    // Remove null bytes
    sanitized = sanitized.replace("\0", "");

    // Remove parent directory references (..)
    sanitized = sanitized.replaceAll("\\.\\.", "");

    // Limit length to 255 characters
    if (sanitized.length() > 255) {
        sanitized = sanitized.substring(0, 255);
    }

    return sanitized;
}
```

**Attack Examples Prevented**:
```
Malicious Input: "../../../etc/passwd"
After Sanitization: "etcpasswd"

Malicious Input: "..\\..\\windows\\system32\\config"
After Sanitization: "windowssystem32config"

Malicious Input: "file\0.jpg" (null byte injection)
After Sanitization: "file.jpg"
```

### Size Limits

**FileValidator.java:28-31**
```java
public static final long MAX_IMAGE_SIZE = 10 * 1024 * 1024;   // 10MB
public static final long MAX_VIDEO_SIZE = 100 * 1024 * 1024;  // 100MB
public static final long MAX_AUDIO_SIZE = 50 * 1024 * 1024;   // 50MB
public static final long MAX_AVATAR_SIZE = 5 * 1024 * 1024;   // 5MB
```

**Why different sizes?**
- Images: 10MB (reasonable for high-res photos)
- Videos: 100MB (allows short clips)
- Audio: 50MB (allows podcasts/music)
- Avatars: 5MB (should be smaller for profile pics)

### Allowed Extensions

**FileValidator.java:13-25**
```java
private static final Set<String> ALLOWED_IMAGE_EXTENSIONS = new HashSet<>(
    Arrays.asList("jpg", "jpeg", "png", "gif", "webp", "svg")
);

private static final Set<String> ALLOWED_VIDEO_EXTENSIONS = new HashSet<>(
    Arrays.asList("mp4", "webm", "ogg", "mov", "avi")
);

private static final Set<String> ALLOWED_AUDIO_EXTENSIONS = new HashSet<>(
    Arrays.asList("mp3", "wav", "ogg", "m4a")
);
```

**Why use `Set` instead of `List`?**
- `.contains()` is O(1) for Set vs O(n) for List
- No duplicates allowed
- Perfect for lookup operations

### Complete Validation Flow

**FileValidator.java:85-113**
```java
public static boolean isValidMediaFile(MultipartFile file) {
    if (file == null || file.isEmpty()) {
        return false;
    }

    String extension = getExtension(file.getOriginalFilename());

    // Step 1: Check extension is allowed
    boolean isAllowedExtension =
        ALLOWED_IMAGE_EXTENSIONS.contains(extension) ||
        ALLOWED_VIDEO_EXTENSIONS.contains(extension) ||
        ALLOWED_AUDIO_EXTENSIONS.contains(extension);

    if (!isAllowedExtension) {
        return false;
    }

    // Step 2: Check file size based on type
    long maxSize = getMaxSizeForExtension(extension);
    if (file.getSize() > maxSize) {
        return false;
    }

    // Step 3: Verify magic bytes for images
    if (ALLOWED_IMAGE_EXTENSIONS.contains(extension)) {
        return verifyImageMagicBytes(file);
    }

    return true;
}
```

### Avatar Upload (Specialized)

**MediaController.java:96-171**
```java
@PostMapping("/upload-avatar")
public ResponseEntity<Map<String, String>> uploadAvatar(
        @RequestParam("file") MultipartFile file) {

    // Validation
    if (file == null || file.isEmpty()) {
        return ResponseEntity.badRequest()
            .body(Map.of("error", "Avatar file is required"));
    }

    // Avatar-specific validation
    if (!AvatarValidator.isValidAvatarFile(file)) {
        return ResponseEntity.badRequest()
            .body(Map.of("error", "Only JPG, PNG, GIF, and WebP allowed"));
    }

    // Stricter size limit for avatars
    if (file.getSize() > FileValidator.MAX_AVATAR_SIZE) { // 5MB
        return ResponseEntity.badRequest()
            .body(Map.of("error", "Avatar must be less than 5MB"));
    }

    // Double-check with FileValidator
    if (!FileValidator.isValidImage(file)) {
        return ResponseEntity.badRequest()
            .body(Map.of("error", "File content does not match extension"));
    }

    try {
        // ... same save logic but with "avatar_" prefix
        String filename = "avatar_" + UUID.randomUUID() + "." + extension;
        // ... save to disk

        return ResponseEntity.ok(Map.of(
            "url", fileUrl,
            "filename", filename
        ));

    } catch (IOException e) {
        return ResponseEntity.internalServerError()
            .body(Map.of("error", "Failed to upload: " + e.getMessage()));
    }
}
```

**Why separate avatar endpoint?**
- Different size limits (5MB vs 10MB)
- Different filename prefix (`avatar_` for organization)
- Additional validation (avatars should be images only)
- Could add image processing (resize, crop) in the future

### Frontend Usage

**Uploading an Image**:
```typescript
uploadImage(file: File): Observable<UploadResponse> {
  const formData = new FormData();
  formData.append('file', file);

  return this.http.post<UploadResponse>(
    `${this.apiUrl}/media/upload`,
    formData
  );
}

// In component
onFileSelected(event: Event) {
  const input = event.target as HTMLInputElement;
  if (input.files && input.files.length > 0) {
    const file = input.files[0];

    this.apiService.uploadImage(file).subscribe({
      next: (response) => {
        console.log('Uploaded:', response.url);
        this.imageUrl = response.url; // Use in template
      },
      error: (err) => {
        this.notificationService.error('Upload failed');
      }
    });
  }
}
```

---

## 3. Pagination Pattern

### What is Pagination?

Instead of loading 10,000 posts at once:
- Load 20 posts at a time
- User clicks "Next" to load next 20
- Much faster, less memory, better UX

### Spring Data Pagination

**Pageable Interface**:
```java
public interface PostRepository extends JpaRepository<Post, Long> {
    // Without pagination (loads everything!)
    List<Post> findByAuthor(User author);

    // With pagination (loads one page)
    Page<Post> findByAuthor(User author, Pageable pageable);
}
```

### Using Pageable

**NotificationController.java:34-45**
```java
@GetMapping
public ResponseEntity<List<NotificationDto>> getNotifications(
        Authentication auth,
        @RequestParam(defaultValue = "0") int page,    // URL: ?page=0
        @RequestParam(defaultValue = "20") int size) { // URL: &size=20

    User user = userService.getUserByUsername(auth.getName());

    // Create Pageable object
    Pageable pageable = PageRequest.of(page, size);

    // Service returns Page<NotificationDto>
    Page<NotificationDto> notifications =
        notificationService.getUserNotifications(user.getId(), pageable);

    // Extract just the content (list of items)
    return ResponseEntity.ok(notifications.getContent());
}
```

### Page Object

**What `Page<T>` contains**:
```java
Page<Notification> page = repository.findByUserOrderByCreatedAtDesc(user, pageable);

page.getContent()          // List<Notification> - the actual items
page.getTotalElements()    // long - total items across all pages (e.g., 153)
page.getTotalPages()       // int - total pages (e.g., 8 pages of 20)
page.getNumber()           // int - current page number (0-indexed)
page.getSize()             // int - items per page (20)
page.hasNext()             // boolean - is there a next page?
page.hasPrevious()         // boolean - is there a previous page?
page.isFirst()             // boolean - is this the first page?
page.isLast()              // boolean - is this the last page?
```

### Frontend Pagination

**Component with Pagination**:
```typescript
export class NotificationsComponent {
  notifications = signal<Notification[]>([]);
  currentPage = signal(0);
  totalPages = signal(0);
  loading = signal(false);

  loadNotifications(page: number) {
    this.loading.set(true);

    // Call API with page number
    this.apiService.getNotifications(page, 20).subscribe({
      next: (response) => {
        this.notifications.set(response.content);
        this.currentPage.set(response.number);
        this.totalPages.set(response.totalPages);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
      }
    });
  }

  nextPage() {
    if (this.currentPage() < this.totalPages() - 1) {
      this.loadNotifications(this.currentPage() + 1);
    }
  }

  previousPage() {
    if (this.currentPage() > 0) {
      this.loadNotifications(this.currentPage() - 1);
    }
  }
}
```

**Template**:
```html
<div class="notifications">
  @if (loading()) {
    <div>Loading...</div>
  } @else {
    @for (notification of notifications(); track notification.id) {
      <div class="notification-item">
        {{ notification.message }}
      </div>
    }
  }
</div>

<div class="pagination">
  <button (click)="previousPage()"
          [disabled]="currentPage() === 0">
    Previous
  </button>

  <span>Page {{ currentPage() + 1 }} of {{ totalPages() }}</span>

  <button (click)="nextPage()"
          [disabled]="currentPage() >= totalPages() - 1">
    Next
  </button>
</div>
```

### Infinite Scroll Pattern

**Instead of pagination buttons, load more on scroll**:
```typescript
export class FeedComponent {
  posts = signal<Post[]>([]);
  currentPage = 0;
  loading = false;
  allLoaded = false;

  @HostListener('window:scroll', ['$event'])
  onScroll() {
    // Check if user scrolled to bottom
    const scrollHeight = document.documentElement.scrollHeight;
    const scrollTop = document.documentElement.scrollTop;
    const clientHeight = document.documentElement.clientHeight;

    if (scrollTop + clientHeight >= scrollHeight - 100 &&
        !this.loading && !this.allLoaded) {
      this.loadMorePosts();
    }
  }

  loadMorePosts() {
    this.loading = true;

    this.apiService.getPosts(this.currentPage, 20).subscribe({
      next: (response) => {
        if (response.content.length === 0) {
          this.allLoaded = true; // No more posts
        } else {
          // Append to existing posts
          this.posts.update(current => [...current, ...response.content]);
          this.currentPage++;
        }
        this.loading = false;
      }
    });
  }
}
```

---

## 4. Builder Pattern

### What is Builder Pattern?

Instead of:
```java
Notification n = new Notification();
n.setUser(user);
n.setActor(actor);
n.setType(type);
n.setMessage(message);
n.setPost(post);
n.setComment(comment);
n.setRead(false);
```

Use builder:
```java
Notification n = Notification.builder()
    .user(user)
    .actor(actor)
    .type(type)
    .message(message)
    .post(post)
    .comment(comment)
    .read(false)
    .build();
```

### How to Enable Builder

**Add Lombok annotation** to entity:
```java
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder  // <-- This generates the builder
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private User user;

    @ManyToOne
    private User actor;

    private NotificationType type;
    private String message;

    @ManyToOne
    private Post post;

    @ManyToOne
    private Comment comment;

    private boolean read;

    @CreationTimestamp
    private Date createdAt;
}
```

### Why Use Builder?

1. **Readable**: Clear what each value is for
2. **Immutable Objects**: Can make builder create final fields
3. **Optional Parameters**: Don't need to call setters for null values
4. **Validation**: Can add validation in `build()` method

### Builder in DTOs

**NotificationDto.java**:
```java
@Getter
@Setter
@Builder
public class NotificationDto {
    private Long id;
    private String actorUsername;
    private String actorAvatar;
    private NotificationType type;
    private String message;
    private Long postId;
    private Long commentId;
    private boolean read;
    private Date createdAt;
}
```

**Usage in Service** (NotificationServiceImpl.java:121-133):
```java
private NotificationDto toDto(Notification notification) {
    return NotificationDto.builder()
            .id(notification.getId())
            .actorUsername(notification.getActor() != null
                ? notification.getActor().getUsername() : null)
            .actorAvatar(notification.getActor() != null
                ? notification.getActor().getAvatar() : null)
            .type(notification.getType())
            .message(notification.getMessage())
            .postId(notification.getPost() != null
                ? notification.getPost().getId() : null)
            .commentId(notification.getComment() != null
                ? notification.getComment().getId() : null)
            .read(notification.isRead())
            .createdAt(notification.getCreatedAt())
            .build();
}
```

**Why null checks?**
- Some notifications don't have a post (like NEW_FOLLOWER)
- Some don't have a comment (like POST_LIKE)
- Prevents NullPointerException

---

## 5. Transaction Management

### What is a Transaction?

A transaction is **all-or-nothing**:
- Either all operations succeed
- Or all operations rollback (undo)

**Example**: Transfer money from Account A to Account B
```java
accountA.balance -= 100;  // Step 1
accountB.balance += 100;  // Step 2
```

If Step 2 fails, Step 1 must rollback!

### @Transactional Annotation

**NotificationServiceImpl.java:31-50**
```java
@Override
@Transactional  // <-- Everything in this method is one transaction
public void createNotification(User user, User actor, NotificationType type,
                              String message, Post post, Comment comment) {
    if (user.getId().equals(actor.getId())) {
        return;
    }

    Notification notification = Notification.builder()
            .user(user)
            .actor(actor)
            .type(type)
            .message(message)
            .post(post)
            .comment(comment)
            .read(false)
            .build();

    notificationRepository.save(notification);
    // If save fails, everything rolls back
}
```

### Transaction Propagation

**When to use `@Transactional`**:

1. **Multiple Database Operations**:
```java
@Transactional
public void deleteUser(Long userId) {
    User user = userRepository.findById(userId).orElseThrow();

    // Step 1: Delete user's posts
    postRepository.deleteByAuthor(user);

    // Step 2: Delete user's comments
    commentRepository.deleteByAuthor(user);

    // Step 3: Delete user
    userRepository.delete(user);

    // If any step fails, ALL steps rollback
}
```

2. **Updating Related Entities**:
```java
@Transactional
public void likePost(Long postId, User user) {
    Post post = postRepository.findById(postId).orElseThrow();

    // Step 1: Add like
    post.getLikedBy().add(user);
    postRepository.save(post);

    // Step 2: Create notification
    notificationService.notifyPostLike(user, post);

    // If notification fails, like is also rolled back
}
```

### Rollback Behavior

**By default**, `@Transactional` rolls back on:
- `RuntimeException` and its subclasses
- `Error`

**Does NOT rollback on**:
- Checked exceptions (unless you specify)

**Custom Rollback**:
```java
@Transactional(rollbackFor = Exception.class)  // Rollback on any exception
public void riskyOperation() {
    // ...
}

@Transactional(noRollbackFor = SpecificException.class)  // Don't rollback on this
public void operation() {
    // ...
}
```

### Read-Only Transactions

**For performance optimization**:
```java
@Transactional(readOnly = true)
public List<Post> getAllPosts() {
    return postRepository.findAll();
}
```

**Benefits**:
- Database optimizations (no write locks)
- Hibernate optimizations (skip dirty checking)
- Prevents accidental modifications

### Transaction Isolation

**Dealing with concurrent access**:
```java
@Transactional(isolation = Isolation.READ_COMMITTED)
public void updatePost(Long postId, String newTitle) {
    // Only reads committed data from other transactions
}
```

**Isolation Levels**:
- `READ_UNCOMMITTED`: Can read uncommitted changes (dirty reads)
- `READ_COMMITTED`: Only reads committed changes (default)
- `REPEATABLE_READ`: Same read twice in transaction = same result
- `SERIALIZABLE`: Full isolation, slowest

---

## 6. Frontend Signals

### What are Signals?

Signals are Angular's **reactive primitive** (introduced in Angular 16+):
- Alternative to `BehaviorSubject` and `Observable` for state
- Automatically trigger re-renders when changed
- Simpler than RxJS for simple state

### Basic Signal Usage

**notification.service.ts:1-28**
```typescript
@Injectable({ providedIn: 'root' })
export class NotificationService {
  // Private writable signal
  private readonly notifications = signal<Notification[]>([]);

  // Public readonly signal (can't be modified from outside)
  readonly notifications$ = this.notifications.asReadonly();

  private idCounter = 0;

  show(message: string, type: 'success' | 'error' | 'info' | 'warning' = 'info',
       duration: number = 5000) {
    const id = this.idCounter++;
    const notification = { id, message, type, duration };

    // Update signal (immutable update)
    this.notifications.update(n => [...n, notification]);

    // Auto-dismiss after duration
    if (duration > 0) {
      setTimeout(() => this.dismiss(id), duration);
    }
  }

  dismiss(id: number) {
    // Remove notification by filtering
    this.notifications.update(n =>
      n.filter(notification => notification.id !== id)
    );
  }
}
```

### Signal Methods

**Create Signal**:
```typescript
const count = signal(0);              // signal<number>
const user = signal<User | null>(null);  // signal<User | null>
const posts = signal<Post[]>([]);     // signal<Post[]>
```

**Read Signal**:
```typescript
const currentCount = count();  // Call it like a function
console.log('Count:', currentCount);
```

**Update Signal**:
```typescript
// Method 1: Set new value
count.set(5);

// Method 2: Update based on current value
count.update(current => current + 1);

// For arrays
posts.update(current => [...current, newPost]);  // Add item
posts.update(current => current.filter(p => p.id !== deleteId));  // Remove item
```

### Computed Signals

**Derived state** (automatically recomputes when dependencies change):
```typescript
const firstName = signal('John');
const lastName = signal('Doe');

// Computed signal
const fullName = computed(() => `${firstName()} ${lastName()}`);

console.log(fullName());  // "John Doe"

firstName.set('Jane');
console.log(fullName());  // "Jane Doe" (auto-updated!)
```

### Effects

**Side effects** that run when signals change:
```typescript
const theme = signal('light');

effect(() => {
  // This runs whenever theme() changes
  console.log('Theme changed to:', theme());
  document.body.className = theme();
});

theme.set('dark');  // Console logs: "Theme changed to: dark"
```

### Signals in Components

**home.component.ts**:
```typescript
@Component({
  selector: 'app-home',
  standalone: true,
  templateUrl: './home.html'
})
export class HomeComponent {
  // State as signals
  posts = signal<Post[]>([]);
  loading = signal(false);
  error = signal<string | null>(null);

  // Computed signal
  postCount = computed(() => this.posts().length);

  constructor(private apiService: ApiService) {
    this.loadPosts();
  }

  loadPosts() {
    this.loading.set(true);
    this.error.set(null);

    this.apiService.getPosts().subscribe({
      next: (posts) => {
        this.posts.set(posts);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set('Failed to load posts');
        this.loading.set(false);
      }
    });
  }

  likePost(postId: number) {
    this.apiService.likePost(postId).subscribe({
      next: () => {
        // Update the specific post
        this.posts.update(current =>
          current.map(post =>
            post.id === postId
              ? { ...post, likesCount: post.likesCount + 1, liked: true }
              : post
          )
        );
      }
    });
  }

  deletePost(postId: number) {
    this.apiService.deletePost(postId).subscribe({
      next: () => {
        // Remove the post
        this.posts.update(current =>
          current.filter(post => post.id !== postId)
        );
      }
    });
  }
}
```

**Template**:
```html
@if (loading()) {
  <div>Loading...</div>
} @else if (error()) {
  <div class="error">{{ error() }}</div>
} @else {
  <div>Total posts: {{ postCount() }}</div>

  @for (post of posts(); track post.id) {
    <article>
      <h2>{{ post.title }}</h2>
      <p>{{ post.content }}</p>
      <button (click)="likePost(post.id)">
        {{ post.liked ? 'Unlike' : 'Like' }} ({{ post.likesCount }})
      </button>
      <button (click)="deletePost(post.id)">Delete</button>
    </article>
  }
}
```

### Signals vs Observables

**When to use Signals**:
- Simple component state (loading, error, data)
- UI state (dropdown open, modal visible)
- Derived values (computed)

**When to use Observables**:
- HTTP requests (already Observable)
- WebSocket streams
- Complex async operations with operators (debounce, switchMap)
- Multiple subscribers

**Combining Both**:
```typescript
posts$ = signal<Post[]>([]);

constructor(private apiService: ApiService) {
  // Observable → Signal
  this.apiService.getPosts().subscribe(posts => {
    this.posts$.set(posts);
  });
}

// Or use toSignal()
posts = toSignal(this.apiService.getPosts(), { initialValue: [] });
```

---

## 7. Security Patterns

### Authentication Pattern

**How JWT Works in The Dispatch**:

1. **User logs in** → Backend generates JWT
2. **JWT stored** in `localStorage` (frontend)
3. **Every request** → JWT sent in `Authorization` header
4. **Backend validates** JWT on each request

### JWT Structure

**Example JWT**:
```
eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJqb2huZG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c
```

**Three parts** (separated by `.`):
1. **Header**: `{"alg":"HS256","typ":"JWT"}`
2. **Payload**: `{"sub":"johndoe","iat":1516239022}`
3. **Signature**: Verifies token hasn't been tampered with

### JWT Generation (Backend)

**JwtService.java**:
```java
public String generateToken(String username) {
    Map<String, Object> claims = new HashMap<>();
    return Jwts.builder()
            .setClaims(claims)
            .setSubject(username)
            .setIssuedAt(new Date(System.currentTimeMillis()))
            .setExpiration(new Date(System.currentTimeMillis() + expirationTime))
            .signWith(getSigningKey(), SignatureAlgorithm.HS256)
            .compact();
}

private Key getSigningKey() {
    byte[] keyBytes = Decoders.BASE64.decode(secretKey);
    return Keys.hmacShaKeyFor(keyBytes);
}
```

### JWT Validation (Backend)

**JwtAuthenticationFilter.java** (runs on EVERY request):
```java
@Override
protected void doFilterInternal(HttpServletRequest request,
                                HttpServletResponse response,
                                FilterChain filterChain)
        throws ServletException, IOException {

    // Step 1: Extract token from Authorization header
    String authHeader = request.getHeader("Authorization");
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
        filterChain.doFilter(request, response);
        return;
    }

    String jwt = authHeader.substring(7);  // Remove "Bearer " prefix
    String username = jwtService.extractUsername(jwt);

    // Step 2: Validate token
    if (username != null &&
        SecurityContextHolder.getContext().getAuthentication() == null) {

        UserDetails userDetails = userDetailsService.loadUserByUsername(username);

        if (jwtService.isTokenValid(jwt, userDetails)) {
            // Step 3: Set authentication in SecurityContext
            UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(
                    userDetails,
                    null,
                    userDetails.getAuthorities()
                );

            authToken.setDetails(
                new WebAuthenticationDetailsSource().buildDetails(request)
            );

            SecurityContextHolder.getContext().setAuthentication(authToken);
        }
    }

    filterChain.doFilter(request, response);
}
```

### JWT Storage (Frontend)

**auth.service.ts**:
```typescript
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly TOKEN_KEY = 'jwt_token';
  private currentUserSignal = signal<UserResponse | null>(null);

  currentUser = this.currentUserSignal.asReadonly();

  login(credentials: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>('/auth/login', credentials)
      .pipe(
        tap(response => {
          // Store token in localStorage
          localStorage.setItem(this.TOKEN_KEY, response.token);
          this.currentUserSignal.set(response.user);
        })
      );
  }

  logout() {
    localStorage.removeItem(this.TOKEN_KEY);
    this.currentUserSignal.set(null);
  }

  getToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }

  isAuthenticated(): boolean {
    const token = this.getToken();
    return token !== null && !this.isTokenExpired(token);
  }

  private isTokenExpired(token: string): boolean {
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      return payload.exp * 1000 < Date.now();
    } catch {
      return true;
    }
  }
}
```

### HTTP Interceptor (Auto JWT Injection)

**auth.interceptor.ts**:
```typescript
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const token = authService.getToken();

  // Clone request and add Authorization header
  if (token) {
    req = req.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`
      }
    });
  }

  return next(req);
};
```

**Registration** (app.config.ts):
```typescript
export const appConfig: ApplicationConfig = {
  providers: [
    provideHttpClient(
      withInterceptors([authInterceptor])  // Register interceptor
    )
  ]
};
```

### Route Guards

**auth.guard.ts**:
```typescript
export const authGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isAuthenticated()) {
    return true;  // Allow access
  } else {
    // Redirect to login
    router.navigate(['/auth/login'], {
      queryParams: { returnUrl: state.url }
    });
    return false;
  }
};
```

**Usage in Routes** (app.routes.ts):
```typescript
export const routes: Routes = [
  { path: 'auth/login', component: LoginComponent },
  {
    path: 'home',
    component: HomeComponent,
    canActivate: [authGuard]  // Protected route
  },
  {
    path: 'profile',
    component: ProfileComponent,
    canActivate: [authGuard]  // Protected route
  }
];
```

### Password Security

**Backend Password Hashing**:
```java
@Configuration
public class SecurityConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();  // Strong hashing
    }
}

@Service
public class UserService {
    private final PasswordEncoder passwordEncoder;

    public void registerUser(String username, String rawPassword) {
        // Hash password before storing
        String hashedPassword = passwordEncoder.encode(rawPassword);

        User user = new User();
        user.setUsername(username);
        user.setPassword(hashedPassword);  // Store hashed, never plain text!

        userRepository.save(user);
    }

    public boolean checkPassword(String rawPassword, String hashedPassword) {
        return passwordEncoder.matches(rawPassword, hashedPassword);
    }
}
```

### CORS Configuration

**SecurityConfig.java**:
```java
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();

    // Allow frontend origin
    configuration.setAllowedOrigins(Arrays.asList("http://localhost:4200"));

    // Allow specific methods
    configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE"));

    // Allow specific headers
    configuration.setAllowedHeaders(Arrays.asList("*"));

    // Allow credentials (cookies, authorization headers)
    configuration.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
}
```

### SQL Injection Prevention

**Spring Data JPA automatically prevents SQL injection**:

**Safe** (using Spring Data):
```java
// Query methods
List<User> findByUsername(String username);

// JPQL with parameters
@Query("SELECT u FROM User u WHERE u.username = :username")
User findByUsernameCustom(@Param("username") String username);
```

**Unsafe** (raw SQL with string concatenation - DON'T DO THIS):
```java
// NEVER DO THIS!
String sql = "SELECT * FROM users WHERE username = '" + username + "'";
// If username = "admin' OR '1'='1", you're hacked!
```

---

## Summary

This guide covered:

1. **Notification System**: Complete flow from backend creation to frontend display
2. **File Upload**: Security-first approach with magic bytes, sanitization, and validation
3. **Pagination**: Efficient data loading with Spring Data Page and Pageable
4. **Builder Pattern**: Clean object creation with Lombok @Builder
5. **Transactions**: Data consistency with @Transactional
6. **Signals**: Modern Angular reactive state management
7. **Security**: JWT authentication, password hashing, CORS, and SQL injection prevention

**Next Steps**:
- Read LEARNING-GUIDE.md for fundamentals
- Read ADVANCED-CONCEPTS.md for design patterns
- Read HOW-TO-ADD-FEATURES.md to build your own features
- Experiment with the codebase!

**Remember**: The best way to learn is to break things and fix them. Don't be afraid to experiment!
