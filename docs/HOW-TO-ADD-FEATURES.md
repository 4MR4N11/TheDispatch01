# How to Add New Features - Step-by-Step Guide

This guide shows you **exactly** how to add a new feature to the application, from database to frontend.

---

## Example: Adding a "Bookmark" Feature

Let's add the ability for users to bookmark posts for later reading.

### Step 1: Database Design

**Think about**:
- What data do I need to store?
- What are the relationships?

**For bookmarks**:
- User can bookmark many Posts
- Post can be bookmarked by many Users
- **Many-to-Many relationship**

### Step 2: Create Entity

**File**: `backend/src/main/java/_blog/blog/entity/Bookmark.java`

```java
package _blog.blog.entity;

import java.util.Date;
import org.hibernate.annotations.CreationTimestamp;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "bookmarks",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "post_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Bookmark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @CreationTimestamp
    @Column(updatable = false, name = "created_at")
    private Date createdAt;
}
```

**Key points**:
- `@UniqueConstraint`: User can only bookmark a post once
- Two `@ManyToOne` relationships
- `createdAt` to track when bookmarked

### Step 3: Update Related Entities

**In User.java**, add:
```java
@OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
private List<Bookmark> bookmarks = new ArrayList<>();
```

**In Post.java**, add:
```java
@OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
private List<Bookmark> bookmarks = new ArrayList<>();
```

### Step 4: Create Repository

**File**: `backend/src/main/java/_blog/blog/repository/BookmarkRepository.java`

```java
package _blog.blog.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import _blog.blog.entity.Bookmark;
import _blog.blog.entity.User;

@Repository
public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    // Check if user bookmarked a post
    boolean existsByUserAndPost_Id(User user, Long postId);

    // Get user's bookmarks with post details
    @Query("SELECT b FROM Bookmark b LEFT JOIN FETCH b.post WHERE b.user = :user ORDER BY b.createdAt DESC")
    List<Bookmark> findByUserWithPost(User user);

    // Find specific bookmark
    Optional<Bookmark> findByUserAndPost_Id(User user, Long postId);

    // Delete bookmark
    void deleteByUserAndPost_Id(User user, Long postId);

    // Count user's bookmarks
    long countByUser(User user);
}
```

### Step 5: Create DTOs

**BookmarkRequest.java**:
```java
package _blog.blog.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BookmarkRequest {
    @NotNull(message = "Post ID is required")
    private Long postId;
}
```

**BookmarkResponse.java**:
```java
package _blog.blog.dto;

import java.util.Date;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BookmarkResponse {
    private Long id;
    private Long postId;
    private String postTitle;
    private String postAuthor;
    private Date bookmarkedAt;
}
```

### Step 6: Create Service

**BookmarkService.java** (interface):
```java
package _blog.blog.service;

import java.util.List;
import _blog.blog.dto.BookmarkResponse;
import _blog.blog.entity.User;

public interface BookmarkService {
    void bookmarkPost(Long postId, User user);
    void removeBookmark(Long postId, User user);
    List<BookmarkResponse> getUserBookmarks(User user);
    boolean isPostBookmarked(Long postId, User user);
}
```

**BookmarkServiceImpl.java**:
```java
package _blog.blog.service;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import _blog.blog.dto.BookmarkResponse;
import _blog.blog.entity.*;
import _blog.blog.repository.*;

@Service
public class BookmarkServiceImpl implements BookmarkService {

    private final BookmarkRepository bookmarkRepository;
    private final PostRepository postRepository;

    public BookmarkServiceImpl(BookmarkRepository bookmarkRepository,
                              PostRepository postRepository) {
        this.bookmarkRepository = bookmarkRepository;
        this.postRepository = postRepository;
    }

    @Override
    @Transactional
    public void bookmarkPost(Long postId, User user) {
        // Check if already bookmarked
        if (bookmarkRepository.existsByUserAndPost_Id(user, postId)) {
            throw new RuntimeException("Post already bookmarked");
        }

        // Get post
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new RuntimeException("Post not found"));

        // Create bookmark
        Bookmark bookmark = Bookmark.builder()
            .user(user)
            .post(post)
            .build();

        bookmarkRepository.save(bookmark);
    }

    @Override
    @Transactional
    public void removeBookmark(Long postId, User user) {
        Bookmark bookmark = bookmarkRepository.findByUserAndPost_Id(user, postId)
            .orElseThrow(() -> new RuntimeException("Bookmark not found"));

        bookmarkRepository.delete(bookmark);
    }

    @Override
    public List<BookmarkResponse> getUserBookmarks(User user) {
        List<Bookmark> bookmarks = bookmarkRepository.findByUserWithPost(user);

        return bookmarks.stream()
            .map(b -> new BookmarkResponse(
                b.getId(),
                b.getPost().getId(),
                b.getPost().getTitle(),
                b.getPost().getAuthor().getUsername(),
                b.getCreatedAt()
            ))
            .collect(Collectors.toList());
    }

    @Override
    public boolean isPostBookmarked(Long postId, User user) {
        return bookmarkRepository.existsByUserAndPost_Id(user, postId);
    }
}
```

