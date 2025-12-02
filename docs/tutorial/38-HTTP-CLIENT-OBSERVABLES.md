# HTTP Client and RxJS Observables

## Overview

This document explains **HTTP communication** and **RxJS Observables** using your Dispatch application's `ApiService` and `AuthService`.

**What you'll learn**:
- HttpClient and HTTP methods
- RxJS Observables fundamentals
- Common operators (`tap`, `map`, `catchError`, `pipe`)
- Your ApiService architecture
- HTTP interceptors
- Error handling
- Authentication with cookies

**Your setup**: HttpClient with cookie-based authentication, auth interceptor

---

## 1. What are Observables?

### Observable Basics

**Observable** = Stream of data over time (like a promise, but can emit multiple values)

```typescript
// Promise (one value)
const promise = fetch('/api/users');
promise.then(response => console.log(response));  // Fires once

// Observable (multiple values possible)
const observable = this.http.get('/api/users');
observable.subscribe(response => console.log(response));  // Can fire multiple times
```

**Key differences**:

| Feature | Promise | Observable |
|---------|---------|------------|
| Values | Single value | Multiple values |
| Cancelable | ❌ No | ✅ Yes (unsubscribe) |
| Lazy | ❌ Executes immediately | ✅ Executes on subscribe |
| Operators | ❌ Limited (then, catch) | ✅ 100+ operators |

### Observable Lifecycle

```typescript
const observable = this.http.get<User[]>('/api/users');

// 1. Created (but NOT executed yet!)
console.log('Observable created');

// 2. Subscribed (NOW it executes)
const subscription = observable.subscribe({
  next: (users) => {
    console.log('Data received:', users);  // Success
  },
  error: (err) => {
    console.error('Error occurred:', err);  // Error
  },
  complete: () => {
    console.log('Observable completed');  // Done
  }
});

// 3. Unsubscribe (cleanup)
subscription.unsubscribe();
```

---

## 2. HttpClient Basics

### Your ApiService Structure

```typescript
@Injectable({
  providedIn: 'root'
})
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.apiUrl;  // http://localhost:8080

  // GET request
  getAllPosts(): Observable<PostResponse[]> {
    return this.http.get<PostResponse[]>(`${this.baseUrl}/posts/all`);
  }

  // GET with path parameter
  getPostById(id: number): Observable<PostResponse> {
    return this.http.get<PostResponse>(`${this.baseUrl}/posts/post/${id}`);
  }

  // POST request
  createPost(request: PostRequest): Observable<string> {
    return this.http.post(`${this.baseUrl}/posts/create`, request, {
      responseType: 'text'  // Response is plain text, not JSON
    });
  }

  // PUT request
  updatePost(id: number, request: PostRequest): Observable<string> {
    return this.http.put(`${this.baseUrl}/posts/${id}`, request, {
      responseType: 'text'
    });
  }

  // DELETE request
  deletePost(id: number) {
    return this.http.delete<{ message: string }>(`${this.baseUrl}/posts/${id}`);
  }
}
```

### HTTP Methods Explained

```typescript
// GET - Retrieve data
this.http.get<PostResponse[]>(url)
// Headers: GET /posts/all HTTP/1.1
// Body: (empty)
// Returns: Observable<PostResponse[]>

// POST - Create data
this.http.post<PostResponse>(url, body)
// Headers: POST /posts/create HTTP/1.1
//          Content-Type: application/json
// Body: {"title": "My Post", "content": "..."}
// Returns: Observable<PostResponse>

// PUT - Update data (full replace)
this.http.put<PostResponse>(url, body)
// Headers: PUT /posts/5 HTTP/1.1
// Body: {"title": "Updated", "content": "..."}

// PATCH - Partial update
this.http.patch<PostResponse>(url, partialBody)
// Body: {"title": "Only update title"}

// DELETE - Delete data
this.http.delete(url)
// Headers: DELETE /posts/5 HTTP/1.1
// Body: (empty)
```

---

## 3. Using HttpClient in Components

### Basic Usage

