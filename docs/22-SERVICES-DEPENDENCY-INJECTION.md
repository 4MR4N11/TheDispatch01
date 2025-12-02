# Services and Dependency Injection

## Table of Contents
1. [Dependency Injection Overview](#dependency-injection-overview)
2. [Creating Services](#creating-services)
3. [Providing Services](#providing-services)
4. [Hierarchical Injectors](#hierarchical-injectors)
5. [Injection Tokens](#injection-tokens)
6. [Advanced DI Patterns](#advanced-di-patterns)
7. [Service Best Practices](#service-best-practices)
8. [TheDispatch Services Architecture](#thedispatch-services-architecture)

---

## Dependency Injection Overview

Dependency Injection (DI) is a design pattern where a class receives its dependencies from external sources rather than creating them itself.

### Benefits of DI

1. **Testability**: Easy to inject mock dependencies for testing
2. **Maintainability**: Dependencies are centrally managed
3. **Flexibility**: Easy to swap implementations
4. **Reusability**: Services can be shared across components

### Without DI (Bad)

```typescript
export class PostListComponent {
  private http: HttpClient;

  constructor() {
    // Component creates its own dependencies - hard to test!
    this.http = new HttpClient(new HttpHandler());
  }

  loadPosts(): void {
    this.http.get('/api/posts').subscribe(/*...*/);
  }
}
```

### With DI (Good)

```typescript
export class PostListComponent {
  constructor(private http: HttpClient) {
    // Dependencies injected by Angular - easy to test!
  }

  loadPosts(): void {
    this.http.get('/api/posts').subscribe(/*...*/);
  }
}
```

---

## Creating Services

Services are classes that encapsulate business logic, data access, or other reusable functionality.

### Basic Service

```typescript
// post.service.ts
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root'  // Service provided at root level
})
export class PostService {
  private apiUrl = 'http://localhost:8080/api/posts';

  constructor(private http: HttpClient) {}

  getAllPosts(): Observable<Post[]> {
    return this.http.get<Post[]>(this.apiUrl);
  }

  getPost(id: number): Observable<Post> {
    return this.http.get<Post>(`${this.apiUrl}/${id}`);
  }

  createPost(post: Post): Observable<Post> {
    return this.http.post<Post>(this.apiUrl, post);
  }

  updatePost(id: number, post: Post): Observable<Post> {
    return this.http.put<Post>(`${this.apiUrl}/${id}`, post);
  }

  deletePost(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}
```

### Service with State

```typescript
// user.service.ts
@Injectable({
  providedIn: 'root'
})
export class UserService {
  private currentUserSubject = new BehaviorSubject<User | null>(null);
  currentUser$ = this.currentUserSubject.asObservable();

  private apiUrl = 'http://localhost:8080/api/users';

  constructor(private http: HttpClient) {
    this.loadUserFromStorage();
  }

  login(credentials: Credentials): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.apiUrl}/login`, credentials)
      .pipe(
        tap(response => {
          this.setCurrentUser(response.user);
          this.saveTokenToStorage(response.token);
        })
      );
  }

  logout(): void {
    this.currentUserSubject.next(null);
    this.removeTokenFromStorage();
  }

  setCurrentUser(user: User): void {
    this.currentUserSubject.next(user);
    localStorage.setItem('user', JSON.stringify(user));
  }

  getCurrentUser(): User | null {
    return this.currentUserSubject.value;
  }

  isAuthenticated(): boolean {
    return this.currentUserSubject.value !== null;
  }

  private loadUserFromStorage(): void {
    const userJson = localStorage.getItem('user');
    if (userJson) {
      this.currentUserSubject.next(JSON.parse(userJson));
    }
  }

  private saveTokenToStorage(token: string): void {
    localStorage.setItem('token', token);
  }

  private removeTokenFromStorage(): void {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
  }
}
```

### Service without @Injectable

Services don't require @Injectable if they have no dependencies:

```typescript
// logger.service.ts
export class LoggerService {
  log(message: string): void {
    console.log('[LOG]:', message);
  }

  error(message: string, error?: any): void {
    console.error('[ERROR]:', message, error);
  }

  warn(message: string): void {
    console.warn('[WARN]:', message);
  }
}

// But you still need to provide it
@NgModule({
  providers: [LoggerService]
})
export class AppModule {}
```

### Generate Service with CLI

```bash
# Generate service in default location
ng generate service services/post

# Generate service with module
ng generate service services/post --module=app

# Generate service with skipTests
ng generate service services/post --skip-tests
```

---

## Providing Services

Services must be registered with Angular's injector to be available for injection.

### Root Level (Preferred)

```typescript
@Injectable({
  providedIn: 'root'  // Available app-wide as singleton
})
export class PostService {
  // Service implementation
}
```

**Benefits:**
- Tree-shakable (removed if not used)
- Singleton across entire app
- No need to add to providers array

### Module Level

```typescript
// Service
@Injectable()
export class PostService {}

// Module
@NgModule({
  providers: [PostService]  // Available to all module components
})
export class AppModule {}
```

### Component Level

```typescript
@Component({
  selector: 'app-post-list',
  templateUrl: './post-list.component.html',
  providers: [PostService]  // New instance for this component and children
})
export class PostListComponent {
  constructor(private postService: PostService) {}
}
```

**Use Case:** When you need a separate instance per component.

### providedIn with Module

```typescript
// Feature module
@NgModule({})
export class AdminModule {}

// Service only available when AdminModule is imported
@Injectable({
  providedIn: AdminModule
})
export class AdminService {}
```

### Multiple Providers

```typescript
@NgModule({
  providers: [
    PostService,
    UserService,
    CommentService,
    AuthService
  ]
})
export class AppModule {}
```

---

## Hierarchical Injectors

Angular has a hierarchical injection system with multiple injector levels.

### Injector Hierarchy

```
Root Injector (providedIn: 'root')
    ↓
Module Injector (module providers)
    ↓
Component Injector (component providers)
    ↓
Child Component Injector
```

### Example

```typescript
// Service provided at root
@Injectable({ providedIn: 'root' })
export class GlobalService {
  instanceId = Math.random();
}

// Service provided at component
@Injectable()
export class LocalService {
  instanceId = Math.random();
}

// Parent component
@Component({
  selector: 'app-parent',
  template: `
    <p>Parent: {{ localService.instanceId }}</p>
    <p>Global: {{ globalService.instanceId }}</p>
    <app-child></app-child>
  `,
  providers: [LocalService]  // Creates new instance
})
export class ParentComponent {
  constructor(
    public localService: LocalService,
    public globalService: GlobalService
  ) {}
}

// Child component
@Component({
  selector: 'app-child',
  template: `
    <p>Child: {{ localService.instanceId }}</p>
    <p>Global: {{ globalService.instanceId }}</p>
  `
})
export class ChildComponent {
  constructor(
    public localService: LocalService,    // Same instance as parent
    public globalService: GlobalService   // Same instance app-wide
  ) {}
}
```

Output:
```
Parent: 0.123  (LocalService)
Global: 0.456  (GlobalService)
Child: 0.123   (Same LocalService instance)
Global: 0.456  (Same GlobalService instance)
```

### Resolution Modifiers

```typescript
export class MyComponent {
  constructor(
    // Default - looks up the injector tree
    private service: MyService,

    // @Optional - returns null if not found
    @Optional() private optionalService: OptionalService,

    // @Self - only look in component's own injector
    @Self() private selfService: SelfService,

    // @SkipSelf - skip component's injector, start with parent
    @SkipSelf() private parentService: ParentService,

    // @Host - stop at host component (useful with content projection)
    @Host() private hostService: HostService
  ) {}
}
```

---

## Injection Tokens

Injection tokens allow you to inject values that aren't classes.

### Using InjectionToken

```typescript
// app-config.ts
import { InjectionToken } from '@angular/core';

export interface AppConfig {
  apiUrl: string;
  appName: string;
  version: string;
}

export const APP_CONFIG = new InjectionToken<AppConfig>('app.config');

// app.module.ts
@NgModule({
  providers: [
    {
      provide: APP_CONFIG,
      useValue: {
        apiUrl: 'http://localhost:8080/api',
        appName: 'TheDispatch',
        version: '1.0.0'
      }
    }
  ]
})
export class AppModule {}

// Using in component/service
export class ApiService {
  constructor(@Inject(APP_CONFIG) private config: AppConfig) {
    console.log('API URL:', this.config.apiUrl);
  }

  makeRequest(): void {
    this.http.get(`${this.config.apiUrl}/posts`);
  }
}
```

### Built-in Injection Tokens

```typescript
export class MyComponent {
  constructor(
    // Document object
    @Inject(DOCUMENT) private document: Document,

    // Window object (if provided)
    @Inject('WINDOW') private window: Window,

    // Platform ID
    @Inject(PLATFORM_ID) private platformId: Object
  ) {
    if (isPlatformBrowser(this.platformId)) {
      console.log('Running in browser');
    }
  }
}
```

### String Tokens (Deprecated)

```typescript
// Old way - avoid
@NgModule({
  providers: [
    { provide: 'API_URL', useValue: 'http://localhost:8080/api' }
  ]
})

// New way - use InjectionToken
export const API_URL = new InjectionToken<string>('api.url');
```

---

## Advanced DI Patterns

### Class Providers

```typescript
// Abstract class
export abstract class Logger {
  abstract log(message: string): void;
  abstract error(message: string, error?: any): void;
}

// Implementation
export class ConsoleLogger extends Logger {
  log(message: string): void {
    console.log(message);
  }

  error(message: string, error?: any): void {
    console.error(message, error);
  }
}

// Provide implementation
@NgModule({
  providers: [
    { provide: Logger, useClass: ConsoleLogger }
  ]
})
export class AppModule {}

// Use abstract class in component
export class MyComponent {
  constructor(private logger: Logger) {
    this.logger.log('Component initialized');
  }
}
```

### Factory Providers

```typescript
// Factory function
export function loggerFactory(config: AppConfig): Logger {
  if (config.environment === 'production') {
    return new RemoteLogger(config.logUrl);
  } else {
    return new ConsoleLogger();
  }
}

// Provider
@NgModule({
  providers: [
    {
      provide: Logger,
      useFactory: loggerFactory,
      deps: [APP_CONFIG]  // Dependencies for factory
    }
  ]
})
export class AppModule {}
```

### Value Providers

```typescript
// Simple value
@NgModule({
  providers: [
    { provide: APP_CONFIG, useValue: { apiUrl: '...' } }
  ]
})

// Function value
export function getCurrentDate(): Date {
  return new Date();
}

@NgModule({
  providers: [
    { provide: 'CURRENT_DATE', useValue: getCurrentDate() }
  ]
})
```

### Existing Provider (Alias)

```typescript
// Create alias for existing service
@NgModule({
  providers: [
    OldApiService,
    { provide: NewApiService, useExisting: OldApiService }
  ]
})
export class AppModule {}

// Both inject the same instance
export class MyComponent {
  constructor(
    private oldApi: OldApiService,  // Original
    private newApi: NewApiService   // Alias - same instance
  ) {
    console.log(oldApi === newApi);  // true
  }
}
```

### Multi Providers

```typescript
// HTTP Interceptors use multi providers
export const HTTP_INTERCEPTORS = new InjectionToken<HttpInterceptor[]>('HTTP_INTERCEPTORS');

@NgModule({
  providers: [
    { provide: HTTP_INTERCEPTORS, useClass: AuthInterceptor, multi: true },
    { provide: HTTP_INTERCEPTORS, useClass: LoggingInterceptor, multi: true },
    { provide: HTTP_INTERCEPTORS, useClass: ErrorInterceptor, multi: true }
  ]
})
export class AppModule {}

// All interceptors injected as array
export class HttpClient {
  constructor(@Inject(HTTP_INTERCEPTORS) private interceptors: HttpInterceptor[]) {
    console.log('Interceptors:', interceptors.length);  // 3
  }
}
```

### forRoot and forChild Pattern

```typescript
// Shared module with services
@NgModule({
  declarations: [SharedComponent],
  exports: [SharedComponent]
})
export class SharedModule {
  // forRoot - import once in AppModule
  static forRoot(): ModuleWithProviders<SharedModule> {
    return {
      ngModule: SharedModule,
      providers: [SharedService]  // Singleton services
    };
  }

  // forChild - import in feature modules
  static forChild(): ModuleWithProviders<SharedModule> {
    return {
      ngModule: SharedModule,
      providers: []  // No services
    };
  }
}

// app.module.ts
@NgModule({
  imports: [
    SharedModule.forRoot()  // Provides services
  ]
})
export class AppModule {}

// feature.module.ts
@NgModule({
  imports: [
    SharedModule.forChild()  // Only imports components, no services
  ]
})
export class FeatureModule {}
```

---

## Service Best Practices

### 1. Single Responsibility

```typescript
// Bad - service doing too much
export class DataService {
  getPosts() {}
  getUsers() {}
  getComments() {}
  authenticateUser() {}
  uploadFile() {}
}

// Good - separate services
export class PostService {
  getPosts() {}
  createPost() {}
  updatePost() {}
  deletePost() {}
}

export class UserService {
  getUsers() {}
  getUserById() {}
}

export class AuthService {
  login() {}
  logout() {}
  isAuthenticated() {}
}
```

### 2. Use providedIn: 'root'

```typescript
// Preferred - tree-shakable
@Injectable({
  providedIn: 'root'
})
export class MyService {}

// Only use module providers when needed
@NgModule({
  providers: [SpecialService]  // Only if really needed
})
```

### 3. Expose Observables, Not Subjects

```typescript
// Bad - exposes Subject
export class DataService {
  dataSubject = new BehaviorSubject<Data[]>([]);
}

// Components can call dataSubject.next() - breaks encapsulation!

// Good - exposes Observable
export class DataService {
  private dataSubject = new BehaviorSubject<Data[]>([]);
  data$ = this.dataSubject.asObservable();  // Read-only

  updateData(data: Data[]): void {
    this.dataSubject.next(data);  // Controlled mutation
  }
}
```

### 4. Handle Errors in Services

```typescript
export class PostService {
  constructor(private http: HttpClient) {}

  getPosts(): Observable<Post[]> {
    return this.http.get<Post[]>('/api/posts')
      .pipe(
        retry(3),  // Retry failed requests
        catchError(error => {
          console.error('Failed to load posts:', error);
          return throwError(() => new Error('Failed to load posts'));
        })
      );
  }
}
```

### 5. Use Interfaces for Abstraction

```typescript
// Interface
export interface DataRepository {
  getAll(): Observable<Data[]>;
  getById(id: number): Observable<Data>;
  create(data: Data): Observable<Data>;
  update(id: number, data: Data): Observable<Data>;
  delete(id: number): Observable<void>;
}

// Implementation
@Injectable({ providedIn: 'root' })
export class HttpDataRepository implements DataRepository {
  constructor(private http: HttpClient) {}

  getAll(): Observable<Data[]> {
    return this.http.get<Data[]>('/api/data');
  }

  // ... other methods
}

// Easy to swap implementations
@NgModule({
  providers: [
    { provide: DataRepository, useClass: HttpDataRepository }
    // or: { provide: DataRepository, useClass: MockDataRepository }
  ]
})
```

### 6. Keep Services Stateless When Possible

```typescript
// Good - stateless
export class CalculatorService {
  add(a: number, b: number): number {
    return a + b;
  }

  multiply(a: number, b: number): number {
    return a * b;
  }
}

// Acceptable - stateful when needed
export class CartService {
  private items: CartItem[] = [];  // State

  addItem(item: CartItem): void {
    this.items.push(item);
  }

  getTotal(): number {
    return this.items.reduce((sum, item) => sum + item.price, 0);
  }
}
```

### 7. Use Dependency Injection for Testing

```typescript
// Service
export class UserService {
  constructor(private http: HttpClient) {}

  getUsers(): Observable<User[]> {
    return this.http.get<User[]>('/api/users');
  }
}

// Test
describe('UserService', () => {
  let service: UserService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [UserService]
    });

    service = TestBed.inject(UserService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  it('should get users', () => {
    const mockUsers: User[] = [
      { id: 1, name: 'John' },
      { id: 2, name: 'Jane' }
    ];

    service.getUsers().subscribe(users => {
      expect(users).toEqual(mockUsers);
    });

    const req = httpMock.expectOne('/api/users');
    expect(req.request.method).toBe('GET');
    req.flush(mockUsers);
  });
});
```

---

## TheDispatch Services Architecture

Complete service architecture for the blog application.

### 1. API Service (Base)

```typescript
// api.service.ts
@Injectable({ providedIn: 'root' })
export class ApiService {
  private baseUrl = 'http://localhost:8080/api';

  constructor(private http: HttpClient) {}

  get<T>(endpoint: string, options?: any): Observable<T> {
    return this.http.get<T>(`${this.baseUrl}${endpoint}`, options)
      .pipe(
        catchError(this.handleError)
      );
  }

  post<T>(endpoint: string, body: any, options?: any): Observable<T> {
    return this.http.post<T>(`${this.baseUrl}${endpoint}`, body, options)
      .pipe(
        catchError(this.handleError)
      );
  }

  put<T>(endpoint: string, body: any, options?: any): Observable<T> {
    return this.http.put<T>(`${this.baseUrl}${endpoint}`, body, options)
      .pipe(
        catchError(this.handleError)
      );
  }

  delete<T>(endpoint: string, options?: any): Observable<T> {
    return this.http.delete<T>(`${this.baseUrl}${endpoint}`, options)
      .pipe(
        catchError(this.handleError)
      );
  }

  private handleError(error: HttpErrorResponse): Observable<never> {
    let errorMessage = 'An error occurred';

    if (error.error instanceof ErrorEvent) {
      // Client-side error
      errorMessage = `Error: ${error.error.message}`;
    } else {
      // Server-side error
      errorMessage = `Error Code: ${error.status}\nMessage: ${error.message}`;
    }

    console.error(errorMessage);
    return throwError(() => new Error(errorMessage));
  }
}
```

### 2. Authentication Service

```typescript
// auth.service.ts
@Injectable({ providedIn: 'root' })
export class AuthService {
  private currentUserSubject = new BehaviorSubject<User | null>(null);
  currentUser$ = this.currentUserSubject.asObservable();

  constructor(
    private api: ApiService,
    private router: Router
  ) {
    this.loadUserFromStorage();
  }

  login(credentials: LoginRequest): Observable<AuthResponse> {
    return this.api.post<AuthResponse>('/auth/login', credentials)
      .pipe(
        tap(response => {
          this.setSession(response);
          this.currentUserSubject.next(response.user);
        })
      );
  }

  register(userData: RegisterRequest): Observable<AuthResponse> {
    return this.api.post<AuthResponse>('/auth/register', userData)
      .pipe(
        tap(response => {
          this.setSession(response);
          this.currentUserSubject.next(response.user);
        })
      );
  }

  logout(): void {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    this.currentUserSubject.next(null);
    this.router.navigate(['/login']);
  }

  isAuthenticated(): boolean {
    const token = this.getToken();
    return token !== null && !this.isTokenExpired(token);
  }

  getToken(): string | null {
    return localStorage.getItem('token');
  }

  getCurrentUser(): User | null {
    return this.currentUserSubject.value;
  }

  private setSession(authResult: AuthResponse): void {
    localStorage.setItem('token', authResult.token);
    localStorage.setItem('user', JSON.stringify(authResult.user));
  }

  private loadUserFromStorage(): void {
    const userJson = localStorage.getItem('user');
    if (userJson && this.isAuthenticated()) {
      this.currentUserSubject.next(JSON.parse(userJson));
    }
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

### 3. Post Service

```typescript
// post.service.ts
@Injectable({ providedIn: 'root' })
export class PostService {
  constructor(private api: ApiService) {}

  getAllPosts(): Observable<Post[]> {
    return this.api.get<Post[]>('/posts/all');
  }

  getPost(id: number): Observable<Post> {
    return this.api.get<Post>(`/posts/post/${id}`);
  }

  getUserPosts(userId: number): Observable<Post[]> {
    return this.api.get<Post[]>(`/posts/user/${userId}`);
  }

  createPost(post: PostDTO): Observable<Post> {
    return this.api.post<Post>('/posts/post', post);
  }

  updatePost(id: number, post: PostDTO): Observable<Post> {
    return this.api.put<Post>(`/posts/post/${id}`, post);
  }

  deletePost(id: number): Observable<void> {
    return this.api.delete<void>(`/posts/post/${id}`);
  }

  searchPosts(query: string): Observable<Post[]> {
    return this.api.get<Post[]>('/posts/search', { params: { q: query } });
  }
}
```

### 4. Comment Service

```typescript
// comment.service.ts
@Injectable({ providedIn: 'root' })
export class CommentService {
  constructor(private api: ApiService) {}

  getComments(postId: number): Observable<Comment[]> {
    return this.api.get<Comment[]>(`/comments/post/${postId}`);
  }

  createComment(comment: CommentDTO): Observable<Comment> {
    return this.api.post<Comment>('/comments/comment', comment);
  }

  updateComment(id: number, comment: CommentDTO): Observable<Comment> {
    return this.api.put<Comment>(`/comments/comment/${id}`, comment);
  }

  deleteComment(id: number): Observable<void> {
    return this.api.delete<void>(`/comments/comment/${id}`);
  }
}
```

### 5. Upload Service

```typescript
// upload.service.ts
@Injectable({ providedIn: 'root' })
export class UploadService {
  constructor(private http: HttpClient) {}

  uploadImage(file: File): Observable<HttpEvent<UploadResponse>> {
    const formData = new FormData();
    formData.append('image', file);

    return this.http.post<UploadResponse>(
      'http://localhost:8080/uploads/image',
      formData,
      {
        reportProgress: true,
        observe: 'events'
      }
    );
  }

  uploadProgress(event: HttpEvent<any>): number {
    if (event.type === HttpEventType.UploadProgress && event.total) {
      return Math.round((100 * event.loaded) / event.total);
    }
    return 0;
  }
}
```

### 6. Notification Service

```typescript
// notification.service.ts
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private notificationsSubject = new BehaviorSubject<Notification[]>([]);
  notifications$ = this.notificationsSubject.asObservable();

  show(message: string, type: 'success' | 'error' | 'info' = 'info', duration: number = 5000): void {
    const notification: Notification = {
      id: Date.now().toString(),
      message,
      type,
      timestamp: new Date()
    };

    const notifications = [...this.notificationsSubject.value, notification];
    this.notificationsSubject.next(notifications);

    if (duration > 0) {
      setTimeout(() => this.dismiss(notification.id), duration);
    }
  }

  success(message: string): void {
    this.show(message, 'success');
  }

  error(message: string): void {
    this.show(message, 'error');
  }

  info(message: string): void {
    this.show(message, 'info');
  }

  dismiss(id: string): void {
    const notifications = this.notificationsSubject.value.filter(n => n.id !== id);
    this.notificationsSubject.next(notifications);
  }
}
```

---

## Summary

Services and Dependency Injection are fundamental to Angular:

1. **Services** - Encapsulate business logic and data access
2. **DI Pattern** - Dependencies injected, not created
3. **Providers** - Register services with injectors
4. **Hierarchical Injectors** - Multiple levels of injection
5. **Injection Tokens** - Inject non-class values
6. **Advanced Patterns** - Factory, multi, existing providers

**Key Takeaways:**
- Use `providedIn: 'root'` for most services
- Keep services focused with single responsibility
- Expose observables, not subjects
- Use interfaces for abstraction
- Handle errors in services
- Test services with mocked dependencies
- Follow DI best practices for maintainable code

Mastering services and DI enables building scalable, testable applications like TheDispatch blog platform.