### Step 7: Create Controller

**File**: `backend/src/main/java/_blog/blog/controller/BookmarkController.java`

```java
package _blog.blog.controller;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import _blog.blog.dto.BookmarkResponse;
import _blog.blog.entity.User;
import _blog.blog.service.BookmarkService;
import _blog.blog.service.UserService;

@RestController
@RequestMapping("/bookmarks")
public class BookmarkController {

    private final BookmarkService bookmarkService;
    private final UserService userService;

    public BookmarkController(BookmarkService bookmarkService,
                             UserService userService) {
        this.bookmarkService = bookmarkService;
        this.userService = userService;
    }

    @PostMapping("/{postId}")
    public ResponseEntity<String> bookmarkPost(
            @PathVariable Long postId,
            Authentication auth) {
        User user = userService.getUserByUsername(auth.getName());
        bookmarkService.bookmarkPost(postId, user);
        return ResponseEntity.ok("Post bookmarked successfully");
    }

    @DeleteMapping("/{postId}")
    public ResponseEntity<String> removeBookmark(
            @PathVariable Long postId,
            Authentication auth) {
        User user = userService.getUserByUsername(auth.getName());
        bookmarkService.removeBookmark(postId, user);
        return ResponseEntity.ok("Bookmark removed successfully");
    }

    @GetMapping("/my-bookmarks")
    public ResponseEntity<List<BookmarkResponse>> getMyBookmarks(Authentication auth) {
        User user = userService.getUserByUsername(auth.getName());
        List<BookmarkResponse> bookmarks = bookmarkService.getUserBookmarks(user);
        return ResponseEntity.ok(bookmarks);
    }

    @GetMapping("/check/{postId}")
    public ResponseEntity<Boolean> isBookmarked(
            @PathVariable Long postId,
            Authentication auth) {
        User user = userService.getUserByUsername(auth.getName());
        boolean bookmarked = bookmarkService.isPostBookmarked(postId, user);
        return ResponseEntity.ok(bookmarked);
    }
}
```

### Step 8: Test Backend with Postman/curl

```bash
# Bookmark a post
curl -X POST http://localhost:8080/bookmarks/42 \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"

# Get my bookmarks
curl -X GET http://localhost:8080/bookmarks/my-bookmarks \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"

# Check if bookmarked
curl -X GET http://localhost:8080/bookmarks/check/42 \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"

# Remove bookmark
curl -X DELETE http://localhost:8080/bookmarks/42 \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

---

## Frontend Implementation

### Step 9: Update Models

**File**: `frontend/src/app/shared/models/models.ts`

Add:
```typescript
export interface BookmarkResponse {
  id: number;
  postId: number;
  postTitle: string;
  postAuthor: string;
  bookmarkedAt: string | Date;
}
```

### Step 10: Update API Service

**File**: `frontend/src/app/core/auth/api.service.ts`

Add methods:
```typescript
// Bookmarks
bookmarkPost(postId: number): Observable<string> {
  return this.http.post(`${this.baseUrl}/bookmarks/${postId}`, {},
    { responseType: 'text' });
}

removeBookmark(postId: number): Observable<string> {
  return this.http.delete(`${this.baseUrl}/bookmarks/${postId}`,
    { responseType: 'text' });
}

