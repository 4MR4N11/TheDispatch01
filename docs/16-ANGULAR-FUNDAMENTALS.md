# Angular Fundamentals and Architecture

This document explains WHAT Angular is, HOW it works internally, and WHY architectural decisions were made. We'll cover modern Angular features including standalone components, signals, and zoneless change detection.

---

## Table of Contents

1. [What Is Angular?](#what-is-angular)
2. [Angular Architecture Overview](#angular-architecture-overview)
3. [Standalone Components](#standalone-components)
4. [Signals - Modern Reactivity](#signals---modern-reactivity)
5. [Dependency Injection Deep Dive](#dependency-injection-deep-dive)
6. [Change Detection](#change-detection)
7. [Component Lifecycle](#component-lifecycle)
8. [Services and Singleton Pattern](#services-and-singleton-pattern)
9. [RxJS Observables](#rxjs-observables)
10. [HttpClient and API Communication](#httpclient-and-api-communication)
11. [Routing and Navigation](#routing-and-navigation)
12. [Guards and Route Protection](#guards-and-route-protection)
13. [Interceptors](#interceptors)
14. [Project Structure](#project-structure)

---

## What Is Angular?

**Angular** is a TypeScript-based web application framework developed by Google.

### Key Characteristics

1. **Component-Based Architecture**
   - UI built from reusable components
   - Each component = template + logic + styles

2. **TypeScript First**
   - Strongly typed
   - Compile-time error checking
   - Better IDE support

3. **Reactive Programming**
   - RxJS for async operations
   - Signals for state management (new in Angular 16+)

4. **Dependency Injection**
   - Built-in DI system
   - Singleton services
   - Hierarchical injectors

5. **CLI Tooling**
   - `ng generate`, `ng serve`, `ng build`
   - Automated tasks and scaffolding

### Angular vs Other Frameworks

| Feature | Angular | React | Vue |
|---------|---------|-------|-----|
| **Language** | TypeScript | JavaScript/JSX | JavaScript/SFC |
| **Architecture** | Framework (opinionated) | Library (flexible) | Progressive Framework |
| **DI** | Built-in | External (Context) | Provide/Inject |
| **Reactivity** | Signals + RxJS | Hooks | Ref/Reactive |
| **Learning Curve** | Steep | Moderate | Easy |

---

## Angular Architecture Overview

### The Big Picture

```
┌─────────────────────────────────────────────┐
│           Application (app.ts)              │
│  - Root component                           │
│  - Initializes services                     │
└──────────────────┬──────────────────────────┘
                   │
    ┌──────────────┴──────────────┐
    │                             │
┌───▼────┐                   ┌───▼────┐
│ Router │                   │Services│
│        │                   │        │
└───┬────┘                   └───┬────┘
    │                            │
    │ Activates                  │ Injected
    │ Components                 │ Into
    │                            │ Components
┌───▼────────────────────────────▼─────┐
│           Components                  │
│  - HomeComponent                      │
│  - ProfileComponent                   │
│  - PostComponent                      │
│  Each has: Template + Logic + Styles │
└───────────────────────────────────────┘
```

### Request Flow

```
1. User visits /home
   ↓
2. Angular Router matches URL → HomeComponent
   ↓
3. Router Guard checks authentication
   ↓
4. Component constructor executes
   ↓
5. Dependency Injection provides services
   ↓
6. ngOnInit lifecycle hook runs
   ↓
7. Component loads data from API
   ↓
8. Observable emits data
   ↓
9. Signal updates with data
   ↓
10. Change detection updates DOM
   ↓
11. User sees rendered page
```

---

## Standalone Components

### What Are Standalone Components?

**Traditional Angular (NgModules):**
```typescript
// Old way - requires NgModule
@NgModule({
  declarations: [HomeComponent],
  imports: [CommonModule, FormsModule],
  providers: [PostService]
})
export class HomeModule { }
```

**Modern Angular (Standalone):**
```typescript
// New way - no NgModule needed
@Component({
  selector: 'app-home',
  standalone: true,  // ← Standalone!
  imports: [CommonModule, FormsModule],  // Direct imports
  templateUrl: './home.html'
})
export class HomeComponent { }
```

### Why Standalone?

**Benefits:**

1. **Simpler** - No NgModule boilerplate
2. **Less code** - Components are self-contained
3. **Tree-shakeable** - Better bundle size
4. **Lazy loading** - Easier to implement
5. **Migration path** - Works with existing code

### Our App Configuration

**File:** `frontend/src/app/app.config.ts`

```typescript
export const appConfig: ApplicationConfig = {
  providers: [
    provideZonelessChangeDetection(),  // Modern change detection
    provideRouter(routes),              // Routing
    provideHttpClient(withInterceptors([authInterceptor]))  // HTTP with interceptor
  ]
};
```

**How it works:**

1. **provideZonelessChangeDetection()**
   - Modern change detection without Zone.js
   - Manual control over when to check for changes
   - Better performance

2. **provideRouter(routes)**
   - Configures routing
   - Replaces old RouterModule.forRoot()

3. **provideHttpClient(withInterceptors([...]))**
   - HTTP client setup
   - Functional interceptors (not class-based)

### Root Component

**File:** `frontend/src/app/app.ts`

```typescript
@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    RouterOutlet,      // For routing
    CommonModule,      // ngIf, ngFor, pipes
    NavbarComponent,   // Our custom components
    NotificationsComponent,
    ConfirmationModalComponent
  ],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class AppComponent implements OnInit {
  // Component logic
}
```

**Key points:**

- `standalone: true` - No NgModule needed
- `imports: [...]` - Directly import what you need
- Components, directives, pipes all importable
- Tree-shaking removes unused imports

---

## Signals - Modern Reactivity

### What Are Signals?

**Signals** are Angular's new reactive primitive (introduced in Angular 16).

**Definition:** A signal is a **wrapper around a value that notifies consumers when the value changes**.

### Traditional vs Signals

**Traditional (Zone.js):**
```typescript
export class HomeComponent {
  posts: PostResponse[] = [];  // Regular property

  loadPosts() {
    this.apiService.getPosts().subscribe(posts => {
      this.posts = posts;  // Assignment triggers change detection
      // Zone.js detects this change automatically
    });
  }
}
```

**With Signals:**
```typescript
export class HomeComponent {
  posts = signal<PostResponse[]>([]);  // Signal

  loadPosts() {
    this.apiService.getPosts().subscribe(posts => {
      this.posts.set(posts);  // Explicitly set signal
      // Change detection triggered only for this component
    });
  }
}
```

### Signal API

```typescript
// Create signal
const count = signal(0);

// Read value
console.log(count());  // 0

// Update value
count.set(5);          // Set to 5
count.update(n => n + 1);  // Increment

// In template
<p>Count: {{ count() }}</p>  // Auto-updates when signal changes
```

### Why Signals?

**Problems with Zone.js:**
1. **Over-checking** - Checks entire component tree
2. **Hidden** - Hard to know when change detection runs
3. **Performance** - Expensive for large apps

**Benefits of Signals:**
1. **Precise** - Only affected components update
2. **Explicit** - Clear when state changes
3. **Fast** - Minimal change detection
4. **Composable** - Computed signals

### Computed Signals

```typescript
export class HomeComponent {
  posts = signal<PostResponse[]>([]);

  // Automatically recalculates when posts changes
  postCount = computed(() => this.posts().length);

  // In template
  <p>You have {{ postCount() }} posts</p>
}
```

**How computed works:**
1. Signal tracks dependencies (`posts` in this case)
2. When `posts` changes, `postCount` recalculates automatically
3. Template updates with new value

### Signal Effects

```typescript
export class HomeComponent {
  darkMode = signal(false);

  constructor() {
    effect(() => {
      // Runs whenever darkMode changes
      const isDark = this.darkMode();
      document.body.classList.toggle('dark-theme', isDark);
    });
  }
}
```

**effect() runs:**
- Once on initialization
- Whenever tracked signals change
- Automatically cleans up on destroy

### Our Application's Signals

**AuthService:**
```typescript
@Injectable({ providedIn: 'root' })
export class AuthService {
  readonly isLoggedIn = signal(false);
  readonly currentUser = signal<UserResponse | null>(null);

  login(request: LoginRequest) {
    return this.http.post<AuthResponse>('/auth/login', request).pipe(
      tap(() => {
        this.isLoggedIn.set(true);  // Update signal
        this.checkAuth().subscribe();  // Load user
      })
    );
  }
}
```

**HomeComponent:**
```typescript
export class HomeComponent {
  protected readonly posts = signal<PostResponse[]>([]);
  protected readonly loading = signal(true);
  protected readonly currentUser = this.authService.currentUser;  // Reference signal

  loadFeed() {
    this.loading.set(true);
    this.apiService.getFeed().subscribe({
      next: (posts) => {
        this.posts.set(posts);
        this.loading.set(false);
      }
    });
  }
}
```

**Template usage:**
```html
<!-- No async pipe needed! -->
<div *ngIf="loading()">Loading...</div>

<div *ngFor="let post of posts()">
  <h3>{{ post.title }}</h3>
  <p>By {{ post.author }}</p>
</div>

<p *ngIf="currentUser()">
  Welcome, {{ currentUser()!.username }}
</p>
```

---

## Dependency Injection Deep Dive

### What Is Dependency Injection?

**DI** is a design pattern where objects receive their dependencies from external sources rather than creating them.

**Without DI (bad):**
```typescript
export class HomeComponent {
  private apiService: ApiService;

  constructor() {
    this.apiService = new ApiService(new HttpClient());  // ❌ Tightly coupled
  }
}
```

**With DI (good):**
```typescript
export class HomeComponent {
  private apiService = inject(ApiService);  // ✅ Angular provides it

  // Or constructor injection:
  constructor(private apiService: ApiService) { }
}
```

### How Angular DI Works

```
1. Service has @Injectable({ providedIn: 'root' })
   ↓
2. Angular creates singleton instance at app startup
   ↓
3. Component requests service via inject() or constructor
   ↓
4. Angular's injector provides the instance
   ↓
5. Same instance reused across all components
```

### Injectable Services

```typescript
@Injectable({
  providedIn: 'root'  // Singleton for entire app
})
export class AuthService {
  private readonly http = inject(HttpClient);

  login(request: LoginRequest) {
    return this.http.post('/auth/login', request);
  }
}
```

**@Injectable decorator:**
- Marks class as injectable
- `providedIn: 'root'` creates singleton
- Angular handles instantiation

### Modern inject() Function

**Old way (constructor injection):**
```typescript
export class HomeComponent {
  constructor(
    private authService: AuthService,
    private apiService: ApiService,
    private router: Router
  ) { }
}
```

**New way (inject function):**
```typescript
export class HomeComponent {
  private readonly authService = inject(AuthService);
  private readonly apiService = inject(ApiService);
  private readonly router = inject(Router);
}
```

**Benefits of inject():**
1. **Cleaner** - No constructor bloat
2. **Flexible** - Can inject anywhere (not just constructor)
3. **Type-safe** - Automatic type inference
4. **Testable** - Easy to mock

### Injection Context

**inject() only works in injection context:**

✅ **Valid:**
```typescript
export class HomeComponent {
  private authService = inject(AuthService);  // Constructor field

  constructor() {
    const router = inject(Router);  // In constructor
  }
}
```

❌ **Invalid:**
```typescript
export class HomeComponent {
  loadData() {
    const service = inject(ApiService);  // ❌ Not in injection context!
  }
}
```

---

## Change Detection

### What Is Change Detection?

**Change Detection** is how Angular synchronizes component state with the DOM.

**The problem:**
```typescript
this.count = 5;  // JavaScript updates variable
// But DOM still shows old value!
// Change detection updates DOM to match
```

### Traditional: Zone.js

**How Zone.js works:**

Zone.js **patches** browser APIs:
```typescript
// Zone.js intercepts:
setTimeout(() => {
  this.count++;  // Zone.js detects this
  // Triggers change detection for entire app
});

button.addEventListener('click', () => {
  this.count++;  // Zone.js detects this too
});
```

**Problem:** Over-checking
- Every async operation triggers full tree check
- Performance issues in large apps

### Modern: Zoneless Change Detection

**Our app uses zoneless:**
```typescript
export const appConfig: ApplicationConfig = {
  providers: [
    provideZonelessChangeDetection(),  // ← No Zone.js!
  ]
};
```

**How zoneless works:**

1. **Signals** - Automatic tracking
   ```typescript
   count = signal(0);
   count.set(5);  // Triggers change detection for this component only
   ```

2. **markForCheck()** - Manual trigger
   ```typescript
   constructor(private cdr: ChangeDetectorRef) {}

   loadData() {
     this.apiService.getPosts().subscribe(posts => {
       this.posts = posts;
       this.cdr.markForCheck();  // Manually mark for update
     });
   }
   ```

3. **OnPush strategy** - Component only checks when:
   - Input changes
   - Event fires
   - Signal updates
   - Observable emits (with async pipe)

**Our components use OnPush:**
```typescript
@Component({
  selector: 'app-home',
  changeDetection: ChangeDetectionStrategy.OnPush,  // ← Optimized
})
export class HomeComponent { }
```

### Change Detection Strategies

**Default:**
- Checks component on every async event
- Checks entire component tree
- Slow but safe

**OnPush:**
- Only checks when:
  - @Input() changes (reference comparison)
  - Event handler fires
  - Observable emits (async pipe)
  - Signal updates
- Fast but requires discipline

**Example with OnPush:**
```typescript
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class PostComponent {
  @Input() post: PostResponse;  // Only checks when post reference changes

  // This WON'T trigger change detection:
  updateProperty() {
    this.post.title = "New Title";  // ❌ Mutating object
  }

  // This WILL trigger change detection:
  updatePost() {
    this.post = { ...this.post, title: "New Title" };  // ✅ New reference
  }
}
```

---

## Component Lifecycle

### Lifecycle Hooks

Angular components have lifecycle hooks that run at specific times.

```
Component Created
    ↓
constructor()         // Dependency injection
    ↓
ngOnInit()           // Initialize component
    ↓
ngAfterViewInit()    // View is ready
    ↓
[Component Active]
    ↓
ngOnDestroy()        // Cleanup before destroy
    ↓
Component Destroyed
```

### constructor()

```typescript
export class HomeComponent {
  private apiService = inject(ApiService);

  constructor() {
    // Called when Angular creates component instance
    // Use for: Dependency injection only
    // DON'T: Access DOM, make HTTP calls
  }
}
```

**What happens:**
1. Angular instantiates component class
2. Dependency injection resolves
3. Properties initialized

**When to use:**
- Initialize local variables
- Set up subscriptions (with takeUntilDestroyed)

### ngOnInit()

```typescript
export class HomeComponent implements OnInit {
  ngOnInit() {
    // Called after constructor and first change detection
    // Use for: HTTP calls, initialization logic
    this.loadPosts();
  }

  loadPosts() {
    this.apiService.getPosts().subscribe(posts => {
      this.posts.set(posts);
    });
  }
}
```

**What happens:**
1. Component fully initialized
2. @Input properties set
3. Ready for data loading

**When to use:**
- API calls
- Subscribe to observables
- Initialize complex logic

### ngOnDestroy()

```typescript
export class HomeComponent implements OnDestroy {
  private subscription: Subscription;

  constructor() {
    this.subscription = this.apiService.getPosts().subscribe();
  }

  ngOnDestroy() {
    // Called before component is destroyed
    // Use for: Cleanup, unsubscribe
    this.subscription.unsubscribe();
  }
}
```

**What happens:**
1. Component about to be removed from DOM
2. Last chance for cleanup

**When to use:**
- Unsubscribe from observables
- Clear intervals/timeouts
- Remove event listeners

### takeUntilDestroyed (Modern Cleanup)

**Old way:**
```typescript
export class HomeComponent implements OnDestroy {
  private destroy$ = new Subject<void>();

  ngOnInit() {
    this.apiService.getPosts()
      .pipe(takeUntil(this.destroy$))
      .subscribe(posts => this.posts = posts);
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
```

**New way (simpler):**
```typescript
export class HomeComponent {
  constructor() {
    this.apiService.getPosts()
      .pipe(takeUntilDestroyed())  // ← Automatic cleanup!
      .subscribe(posts => this.posts.set(posts));
  }
}
```

**takeUntilDestroyed():**
- Automatically unsubscribes on ngOnDestroy
- Must be called in injection context (constructor or field initializer)
- Cleaner than manual cleanup

**Our AppComponent:**
```typescript
export class AppComponent {
  constructor() {
    this.authService.authInitialized$
      .pipe(takeUntilDestroyed())  // Auto-cleanup
      .subscribe(initialized => {
        this.authInitialized.set(initialized);
      });
  }
}
```

---

## Services and Singleton Pattern

### What Are Services?

**Services** are classes that hold business logic, data, or utilities shared across components.

**Characteristics:**
1. **Singleton** - One instance for entire app (with `providedIn: 'root'`)
2. **Injectable** - Can be injected into components
3. **Reusable** - Shared logic, not duplicated

### AuthService Example

```typescript
@Injectable({
  providedIn: 'root'  // Singleton
})
export class AuthService {
  private readonly http = inject(HttpClient);
  readonly isLoggedIn = signal(false);
  readonly currentUser = signal<UserResponse | null>(null);

  login(request: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>('/auth/login', request, {
      withCredentials: true  // Send cookies
    }).pipe(
      tap(response => {
        this.isLoggedIn.set(true);
        this.checkAuth().subscribe();
      })
    );
  }

  checkAuth(): Observable<UserResponse> {
    return this.http.get<UserResponse>('/users/me', {
      withCredentials: true
    }).pipe(
      tap(user => {
        this.isLoggedIn.set(true);
        this.currentUser.set(user);
      })
    );
  }
}
```

**Why services?**

1. **Separation of concerns** - Components handle UI, services handle data
2. **Reusability** - Multiple components use same auth logic
3. **Testability** - Easy to mock services
4. **State management** - Services hold shared state

### Service Communication Pattern

```
HomeComponent
    │
    ├── inject(AuthService)
    │   - Get current user
    │   - Check if logged in
    │
    └── inject(ApiService)
        - Load posts
        - Like/unlike posts

ProfileComponent
    │
    └── inject(AuthService)  ← Same instance!
        - Get current user
```

**Same AuthService instance shared by all components.**

---

## RxJS Observables

### What Are Observables?

**Observable** is a stream of data over time.

**Analogy:** Netflix stream
- You subscribe → start watching
- Data arrives over time → episodes play
- You unsubscribe → stop watching

### Observable vs Promise

| Feature | Observable | Promise |
|---------|------------|---------|
| **Lazy** | Yes (doesn't execute until subscribed) | No (executes immediately) |
| **Multiple values** | Yes (stream) | No (single value) |
| **Cancellable** | Yes (unsubscribe) | No |
| **Operators** | Many (map, filter, switchMap) | Few (then, catch) |

### Basic Observable

```typescript
const observable$ = new Observable<number>(subscriber => {
  subscriber.next(1);
  subscriber.next(2);
  subscriber.next(3);
  subscriber.complete();
});

observable$.subscribe({
  next: (value) => console.log(value),  // 1, 2, 3
  complete: () => console.log('Done!')
});
```

### HttpClient Returns Observables

```typescript
this.http.get<PostResponse[]>('/posts')
  .subscribe({
    next: (posts) => console.log('Received:', posts),
    error: (err) => console.error('Error:', err),
    complete: () => console.log('Request complete')
  });
```

**Why observables for HTTP?**
1. **Cancellable** - Can cancel in-flight requests
2. **Operators** - Transform data before handling
3. **Retry logic** - Built-in retry operators
4. **Consistent API** - All async operations use observables

### Common RxJS Operators

#### tap

```typescript
this.http.post('/auth/login', request).pipe(
  tap(response => {
    console.log('Login response:', response);  // Side effect
    this.isLoggedIn.set(true);                 // Update state
  })
).subscribe();
```

**Purpose:** Perform side effects without changing data

#### map

```typescript
this.http.get<User>('/users/me').pipe(
  map(user => user.username)  // Extract username
).subscribe(username => console.log(username));
```

**Purpose:** Transform emitted values

#### catchError

```typescript
this.http.get('/posts').pipe(
  catchError(error => {
    console.error('Failed to load posts', error);
    return of([]);  // Return empty array as fallback
  })
).subscribe(posts => this.posts.set(posts));
```

**Purpose:** Handle errors, provide fallback

#### switchMap

```typescript
this.route.params.pipe(
  switchMap(params => {
    const id = params['id'];
    return this.apiService.getPost(id);  // Cancel previous, start new
  })
).subscribe(post => this.post.set(post));
```

**Purpose:** Cancel previous observable, switch to new one

#### filter

```typescript
this.authService.authInitialized$.pipe(
  filter(initialized => initialized),  // Only emit when true
  take(1)                              // Take first emission
).subscribe(() => console.log('Auth ready!'));
```

**Purpose:** Only emit values that pass condition

#### takeUntilDestroyed

```typescript
constructor() {
  this.apiService.getPosts().pipe(
    takeUntilDestroyed()  // Auto-unsubscribe on destroy
  ).subscribe(posts => this.posts.set(posts));
}
```

**Purpose:** Automatic cleanup on component destroy

---

## HttpClient and API Communication

### Setup

**Config:**
```typescript
export const appConfig: ApplicationConfig = {
  providers: [
    provideHttpClient(withInterceptors([authInterceptor]))
  ]
};
```

**HttpClient automatically provided to all services.**

### Making Requests

```typescript
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.apiUrl;

  // GET request
  getPosts(): Observable<PostResponse[]> {
    return this.http.get<PostResponse[]>(`${this.baseUrl}/posts/all`, {
      withCredentials: true  // Send cookies
    });
  }

  // POST request
  createPost(post: PostRequest): Observable<string> {
    return this.http.post<string>(`${this.baseUrl}/posts/create`, post, {
      withCredentials: true
    });
  }

  // DELETE request
  deletePost(id: number): Observable<any> {
    return this.http.delete(`${this.baseUrl}/posts/${id}`, {
      withCredentials: true
    });
  }
}
```

### Type Safety

```typescript
// ✅ Type-safe
this.http.get<PostResponse[]>('/posts').subscribe(posts => {
  posts.forEach(post => {
    console.log(post.title);  // TypeScript knows 'title' exists
  });
});

// ❌ Not type-safe
this.http.get('/posts').subscribe((posts: any) => {
  posts.forEach(post => {
    console.log(post.title);  // No type checking
  });
});
```

### withCredentials

```typescript
this.http.get('/posts', {
  withCredentials: true  // ← Send cookies with request
})
```

**Why needed:**
- Backend uses HttpOnly cookies for JWT
- Browser doesn't send cookies to different origins by default
- `withCredentials: true` enables cookie sending

**CORS requirement:**
- Backend must allow credentials: `Access-Control-Allow-Credentials: true`
- Backend must specify origin (can't use `*` wildcard)

---

## Routing and Navigation

### Route Configuration

**File:** `frontend/src/app/app.routes.ts`

```typescript
export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  { path: 'register', component: RegisterComponent },
  { path: 'home', component: HomeComponent, canActivate: [AuthGuard] },
  { path: 'profile/:username', component: ProfileComponent, canActivate: [AuthGuard] },
  { path: '', redirectTo: '/home', pathMatch: 'full' },
  { path: '**', redirectTo: '/home' }  // Wildcard for 404
];
```

**Route anatomy:**

- `path` - URL pattern
- `component` - Component to display
- `canActivate` - Guards that must pass
- `redirectTo` - Redirect target
- `pathMatch: 'full'` - Match entire path (not prefix)

### Router Outlet

**File:** `frontend/src/app/app.html`

```html
<app-navbar></app-navbar>
<router-outlet></router-outlet>  <!-- Components render here -->
<app-notifications></app-notifications>
```

**How it works:**
1. User navigates to `/home`
2. Router matches route
3. HomeComponent renders in `<router-outlet>`
4. Navbar and notifications stay visible (not in outlet)

### Programmatic Navigation

```typescript
export class LoginComponent {
  private readonly router = inject(Router);

  onLoginSuccess() {
    this.router.navigate(['/home']);  // Navigate to home
  }

  viewProfile(username: string) {
    this.router.navigate(['/profile', username]);  // Navigate with param
  }
}
```

**Navigation methods:**

```typescript
// Navigate to absolute path
router.navigate(['/home']);

// Navigate with parameters
router.navigate(['/post', postId]);  // → /post/123

// Navigate with query params
router.navigate(['/search'], { queryParams: { q: 'angular' } });  // → /search?q=angular

// Navigate relative to current route
router.navigate(['../'], { relativeTo: this.route });
```

### Route Parameters

```typescript
export class ProfileComponent {
  private readonly route = inject(ActivatedRoute);

  ngOnInit() {
    // Get route parameter
    this.route.params.subscribe(params => {
      const username = params['username'];
      this.loadProfile(username);
    });

    // Or with snapshot (not reactive)
    const username = this.route.snapshot.params['username'];
  }
}
```

---

## Guards and Route Protection

### What Are Guards?

**Guards** are functions that determine if a route can be activated.

**Use cases:**
- Prevent unauthenticated access
- Check permissions (admin only)
- Confirm before leaving (unsaved changes)

### AuthGuard

**File:** `frontend/src/app/core/guard/auth-guard.ts`

```typescript
export const AuthGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  return authService.waitForAuthInitialization().pipe(
    map(() => {
      if (authService.isLoggedIn()) {
        return true;  // Allow navigation
      } else {
        router.navigate(['/login'], { queryParams: { returnUrl: state.url } });
        return false;  // Block navigation
      }
    })
  );
};
```

**How it works:**

1. User tries to navigate to `/home`
2. AuthGuard executes
3. Waits for auth initialization
4. Checks if logged in
5. If logged in → allow navigation
6. If not logged in → redirect to `/login`

**returnUrl query param:**
```
User tries: /profile/alice
Redirects to: /login?returnUrl=/profile/alice
After login: redirects back to /profile/alice
```

### AdminGuard

```typescript
export const AdminGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  return authService.waitForAuthInitialization().pipe(
    map(() => {
      const user = authService.getCurrentUser();
      if (user?.role === 'ADMIN') {
        return true;
      } else {
        router.navigate(['/home']);
        return false;
      }
    })
  );
};
```

**Multiple guards:**
```typescript
{
  path: 'admin',
  component: AdminComponent,
  canActivate: [AuthGuard, AdminGuard]  // Both must pass
}
```

---

## Interceptors

### What Are Interceptors?

**Interceptors** modify HTTP requests or responses globally.

**Use cases:**
- Add authentication headers
- Log requests
- Handle errors globally
- Retry failed requests

### AuthInterceptor

**File:** `frontend/src/app/core/auth/auth.interceptor.ts`

```typescript
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  // Clone request and add withCredentials
  const authReq = req.clone({
    withCredentials: true  // Send cookies with every request
  });

  return next(authReq);
};
```

**How it works:**

```
Component makes HTTP request
    ↓
Request enters interceptor
    ↓
Interceptor clones request
    ↓
Adds withCredentials: true
    ↓
Modified request sent to server
    ↓
Response returns through interceptor
    ↓
Response reaches component
```

**Why clone request?**
- Requests are immutable
- Can't modify original
- Must clone to change properties

### Registering Interceptor

```typescript
export const appConfig: ApplicationConfig = {
  providers: [
    provideHttpClient(withInterceptors([authInterceptor]))  // Register here
  ]
};
```

**All HTTP requests automatically use this interceptor.**

---

## Project Structure

```
frontend/src/app/
│
├── app.ts                    # Root component
├── app.config.ts            # App configuration
├── app.routes.ts            # Route definitions
│
├── core/                    # Singleton services, guards
│   ├── auth/
│   │   ├── auth.service.ts       # Authentication logic
│   │   ├── auth.interceptor.ts   # Add credentials to requests
│   │   ├── api.service.ts        # HTTP API calls
│   │   └── admin.guard.ts        # Admin route protection
│   │
│   ├── guard/
│   │   └── auth-guard.ts         # Auth route protection
│   │
│   ├── services/
│   │   ├── notification.service.ts  # Toast notifications
│   │   ├── subscription.service.ts  # Follow/unfollow
│   │   └── theme.service.ts         # Dark mode
│   │
│   └── utils/
│       └── error-handler.ts      # Error message extraction
│
├── shared/                  # Reusable components, utilities
│   ├── components/
│   │   ├── navbar/
│   │   ├── notifications/
│   │   ├── confirmation-modal/
│   │   ├── new-post-modal.component.ts
│   │   └── edit-post-modal.component.ts
│   │
│   ├── models/
│   │   └── models.ts            # TypeScript interfaces
│   │
│   ├── utils/
│   │   ├── reading-time.util.ts
│   │   └── format.util.ts
│   │
│   └── services/
│       └── confirmation-modal.service.ts
│
└── features/                # Feature modules
    ├── auth/
    │   ├── login/
    │   └── register/
    │
    ├── home/               # Feed
    ├── posts/              # Post detail
    ├── my-blog/            # User's own posts
    ├── create-post/        # Create new post
    ├── edit-post/          # Edit existing post
    ├── users/              # Profile view
    ├── edit-profile/       # Edit own profile
    ├── notifications/      # Notifications
    ├── reports/            # User reports
    └── admin/              # Admin panel
```

### Naming Conventions

**Components:**
- PascalCase: `HomeComponent`
- File: `home.component.ts` or `home.ts`
- Selector: `app-home`

**Services:**
- PascalCase: `AuthService`
- File: `auth.service.ts`

**Guards:**
- PascalCase: `AuthGuard`
- File: `auth-guard.ts`

**Interfaces:**
- PascalCase: `PostResponse`
- File: `models.ts` (grouped)

---

## Key Takeaways

### Modern Angular Features

- **Standalone components** - No NgModules needed
- **Signals** - Precise reactivity, better performance
- **Zoneless change detection** - Manual control, faster
- **inject() function** - Cleaner dependency injection
- **Functional guards** - Simpler than class guards
- **Functional interceptors** - Easier to understand

### Architecture Patterns

- **Component-based** - UI built from reusable pieces
- **Service-oriented** - Business logic in services
- **Reactive** - Observables + Signals for async
- **Type-safe** - TypeScript everywhere
- **Lazy loading ready** - Standalone makes this easy

### Best Practices

- Use signals for local state
- Use services for shared state
- OnPush change detection for performance
- takeUntilDestroyed for subscription cleanup
- Type HTTP responses for safety
- Guards for route protection
- Interceptors for global HTTP logic

---

**Next**: Component Deep Dive - Line-by-line explanations of HomeComponent, AuthComponents, and how everything connects! Continue?