```typescript
// Your PostDetailComponent
export class PostDetailComponent {
  private readonly apiService = inject(ApiService);
  protected readonly post = signal<PostResponse | null>(null);

  constructor() {
    const postId = Number(this.route.snapshot.paramMap.get('id'));

    // Call API
    this.apiService.getPostById(postId).subscribe(p => {
      this.post.set(p);  // Update signal with response
    });
  }
}
```

**What happens**:

```
1. this.apiService.getPostById(5)
   ↓ Returns Observable<PostResponse>

2. .subscribe(p => ...)
   ↓ Triggers HTTP request:
     GET http://localhost:8080/posts/post/5
     Cookie: jwt=eyJhbGci...

3. Backend responds:
   HTTP/1.1 200 OK
   Content-Type: application/json
   Body: {"id": 5, "title": "My Post", ...}

4. Angular parses JSON → PostResponse object

5. Callback executes: p => this.post.set(p)

6. Observable completes (HTTP requests complete after one emission)
```

### Error Handling

```typescript
// Your component with error handling
export class PostDetailComponent {
  protected readonly post = signal<PostResponse | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  loadPost(id: number) {
    this.loading.set(true);
    this.error.set(null);

    this.apiService.getPostById(id).subscribe({
      next: (post) => {
        // Success
        this.post.set(post);
        this.loading.set(false);
      },
      error: (err) => {
        // Error
        this.error.set(err.message || 'Failed to load post');
        this.loading.set(false);
      },
      complete: () => {
        // Optional: Called after next() or error()
        console.log('Request completed');
      }
    });
  }
}
```

---

## 4. RxJS Operators

### pipe() and Operators

**Operators** = Transform Observable data

```typescript
observable
  .pipe(                    // pipe() chains operators
    operator1(),
    operator2(),
    operator3()
  )
  .subscribe(result => ...);
```

### tap() - Side Effects

**tap()** = Do something without changing the data (like console.log)

```typescript
// Your AuthService
login(request: LoginRequest): Observable<AuthResponse> {
  return this.http.post<AuthResponse>(`${this.baseUrl}/login`, request, {
    withCredentials: true
  }).pipe(
    tap(response => {
      // Side effect: Update state
      this.isLoggedIn.set(true);
      // Data passes through unchanged
    })
  );
}
```

**Without tap** (wrong):
```typescript
login(request: LoginRequest): Observable<AuthResponse> {
  return this.http.post<AuthResponse>(...).subscribe(response => {
    this.isLoggedIn.set(true);  // ❌ Wrong! subscribe() returns Subscription, not Observable
  });
}
```

### map() - Transform Data

**map()** = Transform each emitted value

```typescript
// Transform response
getUsernames(): Observable<string[]> {
  return this.http.get<UserResponse[]>('/api/users').pipe(
    map(users => users.map(u => u.username))  // Extract usernames
  );
  // Returns: Observable<string[]> instead of Observable<UserResponse[]>
}

// Example flow:
// Backend sends: [
//   {id: 1, username: "john", email: "john@example.com"},
//   {id: 2, username: "jane", email: "jane@example.com"}
// ]
// map() transforms to: ["john", "jane"]
```

### catchError() - Handle Errors

**catchError()** = Handle errors and return fallback Observable

```typescript
import { catchError, of, throwError } from 'rxjs';

getPost(id: number): Observable<PostResponse> {
  return this.http.get<PostResponse>(`/api/posts/${id}`).pipe(
    catchError(error => {
      console.error('Error loading post:', error);

      // Option 1: Return fallback value
      return of(null);  // of() creates Observable that emits null

      // Option 2: Re-throw error
      return throwError(() => new Error('Failed to load post'));
    })
  );
}
```

### switchMap() - Chain Requests

**switchMap()** = Cancel previous request when new one starts (useful for search)

```typescript
// Your AuthService pattern
login(request: LoginRequest): Observable<AuthResponse> {
  return this.http.post<AuthResponse>(`${this.baseUrl}/login`, request).pipe(
    tap(() => this.isLoggedIn.set(true)),
    // After login, immediately fetch user details
    switchMap(() => this.checkAuth())  // Returns Observable<UserResponse>
  );
  // Final return type: Observable<UserResponse>
}
```