getMyBookmarks(): Observable<BookmarkResponse[]> {
  return this.http.get<BookmarkResponse[]>(`${this.baseUrl}/bookmarks/my-bookmarks`);
}

isPostBookmarked(postId: number): Observable<boolean> {
  return this.http.get<boolean>(`${this.baseUrl}/bookmarks/check/${postId}`);
}
```

### Step 11: Add Bookmark Button to Post

**File**: `frontend/src/app/features/home/home.html`

Add bookmark button:
```html
<div class="post-actions">
  <!-- Existing like/comment buttons -->

  <!-- NEW: Bookmark button -->
  <button class="action-btn" (click)="toggleBookmark(post.id, $event)">
    <svg width="20" height="20" viewBox="0 0 24 24"
         [attr.fill]="isBookmarked(post.id) ? 'gold' : 'none'"
         stroke="currentColor">
      <path d="M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z"/>
    </svg>
    <span>{{ isBookmarked(post.id) ? 'Bookmarked' : 'Bookmark' }}</span>
  </button>
</div>
```

### Step 12: Add Logic to Component

**File**: `frontend/src/app/features/home/home.ts`

```typescript
export class HomeComponent {
  // Add signal for bookmarked posts
  protected readonly bookmarkedPostIds = signal<Set<number>>(new Set());

  constructor() {
    this.loadFeed();
    this.loadBookmarks(); // Load bookmarks on init
  }

  private loadBookmarks() {
    this.apiService.getMyBookmarks().subscribe({
      next: (bookmarks) => {
        const ids = new Set(bookmarks.map(b => b.postId));
        this.bookmarkedPostIds.set(ids);
      },
      error: (error) => {
        console.error('Failed to load bookmarks', error);
      }
    });
  }

  toggleBookmark(postId: number, event: Event) {
    event.stopPropagation();

    const isCurrentlyBookmarked = this.isBookmarked(postId);

    if (isCurrentlyBookmarked) {
      this.apiService.removeBookmark(postId).subscribe({
        next: () => {
          this.notificationService.success('Bookmark removed');
          const newSet = new Set(this.bookmarkedPostIds());
          newSet.delete(postId);
          this.bookmarkedPostIds.set(newSet);
        },
        error: (error) => {
          this.notificationService.error('Failed to remove bookmark');
        }
      });
    } else {
      this.apiService.bookmarkPost(postId).subscribe({
        next: () => {
          this.notificationService.success('Post bookmarked');
          const newSet = new Set(this.bookmarkedPostIds());
          newSet.add(postId);
          this.bookmarkedPostIds.set(newSet);
        },
        error: (error) => {
          this.notificationService.error('Failed to bookmark post');
        }
      });
    }
  }

  isBookmarked(postId: number): boolean {
    return this.bookmarkedPostIds().has(postId);
  }
}
```

### Step 13: Create Bookmarks Page

**File**: `frontend/src/app/features/bookmarks/bookmarks.component.ts`

```typescript
import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { ApiService } from '../../core/auth/api.service';
import { BookmarkResponse } from '../../shared/models/models';

