# Angular Fundamentals: Architecture and Core Concepts

## Overview

This document explains **Angular's core architecture** using your Dispatch application. We'll cover:
- Application bootstrap process
- Standalone components (modern Angular)
- Component architecture
- Services and dependency injection
- Routing basics
- Your application structure

**Your stack**: Angular 20.3.7 (standalone components, no NgModule)

---

## 1. Angular Architecture Overview

### What is Angular?

Angular is a **component-based framework** for building single-page applications (SPAs). Everything in Angular is built around **components**.

```
Your Dispatch Application
  ↓
[AppComponent] (Root)
  ├─ [NavbarComponent]
  ├─ [RouterOutlet] (where routes render)
  │   ├─ [HomeComponent]
  │   ├─ [PostDetailComponent]
  │   ├─ [ProfileComponent]
  │   └─ ...
  └─ [NotificationsComponent]
```

### Component-Based Architecture

**Component** = Building block of Angular apps

Each component has:
- **Template** (HTML): What users see
- **Class** (TypeScript): Logic and data
- **Styles** (CSS): Component-specific styling
- **Metadata** (@Component decorator): Configuration

```typescript
// Your AppComponent
@Component({
  selector: 'app-root',           // HTML tag: <app-root></app-root>
  standalone: true,               // No NgModule needed!
  imports: [RouterOutlet, ...],   // Dependencies
  templateUrl: './app.html',      // Template file
  styleUrl: './app.css'           // Styles file
})
export class AppComponent {
  // Component logic here
}
```

---

## 2. Application Bootstrap

### How Your App Starts (main.ts)

```typescript
// main.ts - Entry point
import { bootstrapApplication } from '@angular/platform-browser';
import { AppComponent } from './app/app';
import { appConfig } from './app/app.config';

bootstrapApplication(AppComponent, {
  ...appConfig,
}).catch(err => console.error(err));
```

**What happens**:

```
1. Browser loads index.html
   ↓
2. Loads main.ts (JavaScript bundle)
   ↓
3. bootstrapApplication(AppComponent) called
   ↓
4. Angular creates application platform
   ↓
5. Creates root injector (for services)
   ↓
6. Compiles AppComponent (if not AOT)
   ↓
7. Creates AppComponent instance
   ↓
8. Renders component to DOM
   ↓
9. Application running!
```

### index.html (The Container)

```html
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <title>The Dispatch</title>
  <base href="/">
</head>
<body>
  <app-root></app-root>  <!-- Angular renders here -->
  <!-- Angular replaces <app-root> with your AppComponent template -->
</body>
</html>
```

### Application Configuration (appConfig)

```typescript
// app.config.ts (simplified from your app)
export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes),           // Routing
    provideHttpClient(               // HTTP client
      withInterceptors([authInterceptor])
    ),
    provideAnimations(),             // Animations
    // Your services are auto-provided via providedIn: 'root'
  ]
};
```

---

## 3. Standalone Components (Modern Angular)

### Old Way (NgModule) vs New Way (Standalone)

**Old way** (before Angular 14):
```typescript
// app.module.ts
@NgModule({
  declarations: [AppComponent, NavbarComponent, ...],
  imports: [BrowserModule, RouterModule, ...],
  providers: [AuthService, ApiService, ...],
  bootstrap: [AppComponent]
})
export class AppModule { }
```

**Your app** (standalone - modern):
```typescript
// app.component.ts
@Component({
  selector: 'app-root',
  standalone: true,  // ✅ No NgModule!
  imports: [         // Import dependencies directly
    RouterOutlet,
    CommonModule,
    NavbarComponent,
    NotificationsComponent
  ],
  templateUrl: './app.html'
})
export class AppComponent { }
```

**Benefits of standalone**:
- ✅ Simpler (no NgModule boilerplate)
- ✅ Better tree-shaking (unused code removed)
- ✅ Easier lazy loading
- ✅ More explicit dependencies

---

## 4. Component Deep Dive

### Your AppComponent (Root Component)