**Real-world example** (search autocomplete):
```typescript
searchPosts(searchTerm: string): void {
  this.searchInput$.pipe(
    debounceTime(300),        // Wait 300ms after typing stops
    distinctUntilChanged(),   // Only if search term changed
    switchMap(term =>         // Cancel previous search, start new one
      this.apiService.searchPosts(term)
    )
  ).subscribe(results => {
    this.searchResults.set(results);
  });
}
```

### filter() - Skip Unwanted Values

```typescript
// Only emit when user is logged in
this.authService.currentUser$.pipe(
  filter(user => user !== null),  // Skip null values
  map(user => user.username)
).subscribe(username => {
  console.log('Username:', username);  // Only called when user exists
});
```

---

## 5. Your AuthService with Observables

### Complete Login Flow

```typescript
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  readonly isLoggedIn = signal(false);
  readonly currentUser = signal<UserResponse | null>(null);
  private readonly _authInitialized = new BehaviorSubject<boolean>(false);
  public readonly authInitialized$ = this._authInitialized.asObservable();

  login(request: LoginRequest): Observable<AuthResponse> {
    // 1. POST login request
    return this.http.post<AuthResponse>(`${this.baseUrl}/login`, request, {
      withCredentials: true  // ✅ Send cookies
    }).pipe(
      tap(response => {
        // 2. On success, update state
        this.isLoggedIn.set(true);

        // 3. Fetch full user details
        this.checkAuth().subscribe({
          next: () => this._authInitialized.next(true),
          error: () => this._authInitialized.next(true)
        });
      })
    );
  }

  checkAuth(): Observable<UserResponse> {
    // GET /users/me (backend validates JWT cookie)
    return this.http.get<UserResponse>(`${environment.apiUrl}/users/me`, {
      withCredentials: true  // ✅ Automatically sends cookie
    }).pipe(
      tap(user => {
        this.isLoggedIn.set(true);
        this.currentUser.set(user);
        this._authInitialized.next(true);
      }),
      catchError(err => {
        // Cookie invalid/expired
        this.logout();
        this._authInitialized.next(true);
        return throwError(() => err);
      })
    );
  }
}
```

**Usage in component**:
```typescript
// LoginComponent
export class LoginComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  onSubmit() {
    const request: LoginRequest = {
      usernameOrEmail: this.loginForm.value.username,
      password: this.loginForm.value.password
    };

    this.authService.login(request).subscribe({
      next: (response) => {
        // Login successful
        this.router.navigate(['/home']);
      },
      error: (err) => {
        // Login failed
        this.errorMessage = 'Invalid username or password';
      }
    });
  }
}
```

---

## 6. Cookie-Based Authentication

### withCredentials: true

```typescript
// Your AuthService uses cookie-based auth
this.http.post<AuthResponse>(`${this.baseUrl}/login`, request, {
  withCredentials: true  // ✅ CRITICAL for cookie auth
});
```

**What `withCredentials: true` does**:

```
Without withCredentials (default):
Client → Server:
  POST /auth/login
  Body: {"username": "john", "password": "..."}

Server → Client:
  Set-Cookie: jwt=eyJhbGci...; HttpOnly; Secure
  ❌ Cookie IGNORED by browser (CORS policy)

With withCredentials: true:
Client → Server:
  POST /auth/login
  Body: {"username": "john", "password": "..."}

Server → Client:
  Set-Cookie: jwt=eyJhbGci...; HttpOnly; Secure
  ✅ Cookie STORED by browser

Subsequent requests:
Client → Server:
  GET /posts/all
  Cookie: jwt=eyJhbGci...  ✅ Automatically sent!
```

### Why Cookie Auth?

**Alternative 1: localStorage (insecure)**:
```typescript
// ❌ INSECURE! XSS can steal token
localStorage.setItem('token', response.token);

// Later
const token = localStorage.getItem('token');
this.http.get('/api/posts', {
  headers: { Authorization: `Bearer ${token}` }
});
```