@Component({
  selector: 'app-bookmarks',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="bookmarks-page">
      <h1>My Bookmarks</h1>

      @if (loading()) {
        <div>Loading...</div>
      } @else if (bookmarks().length === 0) {
        <div class="empty-state">
          <p>No bookmarks yet. Start bookmarking posts you want to read later!</p>
        </div>
      } @else {
        <div class="bookmarks-list">
          @for (bookmark of bookmarks(); track bookmark.id) {
            <div class="bookmark-card" (click)="viewPost(bookmark.postId)">
              <h3>{{ bookmark.postTitle }}</h3>
              <p>By {{ bookmark.postAuthor }}</p>
              <small>Bookmarked {{ getTimeAgo(bookmark.bookmarkedAt) }}</small>
            </div>
          }
        </div>
      }
    </div>
  `,
  styles: [`
    .bookmarks-page {
      padding: 20px;
    }
    .bookmark-card {
      border: 1px solid #ddd;
      padding: 15px;
      margin-bottom: 10px;
      cursor: pointer;
    }
    .bookmark-card:hover {
      background-color: #f5f5f5;
    }
  `]
})
export class BookmarksComponent {
  private readonly apiService = inject(ApiService);
  private readonly router = inject(Router);

  protected readonly bookmarks = signal<BookmarkResponse[]>([]);
  protected readonly loading = signal(true);

  constructor() {
    this.loadBookmarks();
  }

  private loadBookmarks() {
    this.apiService.getMyBookmarks().subscribe({
      next: (bookmarks) => {
        this.bookmarks.set(bookmarks);
        this.loading.set(false);
      },
      error: (error) => {
        console.error('Failed to load bookmarks', error);
        this.loading.set(false);
      }
    });
  }

  viewPost(postId: number) {
    this.router.navigate(['/post', postId]);
  }

  getTimeAgo(date: string | Date): string {
    const now = new Date();
    const bookmarkedDate = new Date(date);
    const diffMs = now.getTime() - bookmarkedDate.getTime();
    const diffDays = Math.floor(diffMs / 86400000);

    if (diffDays === 0) return 'today';
    if (diffDays === 1) return 'yesterday';
    if (diffDays < 7) return `${diffDays} days ago`;
    return bookmarkedDate.toLocaleDateString();
  }
}
```

### Step 14: Add Route

**File**: `frontend/src/app/app.routes.ts`

```typescript
import { BookmarksComponent } from './features/bookmarks/bookmarks.component';

export const routes: Routes = [
  // ... existing routes
  {
    path: 'bookmarks',
    component: BookmarksComponent,
    canActivate: [AuthGuard]
  },
];
```

### Step 15: Add Link to Navbar

**File**: `frontend/src/app/shared/components/navbar/navbar.component.html`

```html
<nav>
  <a routerLink="/home">Home</a>
  <a routerLink="/my-blog">My Blog</a>
  <a routerLink="/bookmarks">Bookmarks</a>  <!-- NEW -->
  <a routerLink="/profile">Profile</a>
</nav>
```

---

## Testing Your New Feature

### Backend Tests

**BookmarkServiceTest.java**:
```java
@ExtendWith(MockitoExtension.class)
class BookmarkServiceTest {

    @Mock
    private BookmarkRepository bookmarkRepository;

    @Mock
    private PostRepository postRepository;

    @InjectMocks
    private BookmarkServiceImpl bookmarkService;

    @Test
    void bookmarkPost_Success() {
        // Arrange
        User user = new User();
        user.setId(1L);

        Post post = new Post();
        post.setId(42L);

        when(bookmarkRepository.existsByUserAndPost_Id(user, 42L)).thenReturn(false);
        when(postRepository.findById(42L)).thenReturn(Optional.of(post));

        // Act
        bookmarkService.bookmarkPost(42L, user);

        // Assert
        verify(bookmarkRepository, times(1)).save(any(Bookmark.class));
    }

    @Test
    void bookmarkPost_AlreadyBookmarked_ThrowsException() {
        // Arrange
        User user = new User();
        when(bookmarkRepository.existsByUserAndPost_Id(user, 42L)).thenReturn(true);

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            bookmarkService.bookmarkPost(42L, user);
        });
    }
}
```

### Frontend Tests

**bookmarks.component.spec.ts**:
```typescript
describe('BookmarksComponent', () => {
  let component: BookmarksComponent;
  let apiService: jasmine.SpyObj<ApiService>;

  beforeEach(() => {
    const apiServiceSpy = jasmine.createSpyObj('ApiService', ['getMyBookmarks']);

    TestBed.configureTestingModule({
      imports: [BookmarksComponent],
      providers: [
        { provide: ApiService, useValue: apiServiceSpy }
      ]
    });

    apiService = TestBed.inject(ApiService) as jasmine.SpyObj<ApiService>;
    component = TestBed.createComponent(BookmarksComponent).componentInstance;
  });

  it('should load bookmarks on init', () => {
    const mockBookmarks: BookmarkResponse[] = [
      { id: 1, postId: 42, postTitle: 'Test', postAuthor: 'John', bookmarkedAt: new Date() }
    ];

    apiService.getMyBookmarks.and.returnValue(of(mockBookmarks));

    component.ngOnInit();

    expect(component.bookmarks()).toEqual(mockBookmarks);
    expect(component.loading()).toBe(false);
  });
});
```

---

## Feature Checklist

When adding any new feature, follow this checklist:

### Backend:
- [ ] Design database schema
- [ ] Create entity class
- [ ] Update related entities (if needed)
- [ ] Create repository interface
- [ ] Write custom queries (if needed)
- [ ] Create DTOs (Request and Response)
- [ ] Create service interface
- [ ] Implement service
- [ ] Create controller
- [ ] Add validation
- [ ] Add error handling
- [ ] Write unit tests
- [ ] Write integration tests
- [ ] Test with Postman/curl

### Frontend:
- [ ] Update models/interfaces
- [ ] Add API service methods
- [ ] Create or update component
- [ ] Add template/HTML
- [ ] Add styles
- [ ] Update routes (if new page)
- [ ] Add navigation links
- [ ] Handle loading states
- [ ] Handle errors
- [ ] Test in browser
- [ ] Write component tests

---

## Common Patterns

### 1. One-to-Many Relationship

**Example**: User has many addresses

```java
// Entity
@Entity
class Address {
    @ManyToOne
    private User user;
}

// Repository
interface AddressRepository extends JpaRepository<Address, Long> {
    List<Address> findByUser(User user);
}

// Service
@Transactional
public void addAddress(User user, AddressRequest request) {
    Address address = new Address();
    address.setUser(user);
    address.setStreet(request.getStreet());
    addressRepository.save(address);
}

// Controller
@PostMapping("/addresses")
public ResponseEntity<String> addAddress(@RequestBody AddressRequest request, Authentication auth) {
    User user = userService.getUserByUsername(auth.getName());
    addressService.addAddress(user, request);
    return ResponseEntity.ok("Address added");
}
```

### 2. Many-to-Many Relationship

**Example**: Students enroll in courses

```java
// Entity
@Entity
class Enrollment {
    @ManyToOne
    private Student student;

    @ManyToOne
    private Course course;

    private Date enrolledAt;
    private String grade;
}

// Service
@Transactional
public void enrollStudent(Long studentId, Long courseId) {
    Student student = studentRepository.findById(studentId).orElseThrow();
    Course course = courseRepository.findById(courseId).orElseThrow();

    Enrollment enrollment = new Enrollment();
    enrollment.setStudent(student);
    enrollment.setCourse(course);
    enrollmentRepository.save(enrollment);
}
```

### 3. Soft Delete Pattern

**Example**: Mark as deleted instead of actually deleting

```java
// Entity
@Entity
class Post {
    private boolean deleted = false;
    private Date deletedAt;
}

// Repository
@Query("SELECT p FROM Post p WHERE p.deleted = false")
List<Post> findAllActive();

// Service
@Transactional
public void softDeletePost(Long postId) {
    Post post = postRepository.findById(postId).orElseThrow();
    post.setDeleted(true);
    post.setDeletedAt(new Date());
    postRepository.save(post);
}
```

---

## Quick Reference

### HTTP Status Codes

```
200 OK              - Success (GET, PUT)
201 Created         - Resource created (POST)
204 No Content      - Success, no body (DELETE)
400 Bad Request     - Invalid input
401 Unauthorized    - Not authenticated
403 Forbidden       - Not authorized
404 Not Found       - Resource doesn't exist
409 Conflict        - Duplicate resource
500 Server Error    - Internal error
```

### REST API Naming Conventions

```
GET    /posts              - Get all posts
GET    /posts/{id}         - Get one post
POST   /posts              - Create post
PUT    /posts/{id}         - Update post
DELETE /posts/{id}         - Delete post
GET    /posts/{id}/comments - Get post's comments
POST   /posts/{id}/like    - Like a post
```

### Angular Component Lifecycle

```typescript
constructor()       // 1. Create component
ngOnInit()         // 2. Initialize (fetch data here)
ngOnChanges()      // When inputs change
ngDoCheck()        // Custom change detection
ngAfterViewInit()  // After view rendered
ngOnDestroy()      // Cleanup (unsubscribe)
```

---

**Now you know how to add complete features from backend to frontend!** 🎉
