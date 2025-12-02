# Angular Routing and Navigation

## Table of Contents
1. [Router Basics](#router-basics)
2. [Setting Up Routing](#setting-up-routing)
3. [Route Configuration](#route-configuration)
4. [Navigation](#navigation)
5. [Route Parameters](#route-parameters)
6. [Child Routes](#child-routes)
7. [Route Guards](#route-guards)
8. [Lazy Loading](#lazy-loading)
9. [Route Resolvers](#route-resolvers)
10. [Router Events](#router-events)
11. [Advanced Routing Patterns](#advanced-routing-patterns)

---

## Router Basics

The Angular Router enables navigation between views (components) based on the browser URL. It interprets URL paths and loads the appropriate component.

### Key Concepts

- **Routes**: Configurations that map URL paths to components
- **RouterOutlet**: Directive that acts as a placeholder for routed components
- **RouterLink**: Directive for declarative navigation
- **Router Service**: Service for programmatic navigation
- **ActivatedRoute**: Contains information about the current route

---

## Setting Up Routing

### 1. Import RouterModule

```typescript
// app.module.ts
import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { RouterModule, Routes } from '@angular/router';

import { AppComponent } from './app.component';
import { HomeComponent } from './home/home.component';
import { AboutComponent } from './about/about.component';

const routes: Routes = [
  { path: '', component: HomeComponent },
  { path: 'about', component: AboutComponent }
];

@NgModule({
  declarations: [
    AppComponent,
    HomeComponent,
    AboutComponent
  ],
  imports: [
    BrowserModule,
    RouterModule.forRoot(routes)  // Register routes
  ],
  bootstrap: [AppComponent]
})
export class AppModule { }
```

### 2. Add Router Outlet

```html
<!-- app.component.html -->
<nav>
  <a routerLink="/">Home</a>
  <a routerLink="/about">About</a>
</nav>

<!-- Routed component will be displayed here -->
<router-outlet></router-outlet>
```

### 3. Configure Base Href

```html
<!-- index.html -->
<!DOCTYPE html>
<html>
<head>
  <base href="/">  <!-- Required for routing -->
  <title>My App</title>
</head>
<body>
  <app-root></app-root>
</body>
</html>
```

---

## Route Configuration

### Basic Routes

```typescript
const routes: Routes = [
  // Default route
  { path: '', component: HomeComponent },

  // Simple route
  { path: 'about', component: AboutComponent },
  { path: 'contact', component: ContactComponent },

  // Route with title
  { path: 'blog', component: BlogComponent, title: 'Blog' },

  // Redirect
  { path: 'home', redirectTo: '', pathMatch: 'full' },

  // Wildcard route (404)
  { path: '**', component: NotFoundComponent }
];
```

### Route Order Matters

```typescript
const routes: Routes = [
  { path: '', component: HomeComponent },
  { path: 'posts', component: PostListComponent },
  { path: 'posts/:id', component: PostDetailComponent },
  { path: 'posts/new', component: PostCreateComponent },  // Won't match!
  { path: '**', component: NotFoundComponent }
];

// Correct order
const routes: Routes = [
  { path: '', component: HomeComponent },
  { path: 'posts', component: PostListComponent },
  { path: 'posts/new', component: PostCreateComponent },  // More specific first
  { path: 'posts/:id', component: PostDetailComponent },
  { path: '**', component: NotFoundComponent }
];
```

### Route with Data

```typescript
const routes: Routes = [
  {
    path: 'admin',
    component: AdminComponent,
    data: { role: 'admin', breadcrumb: 'Administration' }
  },
  {
    path: 'profile',
    component: ProfileComponent,
    data: { title: 'User Profile', showBackButton: true }
  }
];

// Access in component
export class AdminComponent implements OnInit {
  constructor(private route: ActivatedRoute) {}

  ngOnInit(): void {
    this.route.data.subscribe(data => {
      console.log('Route data:', data);
      console.log('Role:', data['role']);
    });
  }
}
```

---

## Navigation

### Declarative Navigation (routerLink)

```html
<!-- Basic navigation -->
<a routerLink="/">Home</a>
<a routerLink="/about">About</a>
<a routerLink="/posts">Posts</a>

<!-- Navigation with parameters -->
<a [routerLink]="['/posts', postId]">View Post</a>
<a [routerLink]="['/user', userId, 'profile']">User Profile</a>

<!-- Navigation with query parameters -->
<a [routerLink]="['/search']" [queryParams]="{ q: 'angular', page: 1 }">
  Search
</a>

<!-- Navigation with fragment -->
<a [routerLink]="['/article']" [fragment]="'comments'">
  Jump to Comments
</a>

<!-- Relative navigation -->
<a routerLink="../">Parent Route</a>
<a routerLink="./child">Child Route</a>

<!-- Active link styling -->
<a
  routerLink="/posts"
  routerLinkActive="active"
  [routerLinkActiveOptions]="{ exact: true }">
  Posts
</a>
```

### Programmatic Navigation (Router Service)

```typescript
import { Router, ActivatedRoute } from '@angular/router';

export class NavigationComponent {
  constructor(
    private router: Router,
    private route: ActivatedRoute
  ) {}

  // Navigate to a route
  goToHome(): void {
    this.router.navigate(['/']);
  }

  // Navigate with parameters
  goToPost(postId: number): void {
    this.router.navigate(['/posts', postId]);
  }

  // Navigate with query parameters
  search(query: string): void {
    this.router.navigate(['/search'], {
      queryParams: { q: query, page: 1 }
    });
  }

  // Navigate with query params (merge with existing)
  updateSearchParams(page: number): void {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { page },
      queryParamsHandling: 'merge'  // Preserve other query params
    });
  }

  // Navigate with fragment
  goToSection(section: string): void {
    this.router.navigate(['/article'], {
      fragment: section
    });
  }

  // Relative navigation
  goToChild(): void {
    this.router.navigate(['child'], {
      relativeTo: this.route
    });
  }

  // Navigate and replace history
  replaceUrl(): void {
    this.router.navigate(['/new-page'], {
      replaceUrl: true  // Don't add to history
    });
  }

  // Navigate by URL
  navigateByUrl(): void {
    this.router.navigateByUrl('/posts/123');
  }

  // Go back
  goBack(): void {
    window.history.back();
  }
}
```

### RouterLinkActive Directive

```html
<!-- Add class when route is active -->
<nav>
  <a routerLink="/" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }">
    Home
  </a>
  <a routerLink="/posts" routerLinkActive="active">
    Posts
  </a>
  <a routerLink="/about" routerLinkActive="active">
    About
  </a>
</nav>

<!-- Multiple classes -->
<a routerLink="/admin" [routerLinkActive]="['active', 'highlighted']">
  Admin
</a>

<!-- Custom logic -->
<a
  routerLink="/posts"
  routerLinkActive="active"
  #postsLink="routerLinkActive">
  Posts
  <span *ngIf="postsLink.isActive">✓</span>
</a>
```

```css
/* styles.css */
.active {
  color: blue;
  font-weight: bold;
  border-bottom: 2px solid blue;
}
```

---

## Route Parameters

### Route Parameters (/:param)

```typescript
// Route configuration
const routes: Routes = [
  { path: 'posts/:id', component: PostDetailComponent },
  { path: 'users/:userId/posts/:postId', component: UserPostComponent }
];
```

#### Reading Route Parameters

```typescript
import { ActivatedRoute, ParamMap } from '@angular/router';

export class PostDetailComponent implements OnInit {
  postId!: number;
  post?: Post;

  constructor(
    private route: ActivatedRoute,
    private postService: PostService
  ) {}

  ngOnInit(): void {
    // Method 1: Snapshot (one-time read)
    this.postId = Number(this.route.snapshot.paramMap.get('id'));
    this.loadPost(this.postId);

    // Method 2: Observable (reactive, handles parameter changes)
    this.route.paramMap.subscribe((params: ParamMap) => {
      this.postId = Number(params.get('id'));
      this.loadPost(this.postId);
    });

    // Method 3: Using params observable (deprecated but still works)
    this.route.params.subscribe(params => {
      this.postId = Number(params['id']);
      this.loadPost(this.postId);
    });
  }

  private loadPost(id: number): void {
    this.postService.getPost(id).subscribe(post => {
      this.post = post;
    });
  }
}
```

### Query Parameters (?param=value)

```typescript
// URL: /search?q=angular&page=2&sort=date
export class SearchComponent implements OnInit {
  searchQuery: string = '';
  page: number = 1;
  sort: string = 'date';

  constructor(private route: ActivatedRoute) {}

  ngOnInit(): void {
    // Method 1: Snapshot
    this.searchQuery = this.route.snapshot.queryParamMap.get('q') || '';
    this.page = Number(this.route.snapshot.queryParamMap.get('page')) || 1;

    // Method 2: Observable
    this.route.queryParamMap.subscribe(params => {
      this.searchQuery = params.get('q') || '';
      this.page = Number(params.get('page')) || 1;
      this.sort = params.get('sort') || 'date';
      this.performSearch();
    });

    // Method 3: Using queryParams (deprecated but still works)
    this.route.queryParams.subscribe(params => {
      this.searchQuery = params['q'] || '';
      this.page = Number(params['page']) || 1;
      this.performSearch();
    });
  }

  private performSearch(): void {
    console.log('Searching:', this.searchQuery, 'Page:', this.page);
  }
}
```

### Fragment (#section)

```typescript
// URL: /article#comments
export class ArticleComponent implements OnInit {
  constructor(private route: ActivatedRoute) {}

  ngOnInit(): void {
    // Snapshot
    const fragment = this.route.snapshot.fragment;
    if (fragment) {
      this.scrollToSection(fragment);
    }

    // Observable
    this.route.fragment.subscribe(fragment => {
      if (fragment) {
        this.scrollToSection(fragment);
      }
    });
  }

  private scrollToSection(section: string): void {
    const element = document.getElementById(section);
    if (element) {
      element.scrollIntoView({ behavior: 'smooth' });
    }
  }
}
```

### Required Route Parameters

```typescript
// Angular 15+ allows marking parameters as required
const routes: Routes = [
  {
    path: 'posts/:id',
    component: PostDetailComponent,
    // No way to mark as required in routing config
    // Validation should happen in component or guard
  }
];
```

---

## Child Routes

Child routes nest components within a parent component's router outlet.

### Configuration

```typescript
const routes: Routes = [
  {
    path: 'dashboard',
    component: DashboardComponent,
    children: [
      { path: '', redirectTo: 'overview', pathMatch: 'full' },
      { path: 'overview', component: OverviewComponent },
      { path: 'stats', component: StatsComponent },
      { path: 'settings', component: SettingsComponent }
    ]
  }
];
```

### Parent Component Template

```html
<!-- dashboard.component.html -->
<div class="dashboard">
  <aside class="sidebar">
    <nav>
      <a routerLink="overview" routerLinkActive="active">Overview</a>
      <a routerLink="stats" routerLinkActive="active">Statistics</a>
      <a routerLink="settings" routerLinkActive="active">Settings</a>
    </nav>
  </aside>

  <main class="content">
    <!-- Child routes render here -->
    <router-outlet></router-outlet>
  </main>
</div>
```

### Complex Nested Routes Example

```typescript
const routes: Routes = [
  {
    path: 'admin',
    component: AdminComponent,
    children: [
      { path: '', redirectTo: 'users', pathMatch: 'full' },
      {
        path: 'users',
        component: UsersComponent,
        children: [
          { path: '', component: UserListComponent },
          { path: ':id', component: UserDetailComponent },
          { path: ':id/edit', component: UserEditComponent }
        ]
      },
      {
        path: 'posts',
        component: PostsComponent,
        children: [
          { path: '', component: PostListComponent },
          { path: 'new', component: PostCreateComponent },
          { path: ':id', component: PostDetailComponent },
          { path: ':id/edit', component: PostEditComponent }
        ]
      }
    ]
  }
];

// URLs:
// /admin/users
// /admin/users/123
// /admin/users/123/edit
// /admin/posts
// /admin/posts/new
// /admin/posts/456
// /admin/posts/456/edit
```

---

## Route Guards

Guards control access to routes based on conditions.

### Types of Guards

1. **CanActivate**: Can the route be activated?
2. **CanActivateChild**: Can child routes be activated?
3. **CanDeactivate**: Can we navigate away from the route?
4. **CanLoad**: Can the module be loaded?
5. **Resolve**: Fetch data before activating the route

### CanActivate Guard

```typescript
// auth.guard.ts
import { Injectable } from '@angular/core';
import {
  ActivatedRouteSnapshot,
  CanActivate,
  Router,
  RouterStateSnapshot,
  UrlTree
} from '@angular/router';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class AuthGuard implements CanActivate {
  constructor(
    private authService: AuthService,
    private router: Router
  ) {}

  canActivate(
    route: ActivatedRouteSnapshot,
    state: RouterStateSnapshot
  ): Observable<boolean | UrlTree> | Promise<boolean | UrlTree> | boolean | UrlTree {
    // Check if user is authenticated
    if (this.authService.isAuthenticated()) {
      return true;
    }

    // Redirect to login
    return this.router.createUrlTree(['/login'], {
      queryParams: { returnUrl: state.url }
    });
  }
}

// Route configuration
const routes: Routes = [
  {
    path: 'admin',
    component: AdminComponent,
    canActivate: [AuthGuard]  // Apply guard
  }
];
```

### Role-Based Guard

```typescript
// role.guard.ts
@Injectable({ providedIn: 'root' })
export class RoleGuard implements CanActivate {
  constructor(
    private authService: AuthService,
    private router: Router
  ) {}

  canActivate(
    route: ActivatedRouteSnapshot,
    state: RouterStateSnapshot
  ): boolean | UrlTree {
    const expectedRole = route.data['role'];
    const userRole = this.authService.getUserRole();

    if (userRole === expectedRole) {
      return true;
    }

    // Redirect to unauthorized page
    return this.router.createUrlTree(['/unauthorized']);
  }
}

// Route configuration
const routes: Routes = [
  {
    path: 'admin',
    component: AdminComponent,
    canActivate: [AuthGuard, RoleGuard],
    data: { role: 'admin' }
  }
];
```

### CanActivateChild Guard

```typescript
@Injectable({ providedIn: 'root' })
export class AdminGuard implements CanActivateChild {
  constructor(private authService: AuthService) {}

  canActivateChild(
    childRoute: ActivatedRouteSnapshot,
    state: RouterStateSnapshot
  ): boolean {
    return this.authService.isAdmin();
  }
}

const routes: Routes = [
  {
    path: 'admin',
    component: AdminComponent,
    canActivateChild: [AdminGuard],  // Applies to all child routes
    children: [
      { path: 'users', component: UsersComponent },
      { path: 'posts', component: PostsComponent }
    ]
  }
];
```

### CanDeactivate Guard

```typescript
// unsaved-changes.guard.ts
export interface CanComponentDeactivate {
  canDeactivate: () => boolean | Observable<boolean> | Promise<boolean>;
}

@Injectable({ providedIn: 'root' })
export class UnsavedChangesGuard implements CanDeactivate<CanComponentDeactivate> {
  canDeactivate(
    component: CanComponentDeactivate,
    currentRoute: ActivatedRouteSnapshot,
    currentState: RouterStateSnapshot,
    nextState?: RouterStateSnapshot
  ): boolean | Observable<boolean> | Promise<boolean> {
    return component.canDeactivate ? component.canDeactivate() : true;
  }
}

// Component
export class PostEditComponent implements CanComponentDeactivate {
  hasUnsavedChanges: boolean = false;

  canDeactivate(): boolean {
    if (this.hasUnsavedChanges) {
      return confirm('You have unsaved changes. Are you sure you want to leave?');
    }
    return true;
  }
}

// Route configuration
const routes: Routes = [
  {
    path: 'posts/:id/edit',
    component: PostEditComponent,
    canDeactivate: [UnsavedChangesGuard]
  }
];
```

### Functional Guards (Angular 15+)

```typescript
// auth.guard.ts
import { inject } from '@angular/core';
import { Router } from '@angular/router';

export const authGuard = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isAuthenticated()) {
    return true;
  }

  return router.createUrlTree(['/login']);
};

// Route configuration
const routes: Routes = [
  {
    path: 'admin',
    component: AdminComponent,
    canActivate: [authGuard]
  }
];
```

---

## Lazy Loading

Lazy loading loads feature modules on demand, reducing initial bundle size.

### Feature Module Setup

```typescript
// posts/posts.module.ts
@NgModule({
  declarations: [
    PostListComponent,
    PostDetailComponent,
    PostCreateComponent
  ],
  imports: [
    CommonModule,
    RouterModule.forChild([  // forChild, not forRoot
      { path: '', component: PostListComponent },
      { path: 'new', component: PostCreateComponent },
      { path: ':id', component: PostDetailComponent }
    ])
  ]
})
export class PostsModule { }
```

### Main Routes Configuration

```typescript
// app-routing.module.ts
const routes: Routes = [
  { path: '', component: HomeComponent },
  {
    path: 'posts',
    loadChildren: () => import('./posts/posts.module').then(m => m.PostsModule)
  },
  {
    path: 'admin',
    loadChildren: () => import('./admin/admin.module').then(m => m.AdminModule),
    canLoad: [AuthGuard]  // Only load if guard passes
  }
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }
```

### Preloading Strategies

```typescript
// app-routing.module.ts
import { PreloadAllModules, RouterModule } from '@angular/router';

@NgModule({
  imports: [
    RouterModule.forRoot(routes, {
      preloadingStrategy: PreloadAllModules  // Preload all lazy modules
    })
  ],
  exports: [RouterModule]
})
export class AppRoutingModule { }
```

### Custom Preloading Strategy

```typescript
// custom-preload.strategy.ts
@Injectable({ providedIn: 'root' })
export class CustomPreloadStrategy implements PreloadingStrategy {
  preload(route: Route, load: () => Observable<any>): Observable<any> {
    // Only preload if route data has preload: true
    if (route.data && route.data['preload']) {
      console.log('Preloading:', route.path);
      return load();
    }
    return of(null);
  }
}

// Route configuration
const routes: Routes = [
  {
    path: 'posts',
    loadChildren: () => import('./posts/posts.module').then(m => m.PostsModule),
    data: { preload: true }  // Will be preloaded
  },
  {
    path: 'admin',
    loadChildren: () => import('./admin/admin.module').then(m => m.AdminModule),
    data: { preload: false }  // Won't be preloaded
  }
];

@NgModule({
  imports: [
    RouterModule.forRoot(routes, {
      preloadingStrategy: CustomPreloadStrategy
    })
  ]
})
export class AppRoutingModule { }
```

---

## Route Resolvers

Resolvers fetch data before activating a route.

### Creating a Resolver

```typescript
// post-resolver.service.ts
import { Injectable } from '@angular/core';
import {
  Resolve,
  ActivatedRouteSnapshot,
  RouterStateSnapshot
} from '@angular/router';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class PostResolver implements Resolve<Post> {
  constructor(private postService: PostService) {}

  resolve(
    route: ActivatedRouteSnapshot,
    state: RouterStateSnapshot
  ): Observable<Post> | Promise<Post> | Post {
    const postId = Number(route.paramMap.get('id'));
    return this.postService.getPost(postId);
  }
}

// Route configuration
const routes: Routes = [
  {
    path: 'posts/:id',
    component: PostDetailComponent,
    resolve: { post: PostResolver }  // Data will be available as 'post'
  }
];
```

### Using Resolved Data

```typescript
// post-detail.component.ts
export class PostDetailComponent implements OnInit {
  post!: Post;

  constructor(private route: ActivatedRoute) {}

  ngOnInit(): void {
    // Data is already loaded by resolver
    this.route.data.subscribe(data => {
      this.post = data['post'];
    });

    // Or using snapshot
    this.post = this.route.snapshot.data['post'];
  }
}
```

### Multiple Resolvers

```typescript
const routes: Routes = [
  {
    path: 'posts/:id',
    component: PostDetailComponent,
    resolve: {
      post: PostResolver,
      comments: CommentsResolver,
      author: AuthorResolver
    }
  }
];

// Component
export class PostDetailComponent implements OnInit {
  post!: Post;
  comments!: Comment[];
  author!: User;

  constructor(private route: ActivatedRoute) {}

  ngOnInit(): void {
    this.route.data.subscribe(data => {
      this.post = data['post'];
      this.comments = data['comments'];
      this.author = data['author'];
    });
  }
}
```

### Functional Resolver (Angular 15+)

```typescript
// post.resolver.ts
import { inject } from '@angular/core';
import { ActivatedRouteSnapshot } from '@angular/router';

export const postResolver = (route: ActivatedRouteSnapshot) => {
  const postService = inject(PostService);
  const postId = Number(route.paramMap.get('id'));
  return postService.getPost(postId);
};

// Route configuration
const routes: Routes = [
  {
    path: 'posts/:id',
    component: PostDetailComponent,
    resolve: { post: postResolver }
  }
];
```

---

## Router Events

Monitor navigation events for loading indicators, analytics, etc.

### Available Events

```typescript
import { Router, NavigationStart, NavigationEnd, NavigationError } from '@angular/router';

export class AppComponent implements OnInit {
  loading: boolean = false;

  constructor(private router: Router) {}

  ngOnInit(): void {
    this.router.events.subscribe(event => {
      if (event instanceof NavigationStart) {
        console.log('Navigation started:', event.url);
        this.loading = true;
      }

      if (event instanceof NavigationEnd) {
        console.log('Navigation ended:', event.url);
        this.loading = false;
        // Send analytics
        this.trackPageView(event.url);
      }

      if (event instanceof NavigationError) {
        console.error('Navigation error:', event.error);
        this.loading = false;
      }
    });
  }

  private trackPageView(url: string): void {
    // Analytics tracking
  }
}
```

### Complete Router Events

```typescript
import {
  Router,
  NavigationStart,
  NavigationEnd,
  NavigationCancel,
  NavigationError,
  RouteConfigLoadStart,
  RouteConfigLoadEnd,
  RoutesRecognized,
  GuardsCheckStart,
  GuardsCheckEnd,
  ResolveStart,
  ResolveEnd
} from '@angular/router';

export class RouterEventsComponent implements OnInit {
  constructor(private router: Router) {}

  ngOnInit(): void {
    this.router.events.subscribe(event => {
      if (event instanceof NavigationStart) {
        console.log('NavigationStart:', event);
      }

      if (event instanceof RouteConfigLoadStart) {
        console.log('Loading lazy module');
      }

      if (event instanceof RouteConfigLoadEnd) {
        console.log('Lazy module loaded');
      }

      if (event instanceof RoutesRecognized) {
        console.log('Routes recognized:', event);
      }

      if (event instanceof GuardsCheckStart) {
        console.log('Checking guards');
      }

      if (event instanceof GuardsCheckEnd) {
        console.log('Guards checked');
      }

      if (event instanceof ResolveStart) {
        console.log('Resolving data');
      }

      if (event instanceof ResolveEnd) {
        console.log('Data resolved');
      }

      if (event instanceof NavigationEnd) {
        console.log('NavigationEnd:', event);
      }

      if (event instanceof NavigationCancel) {
        console.log('NavigationCancel:', event);
      }

      if (event instanceof NavigationError) {
        console.log('NavigationError:', event);
      }
    });
  }
}
```

### Loading Indicator Example

```typescript
export class LoadingIndicatorComponent implements OnInit {
  loading: boolean = false;

  constructor(private router: Router) {}

  ngOnInit(): void {
    this.router.events
      .pipe(
        filter(event =>
          event instanceof NavigationStart ||
          event instanceof NavigationEnd ||
          event instanceof NavigationError
        )
      )
      .subscribe(event => {
        this.loading = event instanceof NavigationStart;
      });
  }
}
```

---

## Advanced Routing Patterns

### TheDispatch Blog Example

```typescript
// app-routing.module.ts
const routes: Routes = [
  // Home page
  { path: '', component: HomeComponent, title: 'TheDispatch - Home' },

  // Authentication routes
  {
    path: 'login',
    component: LoginComponent,
    canActivate: [GuestGuard],  // Redirect if already logged in
    title: 'Login'
  },
  {
    path: 'register',
    component: RegisterComponent,
    canActivate: [GuestGuard],
    title: 'Register'
  },

  // Posts routes
  {
    path: 'posts',
    children: [
      {
        path: '',
        component: PostListComponent,
        title: 'All Posts'
      },
      {
        path: 'new',
        component: PostCreateComponent,
        canActivate: [AuthGuard],
        title: 'Create Post'
      },
      {
        path: ':id',
        component: PostDetailComponent,
        resolve: { post: PostResolver },
        title: PostTitleResolver
      },
      {
        path: ':id/edit',
        component: PostEditComponent,
        canActivate: [AuthGuard],
        canDeactivate: [UnsavedChangesGuard],
        resolve: { post: PostResolver },
        title: 'Edit Post'
      }
    ]
  },

  // User profile
  {
    path: 'profile',
    component: ProfileComponent,
    canActivate: [AuthGuard],
    children: [
      { path: '', redirectTo: 'info', pathMatch: 'full' },
      { path: 'info', component: ProfileInfoComponent },
      { path: 'posts', component: ProfilePostsComponent },
      { path: 'settings', component: ProfileSettingsComponent }
    ]
  },

  // Admin area (lazy loaded)
  {
    path: 'admin',
    loadChildren: () => import('./admin/admin.module').then(m => m.AdminModule),
    canActivate: [AuthGuard, RoleGuard],
    data: { role: 'admin' }
  },

  // 404 Not Found
  { path: '**', component: NotFoundComponent, title: 'Page Not Found' }
];

@NgModule({
  imports: [
    RouterModule.forRoot(routes, {
      preloadingStrategy: PreloadAllModules,
      scrollPositionRestoration: 'enabled',  // Restore scroll position
      anchorScrolling: 'enabled',  // Enable fragment scrolling
      onSameUrlNavigation: 'reload'  // Reload on same URL navigation
    })
  ],
  exports: [RouterModule]
})
export class AppRoutingModule { }
```

### Navigation Bar Component

```html
<!-- navbar.component.html -->
<nav class="navbar">
  <div class="nav-brand">
    <a routerLink="/">TheDispatch</a>
  </div>

  <ul class="nav-menu">
    <li>
      <a routerLink="/" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }">
        Home
      </a>
    </li>
    <li>
      <a routerLink="/posts" routerLinkActive="active">
        Posts
      </a>
    </li>
    <li *ngIf="isAuthenticated">
      <a routerLink="/posts/new" routerLinkActive="active">
        Create Post
      </a>
    </li>
    <li *ngIf="isAuthenticated">
      <a routerLink="/profile" routerLinkActive="active">
        Profile
      </a>
    </li>
    <li *ngIf="isAdmin">
      <a routerLink="/admin" routerLinkActive="active">
        Admin
      </a>
    </li>
  </ul>

  <div class="nav-actions">
    <ng-container *ngIf="!isAuthenticated">
      <a routerLink="/login" class="btn btn-outline">Login</a>
      <a routerLink="/register" class="btn btn-primary">Register</a>
    </ng-container>
    <ng-container *ngIf="isAuthenticated">
      <button (click)="logout()" class="btn btn-outline">Logout</button>
    </ng-container>
  </div>
</nav>
```

### Breadcrumb Component

```typescript
// breadcrumb.component.ts
export class BreadcrumbComponent implements OnInit {
  breadcrumbs: Breadcrumb[] = [];

  constructor(
    private router: Router,
    private activatedRoute: ActivatedRoute
  ) {}

  ngOnInit(): void {
    this.router.events
      .pipe(filter(event => event instanceof NavigationEnd))
      .subscribe(() => {
        this.breadcrumbs = this.createBreadcrumbs(this.activatedRoute.root);
      });
  }

  private createBreadcrumbs(
    route: ActivatedRoute,
    url: string = '',
    breadcrumbs: Breadcrumb[] = []
  ): Breadcrumb[] {
    const children: ActivatedRoute[] = route.children;

    if (children.length === 0) {
      return breadcrumbs;
    }

    for (const child of children) {
      const routeURL: string = child.snapshot.url
        .map(segment => segment.path)
        .join('/');

      if (routeURL !== '') {
        url += `/${routeURL}`;
      }

      const label = child.snapshot.data['breadcrumb'];
      if (label) {
        breadcrumbs.push({ label, url });
      }

      return this.createBreadcrumbs(child, url, breadcrumbs);
    }

    return breadcrumbs;
  }
}
```

---

## Summary

Angular Router provides powerful navigation capabilities:

1. **Route Configuration** - Map URLs to components
2. **Navigation** - RouterLink for declarative, Router service for programmatic
3. **Route Parameters** - Path params, query params, fragments
4. **Child Routes** - Nest routes for complex layouts
5. **Guards** - Control access and navigation
6. **Lazy Loading** - Load modules on demand
7. **Resolvers** - Pre-fetch data before route activation
8. **Router Events** - Monitor navigation lifecycle

**Best Practices:**
- Use lazy loading for large feature modules
- Implement guards for authentication and authorization
- Use resolvers to ensure data is available before rendering
- Prefer declarative navigation (routerLink) when possible
- Order routes from most specific to least specific
- Use OnPush change detection with route observables
- Handle 404 routes with wildcard route

Understanding routing is essential for building multi-page Angular applications like TheDispatch blog platform.