**Your app (HttpOnly cookies)**:
```typescript
// ✅ SECURE! JavaScript can't access cookie
this.http.post('/auth/login', request, {
  withCredentials: true
});
// Cookie stored by browser, automatically sent with every request
```

**Benefits**:
- ✅ Immune to XSS (JavaScript can't read HttpOnly cookies)
- ✅ Automatic - browser handles cookie sending
- ✅ CSRF protection (backend validates origin)

---

## 7. HTTP Interceptors

### What is an Interceptor?

**Interceptor** = Middleware that intercepts HTTP requests/responses

```
Request Flow with Interceptor:

Component → HttpClient
              ↓
         [Interceptor]  ← Modify request (add headers, log, etc.)
              ↓
          Backend
              ↓
         [Interceptor]  ← Modify response (log, handle errors)
              ↓
         Component
```

### Your Auth Interceptor

```typescript
// auth.interceptor.ts
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  // 1. Clone request (requests are immutable)
  const clonedReq = req.clone({
    withCredentials: true  // ✅ Automatically add withCredentials to ALL requests
  });

  // 2. Pass to next handler
  return next(clonedReq);
};
```

**Without interceptor** (repetitive):
```typescript
// Every API call needs withCredentials
this.http.get('/api/posts', { withCredentials: true });
this.http.post('/api/posts', body, { withCredentials: true });
this.http.put('/api/posts/5', body, { withCredentials: true });
// ... repeated 50+ times
```

**With interceptor** (DRY):
```typescript
// Interceptor adds withCredentials automatically
this.http.get('/api/posts');  // withCredentials: true added automatically!
this.http.post('/api/posts', body);  // withCredentials: true added!
```

### Common Interceptor Use Cases

**1. Add Auth Headers**:
```typescript
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const token = localStorage.getItem('token');

  if (token) {
    const cloned = req.clone({
      headers: req.headers.set('Authorization', `Bearer ${token}`)
    });
    return next(cloned);
  }

  return next(req);
};
```

**2. Global Error Handling**:
```typescript
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401) {
        // Unauthorized - redirect to login
        inject(Router).navigate(['/login']);
      } else if (error.status === 500) {
        // Server error - show notification
        inject(NotificationService).error('Server error occurred');
      }

      return throwError(() => error);
    })
  );
};
```

**3. Logging**:
```typescript
export const loggingInterceptor: HttpInterceptorFn = (req, next) => {
  const started = Date.now();
  console.log(`→ ${req.method} ${req.url}`);

  return next(req).pipe(
    tap({
      next: () => {
        const elapsed = Date.now() - started;
        console.log(`← ${req.method} ${req.url} (${elapsed}ms)`);
      },
      error: (err) => {
        console.error(`✗ ${req.method} ${req.url}`, err);
      }
    })
  );
};
```

---

## 8. Common Patterns in Your Application

### Pattern 1: Loading State

```typescript
export class PostDetailComponent {
  protected readonly loading = signal(false);
  protected readonly post = signal<PostResponse | null>(null);

  loadPost(id: number) {
    this.loading.set(true);  // Start loading

    this.apiService.getPostById(id).subscribe({
      next: (post) => {
        this.post.set(post);
        this.loading.set(false);  // Stop loading
      },
      error: () => {
        this.loading.set(false);  // Stop loading even on error
      }
    });
  }
}
```

**Template**:
```html
@if (loading()) {
  <div class="spinner">Loading...</div>
} @else {
  <div class="post">{{ post()?.title }}</div>
}
```

### Pattern 2: Optimistic Update

```typescript
// Your NavbarComponent
toggleLike() {
  const post = this.post();

  this.togglingLike.set(true);
  const action = this.isLiked()
    ? this.apiService.unlikePost(post.id)
    : this.apiService.likePost(post.id);

  action.subscribe({
    next: () => {
      // Update UI immediately (optimistic)
      this.isLiked.update(v => !v);

      this.post.update(p => {
        if (p) {
          return {
            ...p,
            likeCount: this.isLiked() ? p.likeCount + 1 : p.likeCount - 1
          };
        }
        return p;
      });

      this.togglingLike.set(false);
    },
    error: () => {
      // Revert on error
      this.togglingLike.set(false);
      this.notificationService.error('Failed to update like');
    }
  });
}
```

### Pattern 3: Polling

```typescript
// Your NavbarComponent polls for notifications
ngOnInit() {
  if (this.authService.isLoggedIn()) {
    this.loadUnreadCount();

    // Poll every 30 seconds
    this.pollSubscription = interval(30000).subscribe(() => {
      this.loadUnreadCount();
    });
  }
}

ngOnDestroy() {
  // Clean up!
  this.pollSubscription?.unsubscribe();
}

private loadUnreadCount() {
  this.apiService.getUnreadNotificationsCount().subscribe({
    next: (count) => this.unreadCount.set(count),
    error: () => {
      // Silently fail - not critical
    }
  });
}
```

---

## 9. Unsubscribing (Memory Leak Prevention)

### The Problem

```typescript
// ❌ Memory leak!
export class MyComponent {
  ngOnInit() {
    this.apiService.getData().subscribe(data => {
      // Subscription never cleaned up!
      // Component destroyed? Subscription still active!
    });
  }
}
```

### Solution 1: Unsubscribe Manually

```typescript
export class MyComponent implements OnDestroy {
  private subscription?: Subscription;

  ngOnInit() {
    this.subscription = this.apiService.getData().subscribe(data => {
      // ...
    });
  }

  ngOnDestroy() {
    this.subscription?.unsubscribe();  // ✅ Cleanup
  }
}
```

### Solution 2: takeUntilDestroyed (Modern)

```typescript
// Your AppComponent uses this!
export class AppComponent {
  constructor() {
    this.authService.authInitialized$
      .pipe(takeUntilDestroyed())  // ✅ Auto-cleanup when component destroyed
      .subscribe(initialized => {
        this.authInitialized.set(initialized);
      });
  }
}
```

### Solution 3: async Pipe (Template)

```html
<!-- Template automatically subscribes AND unsubscribes -->
<div>{{ posts$ | async }}</div>
```

```typescript
export class MyComponent {
  // Don't subscribe in component
  posts$ = this.apiService.getAllPosts();  // Observable<PostResponse[]>
}
```

---

## 10. Key Takeaways

### HTTP Basics

- ✅ Use `HttpClient` for all HTTP requests
- ✅ Methods: `get()`, `post()`, `put()`, `delete()`
- ✅ Returns `Observable` (lazy, cancelable)
- ✅ Must `subscribe()` to execute request

### Observables

- ✅ Stream of data over time
- ✅ Lazy (only executes on subscribe)
- ✅ Operators transform data (`map`, `tap`, `catchError`)
- ✅ Must unsubscribe to prevent memory leaks

### Your Application

- ✅ Cookie-based authentication (`withCredentials: true`)
- ✅ Auth interceptor adds credentials automatically
- ✅ ApiService centralizes all HTTP calls
- ✅ Signals for reactive state updates
- ✅ takeUntilDestroyed() for automatic cleanup

### Best Practices

- ✅ Always handle errors (`.subscribe({ next, error })`)
- ✅ Show loading states
- ✅ Unsubscribe or use `takeUntilDestroyed()`
- ✅ Use interceptors for cross-cutting concerns
- ✅ Use `withCredentials: true` for cookie auth
- ❌ Don't subscribe in services (return Observable)
- ❌ Don't forget error handling
- ❌ Don't use localStorage for tokens (XSS risk)

---

## What's Next?

Continue to `39-STATE-MANAGEMENT.md` for managing application state and component communication.

**Completed**:
- ✅ TypeScript essentials
- ✅ Angular fundamentals
- ✅ HTTP Client & Observables

**Next**:
- State management patterns
- RxJS advanced patterns
- Component communication
- Performance optimization