```typescript
@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, CommonModule, NavbarComponent, NotificationsComponent, ConfirmationModalComponent],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class AppComponent implements OnInit {
  // Dependency injection (modern way)
  protected readonly authService = inject(AuthService);

  // Signals for reactive state
  protected readonly authInitialized = signal(false);

  constructor() {
    // Subscribe to auth initialization
    this.authService.authInitialized$
      .pipe(takeUntilDestroyed())  // Auto-cleanup on destroy
      .subscribe(initialized => {
        this.authInitialized.set(initialized);
      });
  }

  ngOnInit() {
    // Lifecycle hook - called after component initialized
    this.authService.initializeAuth();
  }
}
```

### Component Lifecycle

Angular components have **lifecycle hooks**:

```typescript
export class NavbarComponent implements OnInit, OnDestroy {

  ngOnInit() {
    // Called ONCE after component initialized
    // Perfect for: data loading, subscriptions
    if (this.authService.isLoggedIn()) {
      this.loadUnreadCount();
      // Start polling for notifications
      this.pollSubscription = interval(30000).subscribe(() => {
        this.loadUnreadCount();
      });
    }
  }

  ngOnDestroy() {
    // Called ONCE before component destroyed
    // Perfect for: cleanup, unsubscribe
    this.pollSubscription?.unsubscribe();
  }
}
```

**Complete lifecycle**:

```
constructor()           // Component created
  ↓
ngOnChanges()          // Input properties changed (if any)
  ↓
ngOnInit()             // Component initialized (ONCE)
  ↓
ngDoCheck()            // Change detection runs
  ↓
ngAfterContentInit()   // Content projected into component
  ↓
ngAfterContentChecked() // After checking projected content
  ↓
ngAfterViewInit()      // Component view initialized
  ↓
ngAfterViewChecked()   // After checking component view
  ↓
... (component active, change detection runs multiple times)
  ↓
ngOnDestroy()          // Component destroyed (ONCE)
```

**Most commonly used**:
- `ngOnInit()` - Initialize component (90% of cases)
- `ngOnDestroy()` - Cleanup (unsubscribe, clear timers)
- `ngOnChanges()` - React to input changes

---

## 5. Services and Dependency Injection

### What are Services?

**Services** = Shared logic and data between components

Your app has:
- `AuthService` - Authentication logic
- `ApiService` - HTTP requests
- `NotificationService` - Toast notifications
- `ThemeService` - Dark/light theme

### Service Structure

```typescript
// Your AuthService
@Injectable({
  providedIn: 'root'  // Singleton - one instance for entire app
})
export class AuthService {
  private readonly http = inject(HttpClient);
  readonly isLoggedIn = signal(false);
  readonly currentUser = signal<UserResponse | null>(null);

  login(request: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.baseUrl}/login`, request, {
      withCredentials: true
    }).pipe(
      tap(response => {
        this.isLoggedIn.set(true);
        this.checkAuth().subscribe();
      })
    );
  }

  checkAuth(): Observable<UserResponse> {
    return this.http.get<UserResponse>(`${environment.apiUrl}/users/me`, {
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

### Dependency Injection (DI)

**Old way** (constructor injection):
```typescript
export class MyComponent {
  constructor(
    private authService: AuthService,
    private apiService: ApiService,
    private router: Router
  ) { }
}
```

**Your app** (inject() function - modern):
```typescript
export class NavbarComponent {
  // Modern DI - cleaner!
  protected readonly authService = inject(AuthService);
  private readonly apiService = inject(ApiService);
  private readonly router = inject(Router);

  constructor() {
    // Constructor is now cleaner
  }
}
```

**How DI works**:

```
1. You request AuthService
   ↓
2. Angular checks: Does AuthService exist?
   - providedIn: 'root' → Create singleton
   - providedIn: 'platform' → One per platform
   - provided in component → One per component
   ↓
3. If AuthService needs HttpClient, inject that too (recursive)
   ↓
4. Return AuthService instance
```

**Benefits**:
- ✅ Services are **singletons** (one instance shared)
- ✅ Easy to test (inject mocks)
- ✅ Automatic dependency resolution

---

## 6. Component Communication

### Parent → Child (Input)

```typescript
// Parent component
@Component({
  template: `
    <app-post-card [post]="selectedPost"></app-post-card>
  `
})
export class ParentComponent {
  selectedPost = signal<Post>({ id: 1, title: "..." });
}

// Child component
@Component({
  selector: 'app-post-card'
})
export class PostCardComponent {
  @Input() post!: Post;  // Receives data from parent
}
```

### Child → Parent (Output)

```typescript
// Child component
@Component({
  selector: 'app-post-card'
})
export class PostCardComponent {
  @Output() postLiked = new EventEmitter<number>();

  onLike(postId: number) {
    this.postLiked.emit(postId);  // Send event to parent
  }
}

// Parent component
@Component({
  template: `
    <app-post-card (postLiked)="handleLike($event)"></app-post-card>
  `
})
export class ParentComponent {
  handleLike(postId: number) {
    console.log('Post liked:', postId);
  }
}
```

### Sibling Communication (via Service)

```typescript
// Shared service
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private messageSubject = new BehaviorSubject<string>('');
  message$ = this.messageSubject.asObservable();

  sendMessage(msg: string) {
    this.messageSubject.next(msg);
  }
}

// Component A (sender)
export class ComponentA {
  private notifService = inject(NotificationService);

  sendNotification() {
    this.notifService.sendMessage('Hello from A!');
  }
}

// Component B (receiver)
export class ComponentB {
  private notifService = inject(NotificationService);

  constructor() {
    this.notifService.message$.subscribe(msg => {
      console.log('Received:', msg);
    });
  }
}
```

---

## 7. Routing and Navigation

### Route Configuration

```typescript
// Your app.routes.ts
export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  { path: 'register', component: RegisterComponent },
  { path: 'home', component: HomeComponent, canActivate: [AuthGuard] },
  { path: 'profile/:username', component: ProfileComponent, canActivate: [AuthGuard] },
  { path: 'post/:id', component: PostDetailComponent, canActivate: [AuthGuard] },
  { path: '', redirectTo: '/home', pathMatch: 'full' },
  { path: '**', redirectTo: '/home' }  // Wildcard - 404 handler
];
```

### Router Outlet

```html
<!-- app.html -->
<app-navbar></app-navbar>  <!-- Always visible -->

<router-outlet></router-outlet>  <!-- Route renders here -->
<!-- When user goes to /home, HomeComponent renders here -->
<!-- When user goes to /post/5, PostDetailComponent renders here -->

<app-notifications></app-notifications>  <!-- Always visible -->
```

### Programmatic Navigation

```typescript
// NavbarComponent
export class NavbarComponent {
  private readonly router = inject(Router);

  logout() {
    this.authService.logout();
    this.router.navigate(['/login']);  // Navigate to /login
  }

  viewProfile(username: string) {
    this.router.navigate(['/profile', username]);  // Navigate to /profile/:username
  }
}
```

### Route Parameters

```typescript
// PostDetailComponent
export class PostDetailComponent {
  private readonly route = inject(ActivatedRoute);

  constructor() {
    // Get route parameter: /post/:id
    const postId = Number(this.route.snapshot.paramMap.get('id'));
    // URL: /post/5 → postId = 5

    this.apiService.getPostById(postId).subscribe(post => {
      this.post.set(post);
    });
  }
}
```

### Route Guards (Protection)

```typescript
// Your AuthGuard
export const AuthGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  // Check if user is authenticated
  if (authService.isLoggedIn()) {
    return true;  // Allow access
  }

  // Redirect to login
  router.navigate(['/login']);
  return false;  // Block access
};

// AdminGuard
export const AdminGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  const user = authService.currentUser();
  if (user?.role === 'ADMIN') {
    return true;  // Allow access
  }

  router.navigate(['/home']);
  return false;  // Block access
};
```

**Usage in routes**:
```typescript
{ path: 'admin', component: AdminComponent, canActivate: [AuthGuard, AdminGuard] }
// Must pass BOTH guards to access /admin
```

---

## 8. Your Application Structure

### Directory Layout

```
frontend/src/app/
├── app.ts                    # Root component
├── app.html                  # Root template
├── app.css                   # Root styles
├── app.routes.ts             # Route configuration
├── app.config.ts             # App configuration
│
├── core/                     # Singleton services (app-wide)
│   ├── auth/
│   │   ├── auth.service.ts   # Authentication logic
│   │   ├── api.service.ts    # HTTP requests
│   │   ├── auth.interceptor.ts # Add auth headers
│   │   └── admin.guard.ts    # Admin route guard
│   ├── guard/
│   │   └── auth-guard.ts     # Auth route guard
│   └── services/
│       ├── notification.service.ts
│       ├── theme.service.ts
│       └── subscription.service.ts
│
├── features/                 # Feature components (pages)
│   ├── auth/
│   │   ├── login/
│   │   └── register/
│   ├── home/
│   ├── posts/
│   ├── users/
│   ├── my-blog/
│   ├── create-post/
│   ├── edit-post/
│   └── admin/
│
└── shared/                   # Reusable components/utilities
    ├── components/
    │   ├── navbar/
    │   ├── notifications/
    │   └── confirmation-modal/
    ├── models/
    │   └── models.ts         # TypeScript interfaces
    └── utils/
        ├── reading-time.util.ts
        └── format.util.ts
```

### Design Patterns

**1. Core Module Pattern**
```typescript
// Services in core/ are singletons (providedIn: 'root')
@Injectable({ providedIn: 'root' })
export class AuthService { }
// One instance for entire app
```

**2. Feature Modules Pattern**
```typescript
// Each feature has its own directory
features/posts/
  ├── posts.component.ts
  ├── posts.component.html
  └── posts.component.css
```

**3. Shared Module Pattern**
```typescript
// Reusable components in shared/
shared/components/navbar/
  ├── navbar.component.ts
  ├── navbar.component.html
  └── navbar.component.css
```

---

## 9. Signals (Modern Reactive State)

### What are Signals?

**Signals** = Angular's new reactive primitive (Angular 16+)

**Before signals** (RxJS):
```typescript
// Observable-based (verbose)
isLoggedIn$ = new BehaviorSubject<boolean>(false);

// Read value
this.isLoggedIn$.subscribe(value => console.log(value));

// Update value
this.isLoggedIn$.next(true);
```

**With signals** (simpler):
```typescript
// Signal-based (clean!)
isLoggedIn = signal(false);

// Read value
const value = this.isLoggedIn();  // Just call it!

// Update value
this.isLoggedIn.set(true);  // Set new value
this.isLoggedIn.update(v => !v);  // Update based on current
```

### Your App's Signals

```typescript
// NavbarComponent
export class NavbarComponent {
  protected readonly dropdownOpen = signal(false);
  protected readonly unreadCount = signal(0);
  protected readonly notifications = signal<NotificationResponse[]>([]);
  protected readonly loadingNotifications = signal(false);

  toggleDropdown() {
    this.dropdownOpen.update(v => !v);  // Toggle
  }

  loadNotifications() {
    this.loadingNotifications.set(true);  // Start loading

    this.apiService.getNotifications(0, 10).subscribe({
      next: (notifications) => {
        this.notifications.set(notifications);  // Set data
        this.loadingNotifications.set(false);   // Stop loading
      }
    });
  }
}
```

### Signal Benefits

```typescript
// Template automatically updates when signal changes!

// Component
protected readonly count = signal(0);

increment() {
  this.count.update(v => v + 1);
  // Template re-renders automatically (OnPush compatible!)
}

// Template
<p>Count: {{ count() }}</p>  <!-- Always shows latest value -->
```

**Benefits**:
- ✅ Simpler than Observables for simple state
- ✅ Automatic change detection (even with OnPush)
- ✅ Type-safe
- ✅ Better performance (fine-grained reactivity)

---

## 10. Change Detection Strategy

### Default vs OnPush

**Default** (checks entire component tree):
```typescript
@Component({
  selector: 'app-my-component',
  changeDetection: ChangeDetectionStrategy.Default  // Default
})
```

**OnPush** (only checks when inputs change or events fire):
```typescript
// Your PostDetailComponent uses this!
@Component({
  selector: 'app-post-detail',
  changeDetection: ChangeDetectionStrategy.OnPush  // ✅ Better performance
})
export class PostDetailComponent {
  protected readonly post = signal<PostResponse | null>(null);

  // Signals work with OnPush automatically!
  updatePost() {
    this.post.set(newPost);  // Change detection triggered!
  }
}
```

**Why OnPush**:
- ✅ Better performance (less change detection runs)
- ✅ Forces immutability patterns
- ✅ Works perfectly with signals

---

## 11. Common Patterns in Your App

### Pattern 1: Service with Signals

```typescript
@Injectable({ providedIn: 'root' })
export class AuthService {
  // State as signals
  readonly isLoggedIn = signal(false);
  readonly currentUser = signal<UserResponse | null>(null);

  // Methods that update state
  login(request: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(...).pipe(
      tap(() => this.isLoggedIn.set(true))  // Update signal
    );
  }
}
```

### Pattern 2: Component with Loading States

```typescript
export class PostDetailComponent {
  protected readonly post = signal<PostResponse | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  loadPost(id: number) {
    this.loading.set(true);
    this.error.set(null);

    this.apiService.getPostById(id).subscribe({
      next: post => {
        this.post.set(post);
        this.loading.set(false);
      },
      error: err => {
        this.error.set(err.message);
        this.loading.set(false);
      }
    });
  }
}
```

### Pattern 3: Cleanup with ngOnDestroy

```typescript
export class NavbarComponent implements OnDestroy {
  private pollSubscription?: Subscription;

  ngOnInit() {
    // Start polling
    this.pollSubscription = interval(30000).subscribe(() => {
      this.loadUnreadCount();
    });
  }

  ngOnDestroy() {
    // Cleanup - prevent memory leaks!
    this.pollSubscription?.unsubscribe();
  }
}
```

### Pattern 4: takeUntilDestroyed (Auto-cleanup)

```typescript
export class AppComponent {
  constructor() {
    // Automatically unsubscribes when component destroyed!
    this.authService.authInitialized$
      .pipe(takeUntilDestroyed())  // ✅ No manual cleanup needed
      .subscribe(initialized => {
        this.authInitialized.set(initialized);
      });
  }
}
```

---

## 12. Key Takeaways

### Angular Architecture

- **Component-based**: Everything is a component
- **Standalone components**: No NgModule needed (modern Angular)
- **Services**: Shared logic via dependency injection
- **Routing**: Navigate between components
- **Signals**: Reactive state management

### Your Application

- ✅ Uses standalone components (modern)
- ✅ Uses inject() for DI (modern)
- ✅ Uses signals for state (modern)
- ✅ Uses OnPush change detection (performance)
- ✅ Route guards for protection
- ✅ Well-structured (core/features/shared)

### Best Practices

- ✅ Use signals for component state
- ✅ Use services for shared logic
- ✅ Use OnPush change detection
- ✅ Clean up subscriptions (takeUntilDestroyed)
- ✅ Type everything (TypeScript)
- ✅ Organize by feature (features/ directory)
- ❌ Don't put logic in templates
- ❌ Don't forget to unsubscribe

---

## What's Next?

Continue to `32-COMPONENT-ARCHITECTURE.md` for deep dive into components, templates, and data binding.

**Completed**:
- ✅ TypeScript essentials
- ✅ Angular fundamentals
- ✅ Application architecture
- ✅ Services and DI basics

**Next**:
- Component lifecycle in detail
- Template syntax
- Data binding
- Component interaction
- Change detection details
