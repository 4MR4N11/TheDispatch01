# Angular Components Deep Dive

This document provides line-by-line explanations of Angular components, showing HOW they work, WHAT each piece does, and WHY architectural decisions were made. We'll cover component anatomy, template syntax, forms, and service integration.

---

## Table of Contents

1. [Component Anatomy](#component-anatomy)
2. [Line-by-Line: LoginComponent](#line-by-line-logincomponent)
3. [Line-by-Line: RegisterComponent](#line-by-line-registercomponent)
4. [Template Syntax](#template-syntax)
5. [Forms and Two-Way Binding](#forms-and-two-way-binding)
6. [Event Handling](#event-handling)
7. [Component-Service Integration](#component-service-integration)
8. [Error Handling Patterns](#error-handling-patterns)
9. [Utility Functions](#utility-functions)
10. [HomeComponent Overview](#homecomponent-overview)
11. [Best Practices](#best-practices)

---

## Component Anatomy

### Three Parts of a Component

Every Angular component consists of three files:

```
login.component.ts       # TypeScript logic
login.component.html     # HTML template
login.component.css      # Component-specific styles
```

### Component Decorator

```typescript
@Component({
  selector: 'app-login',              // HTML tag: <app-login></app-login>
  standalone: true,                   // No NgModule needed
  imports: [FormsModule, RouterLink], // Dependencies
  templateUrl: './login.component.html',  // Template file
  styleUrl: './login.component.css',       // Styles file
  changeDetection: ChangeDetectionStrategy.OnPush  // Performance optimization
})
export class LoginComponent {
  // Component logic
}
```

**Decorator properties:**

1. **selector** - Custom HTML element name
   - Used in templates: `<app-login></app-login>`
   - Convention: `app-` prefix

2. **standalone: true** - Modern Angular
   - No NgModule required
   - Component declares its own dependencies

3. **imports** - Component dependencies
   - FormsModule: ngModel, forms
   - RouterLink: Navigation
   - Other components, directives, pipes

4. **templateUrl** - Path to HTML template
   - Can use `template: 'inline HTML'` for small templates

5. **styleUrl** - Path to CSS file
   - Styles scoped to this component only
   - Can use `styles: ['css here']` for inline

6. **changeDetection** - How Angular detects changes
   - OnPush: Only check when inputs change or events fire
   - Improves performance

---

## Line-by-Line: LoginComponent

**File:** `frontend/src/app/features/auth/login/login.component.ts`

### Lines 1-8: Imports

```typescript
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';
import { NotificationService } from '../../../core/services/notification.service';
import { ErrorHandler } from '../../../core/utils/error-handler';
```

**What's imported:**

1. **Angular core:**
   - `Component` - Component decorator
   - `inject` - Dependency injection function
   - `signal` - Reactive state management
   - `ChangeDetectionStrategy` - Performance optimization

2. **FormsModule:**
   - Enables `ngModel` for two-way binding
   - Handles form inputs

3. **Router:**
   - `Router` - Programmatic navigation
   - `RouterLink` - Template navigation directive

4. **Services:**
   - `AuthService` - Authentication logic
   - `NotificationService` - Toast notifications

5. **Utils:**
   - `ErrorHandler` - Extract user-friendly error messages

### Lines 9-16: Component Decorator

```typescript
@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
```

**ChangeDetectionStrategy.OnPush:**
- Only check component when:
  - Input properties change
  - Events fire (button clicks, etc.)
  - Signals update
  - Observables emit (with async pipe)
- Skips unnecessary checks
- Better performance

### Lines 17-26: Component Class

```typescript
export class LoginComponent {
  protected readonly usernameOrEmail = signal('');
  protected readonly password = signal('');
  protected readonly loading = signal(false);
  protected readonly errorMessage = signal<string | null>(null);

  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly notificationService = inject(NotificationService);
}
```

**Signals for state:**
```typescript
protected readonly usernameOrEmail = signal('');
```

**Why signals?**
- Reactive: template auto-updates when signal changes
- Precise: only this component checks for updates
- Type-safe: TypeScript enforces types

**protected vs private:**
- `protected` - Accessible in template
- `private` - Only in component class

**Why readonly?**
- Signal reference shouldn't change
- Can still call `.set()` or `.update()` on signal
- Prevents accidental reassignment: `loading = signal(true)` ❌

**Service injection:**
```typescript
private readonly authService = inject(AuthService);
```

**Modern inject() function:**
- Cleaner than constructor injection
- Type inference automatic
- Can inject anywhere in injection context

### Lines 27-52: Login Method

```typescript
login() {
  if (!this.usernameOrEmail() || !this.password()) {
    this.errorMessage.set('Please enter username/email and password');
    return;
  }

  this.loading.set(true);
  this.errorMessage.set(null);

  this.authService.login({
    usernameOrEmail: this.usernameOrEmail(),
    password: this.password()
  }).subscribe({
    next: () => {
      this.loading.set(false);
      this.notificationService.success('Login successful');
      this.router.navigate(['/home']);
    },
    error: (err) => {
      this.loading.set(false);
      const message = ErrorHandler.getAuthErrorMessage(err);
      this.errorMessage.set(message);
      this.notificationService.error(message);
    }
  });
}
```

**Step-by-step breakdown:**

#### Validation (Lines 28-31)

```typescript
if (!this.usernameOrEmail() || !this.password()) {
  this.errorMessage.set('Please enter username/email and password');
  return;
}
```

**Client-side validation:**
- Check required fields
- Prevent unnecessary API calls
- Immediate feedback to user

**Reading signals:**
```typescript
this.usernameOrEmail()  // Call signal as function to get value
```

**Setting signals:**
```typescript
this.errorMessage.set('error message')  // Update signal value
```

#### Loading State (Lines 33-34)

```typescript
this.loading.set(true);
this.errorMessage.set(null);
```

**Why manage loading state?**
1. Disable form inputs (prevent duplicate submissions)
2. Show loading spinner
3. Better UX

**Clear previous errors:**
- Reset errorMessage to null
- Clean slate for new attempt

#### Call AuthService (Lines 36-39)

```typescript
this.authService.login({
  usernameOrEmail: this.usernameOrEmail(),
  password: this.password()
}).subscribe({
```

**Observable pattern:**
1. `login()` returns Observable
2. Nothing happens until `.subscribe()`
3. Subscribe executes the HTTP request
4. Observable emits response or error

**Why observables?**
- Lazy: doesn't execute until subscribed
- Cancellable: unsubscribe to cancel request
- Operators: transform data before handling

#### Success Handler (Lines 40-44)

```typescript
next: () => {
  this.loading.set(false);
  this.notificationService.success('Login successful');
  this.router.navigate(['/home']);
},
```

**On successful login:**

1. **Stop loading:**
   ```typescript
   this.loading.set(false);
   ```

2. **Show success notification:**
   ```typescript
   this.notificationService.success('Login successful');
   ```
   - Toast notification appears
   - Auto-dismisses after 5 seconds

3. **Navigate to home:**
   ```typescript
   this.router.navigate(['/home']);
   ```
   - Programmatic navigation
   - User sees home page
   - AuthGuard allows access (user now logged in)

#### Error Handler (Lines 45-50)

```typescript
error: (err) => {
  this.loading.set(false);
  const message = ErrorHandler.getAuthErrorMessage(err);
  this.errorMessage.set(message);
  this.notificationService.error(message);
}
```

**On login failure:**

1. **Stop loading:**
   - Re-enable form

2. **Extract error message:**
   ```typescript
   const message = ErrorHandler.getAuthErrorMessage(err);
   ```
   - Converts backend error to user-friendly message
   - "Invalid credentials" instead of "401 Unauthorized"

3. **Display error:**
   - Set errorMessage signal (shows in template)
   - Show toast notification

**Why both errorMessage and notification?**
- errorMessage: stays visible in form
- notification: temporary toast
- Belt and suspenders approach

---

## Line-by-Line: RegisterComponent

**File:** `frontend/src/app/features/auth/register/register.component.ts`

### Lines 18-30: Component State

```typescript
protected readonly firstname = signal('');
protected readonly lastname = signal('');
protected readonly username = signal('');
protected readonly email = signal('');
protected readonly password = signal('');
protected readonly avatar = signal('');
protected readonly loading = signal(false);
protected readonly errorMessage = signal<string | null>(null);
protected readonly selectedFile = signal<File | null>(null);
private readonly authService = inject(AuthService);
private readonly router = inject(Router);
private readonly notificationService = inject(NotificationService);
```

**More signals than LoginComponent:**
- All form fields as signals
- Reactive state for entire form
- `selectedFile` for avatar upload

### Lines 32-75: Register Method

```typescript
register() {
  // Basic validation
  if (!this.firstname() || !this.lastname() || !this.username() ||
      !this.email() || !this.password()) {
    this.errorMessage.set('All fields except avatar are required');
    return;
  }

  // Validate email format
  if (!isValidEmail(this.email())) {
    this.errorMessage.set('Please enter a valid email address');
    return;
  }

  // Validate password strength
  const passwordValidation = validatePassword(this.password());
  if (!passwordValidation.isValid) {
    this.errorMessage.set(passwordValidation.errors[0]);
    return;
  }

  this.loading.set(true);
  this.errorMessage.set(null);

  this.authService.register({
    firstname: this.firstname(),
    lastname: this.lastname(),
    username: this.username(),
    email: this.email(),
    password: this.password(),
    avatar: this.avatar()
  }).subscribe({
    next: () => {
      this.loading.set(false);
      this.notificationService.success('Registration successful');
      this.router.navigate(['/home']);
    },
    error: (err) => {
      this.loading.set(false);
      const message = ErrorHandler.getRegistrationErrorMessage(err);
      this.errorMessage.set(message);
      this.notificationService.error(message);
    }
  });
}
```

**Enhanced validation:**

#### Required Fields (Lines 34-37)

```typescript
if (!this.firstname() || !this.lastname() || !this.username() ||
    !this.email() || !this.password()) {
  this.errorMessage.set('All fields except avatar are required');
  return;
}
```

**Why check in frontend?**
- Immediate feedback (no API call)
- Better UX
- Still validated in backend (never trust client)

#### Email Validation (Lines 39-42)

```typescript
if (!isValidEmail(this.email())) {
  this.errorMessage.set('Please enter a valid email address');
  return;
}
```

**isValidEmail utility:**
```typescript
export function isValidEmail(email: string): boolean {
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  return emailRegex.test(email);
}
```

**Simple regex check:**
- `[^\s@]+` - One or more non-whitespace, non-@ characters
- `@` - Literal @ symbol
- `[^\s@]+` - Domain name
- `\.` - Literal dot
- `[^\s@]+` - TLD (com, org, etc.)

**Catches:**
- Missing @ symbol
- Missing domain
- Missing TLD

#### Password Validation (Lines 44-48)

```typescript
const passwordValidation = validatePassword(this.password());
if (!passwordValidation.isValid) {
  this.errorMessage.set(passwordValidation.errors[0]);
  return;
}
```

**validatePassword utility:**
```typescript
export function validatePassword(password: string): PasswordValidationResult {
  const errors: string[] = [];

  if (password.length < 8) {
    errors.push('Password must be at least 8 characters');
  }

  if (!/[A-Z]/.test(password)) {
    errors.push('Password must contain at least one uppercase letter');
  }

  if (!/[a-z]/.test(password)) {
    errors.push('Password must contain at least one lowercase letter');
  }

  if (!/[0-9]/.test(password)) {
    errors.push('Password must contain at least one number');
  }

  if (!/[!@#$%^&*(),.?":{}|<>]/.test(password)) {
    errors.push('Password must contain at least one special character');
  }

  return {
    isValid: errors.length === 0,
    errors
  };
}
```

**Strong password requirements:**
1. Minimum 8 characters
2. At least one uppercase letter (A-Z)
3. At least one lowercase letter (a-z)
4. At least one digit (0-9)
5. At least one special character

**Matches backend validation:**
- Frontend and backend enforce same rules
- Consistent user experience
- Backend is final authority (frontend can be bypassed)

---

## Template Syntax

### Angular Template Basics

**File:** `frontend/src/app/features/auth/login/login.component.html`

### @if Directive (New in Angular 17)

```html
@if (errorMessage()) {
  <div class="error-alert">
    {{ errorMessage() }}
  </div>
}
```

**How it works:**
1. Evaluates condition: `errorMessage()`
2. Signal returns string or null
3. Truthy (has value) → render div
4. Falsy (null) → don't render

**Old syntax (still works):**
```html
<div *ngIf="errorMessage()" class="error-alert">
  {{ errorMessage() }}
</div>
```

**Why new @if syntax?**
- Cleaner, more intuitive
- Better TypeScript integration
- Matches other frameworks

### @else

```html
@if (loading()) {
  <span class="spinner"></span> Signing in...
} @else {
  <svg>...</svg>
  Sign In
}
```

**Conditional rendering:**
- If loading → show spinner
- Else → show icon

### Interpolation {{ }}

```html
<div class="error-alert">
  {{ errorMessage() }}
</div>
```

**Double curly braces:**
- Evaluate TypeScript expression
- Convert to string
- Insert into DOM

**Examples:**
```html
{{ username() }}                    <!-- Signal value -->
{{ post.title }}                    <!-- Object property -->
{{ items.length }}                  <!-- Array length -->
{{ 2 + 2 }}                         <!-- Expression -->
{{ getFullName() }}                 <!-- Method call -->
{{ user ? user.name : 'Guest' }}    <!-- Ternary -->
```

### Property Binding [property]

```html
<input
  type="text"
  [ngModel]="usernameOrEmail()"
  [disabled]="loading()"
  required />
```

**Square brackets = property binding:**
- Bind component property to DOM property
- One-way: component → template

**[ngModel]:**
- Binds input value to signal
- Updates input when signal changes

**[disabled]:**
- Binds disabled attribute
- `loading() = true` → input disabled
- `loading() = false` → input enabled

**Without brackets:**
```html
<input disabled="true">  <!-- Always disabled -->
<input [disabled]="loading()">  <!-- Dynamic -->
```

### Event Binding (event)

```html
<input
  (ngModelChange)="usernameOrEmail.set($event)"
  (input)="handleInput($event)"
  (focus)="onFocus()"
  (blur)="onBlur()" />
```

**Parentheses = event binding:**
- Listen to DOM events
- Call component method

**(ngModelChange):**
- Fires when input value changes
- `$event` contains new value
- Update signal with new value

**Common events:**
```html
(click)="handleClick()"                 <!-- Button click -->
(submit)="onSubmit()"                   <!-- Form submit -->
(input)="onInput($event)"              <!-- Input change -->
(keyup.enter)="onEnter()"              <!-- Enter key -->
(mouseenter)="onHover()"               <!-- Mouse hover -->
```

### Two-Way Binding [(ngModel)]

**Shorthand for property + event binding:**

```html
<!-- Full syntax -->
<input
  [ngModel]="usernameOrEmail()"
  (ngModelChange)="usernameOrEmail.set($event)" />

<!-- Shorthand (banana in a box) -->
<input [(ngModel)]="usernameOrEmail" />
```

**Why "banana in a box"?**
- `[()]` looks like banana `()` in a box `[]`
- Combines property `[]` and event `()` binding

**With signals:**
```html
<!-- Can't use [(ngModel)] directly with signals -->
<!-- Must use property + event binding -->
<input
  [ngModel]="usernameOrEmail()"
  (ngModelChange)="usernameOrEmail.set($event)" />
```

### Template Reference Variables #var

```html
<input #usernameInput type="text" />
<button (click)="focusInput(usernameInput)">Focus</button>
```

**#usernameInput:**
- Creates reference to DOM element
- Can pass to methods
- Access in template expressions

**Usage:**
```typescript
focusInput(input: HTMLInputElement) {
  input.focus();
  console.log('Current value:', input.value);
}
```

---

## Forms and Two-Way Binding

### How ngModel Works

**Template:**
```html
<input
  type="text"
  [ngModel]="username()"
  (ngModelChange)="username.set($event)"
  name="username" />
```

**Flow:**

```
1. User types "alice"
   ↓
2. (ngModelChange) event fires
   ↓
3. $event = "alice"
   ↓
4. username.set("alice") is called
   ↓
5. Signal updates: username = signal("alice")
   ↓
6. [ngModel]="username()" reads new value
   ↓
7. Input value updates (stays in sync)
```

### FormsModule

**Required for ngModel:**
```typescript
@Component({
  imports: [FormsModule]  // ← Must import
})
```

**Without FormsModule:**
- `ngModel` directive not available
- Error: "Can't bind to 'ngModel'"

### Form Submission

```html
<form (submit)="login(); $event.preventDefault()">
  <!-- Form fields -->
  <button type="submit">Sign In</button>
</form>
```

**(submit) event:**
- Fires when form submits
- Triggered by: button click, Enter key

**$event.preventDefault():**
- Prevents default form submission
- Default = page reload
- We handle submission with JavaScript

**Semicolon syntax:**
```html
(submit)="login(); $event.preventDefault()"
```
- Execute both statements
- First login(), then preventDefault()

---

## Event Handling

### Click Events

```html
<button (click)="deletePost(post.id, $event)">Delete</button>
```

**Pass event object:**
```typescript
deletePost(postId: number, event: Event) {
  event.stopPropagation();  // Stop event bubbling
  // Delete logic
}
```

**Why stopPropagation?**
```html
<div (click)="viewPost(post.id)">
  <button (click)="deletePost(post.id, $event)">Delete</button>
</div>
```

Without `stopPropagation()`:
1. Button click fires `deletePost`
2. Event bubbles up to div
3. Div click fires `viewPost`
4. Post deleted AND navigated away!

With `stopPropagation()`:
1. Button click fires `deletePost`
2. Event stopped
3. Div click doesn't fire
4. Only post deleted

### Event Modifiers

```html
<!-- Only fire on Enter key -->
<input (keyup.enter)="search()" />

<!-- Only fire on Escape -->
<input (keyup.escape)="cancel()" />

<!-- Prevent default -->
<form (submit)="onSubmit(); $event.preventDefault()">
```

---

## Component-Service Integration

### The Flow

```
Component
   ↓ inject()
Service
   ↓ HttpClient
Backend API
   ↓ Response
Service
   ↓ Observable
Component
   ↓ subscribe()
Handle Response
```

### Login Example

**1. Component injects service:**
```typescript
export class LoginComponent {
  private readonly authService = inject(AuthService);
}
```

**2. Component calls service method:**
```typescript
this.authService.login({
  usernameOrEmail: this.usernameOrEmail(),
  password: this.password()
})
```

**3. Service makes HTTP request:**
```typescript
// In AuthService
login(request: LoginRequest): Observable<AuthResponse> {
  return this.http.post<AuthResponse>('/auth/login', request, {
    withCredentials: true
  });
}
```

**4. Component subscribes to observable:**
```typescript
.subscribe({
  next: (response) => {
    // Handle success
  },
  error: (err) => {
    // Handle error
  }
})
```

**5. Service updates shared state:**
```typescript
// In AuthService
tap(response => {
  this.isLoggedIn.set(true);      // Signal update
  this.currentUser.set(user);     // Shared state
})
```

**6. Other components react:**
```typescript
// In NavbarComponent
readonly isLoggedIn = this.authService.isLoggedIn;  // Reference signal

// Template
@if (isLoggedIn()) {
  <button>Logout</button>
}
```

### Observable Pattern Benefits

**Lazy execution:**
```typescript
const login$ = this.authService.login(request);  // Nothing happens yet!
login$.subscribe();  // NOW the HTTP request executes
```

**Cancellable:**
```typescript
const subscription = this.http.get('/slow-endpoint').subscribe();
// User navigates away
subscription.unsubscribe();  // Cancel request
```

**Transformable:**
```typescript
this.http.get<Post[]>('/posts').pipe(
  map(posts => posts.filter(p => !p.hidden)),  // Transform
  tap(posts => console.log('Loaded:', posts))   // Side effect
).subscribe(posts => this.posts.set(posts));
```

---

## Error Handling Patterns

### ErrorHandler Utility

**File:** `frontend/src/app/core/utils/error-handler.ts`

```typescript
export class ErrorHandler {
  static getErrorMessage(error: any, defaultMessage: string): string {
    // Check for backend error message
    if (error.error?.error) {
      return error.error.error;
    }

    if (error.error?.message) {
      return error.error.message;
    }

    // Handle HTTP status codes
    switch (error.status) {
      case 0:
        return 'Unable to connect to server';
      case 401:
        return 'Session expired. Please login again.';
      case 403:
        return this.get403Message(error);
      case 404:
        return 'Resource not found';
      case 500:
        return 'Server error. Try again later.';
      default:
        return defaultMessage;
    }
  }
}
```

**Why centralize error handling?**

1. **Consistency** - Same error messages everywhere
2. **User-friendly** - Convert tech errors to plain English
3. **Maintainability** - Change messages in one place
4. **Context-aware** - Different messages for different endpoints

### Context-Aware Error Messages

```typescript
private static get403Message(error: any): string {
  const url = error.url || '';

  if (url.includes('/admin')) {
    return 'Admin privileges required';
  }

  if (url.includes('/post') && error.method === 'DELETE') {
    return 'You can only delete your own posts';
  }

  return 'Access denied';
}
```

**Better UX:**
- Generic: "403 Forbidden"
- Specific: "You can only delete your own posts"

### Error Handling in Components

**Pattern 1: Display in template**
```typescript
.subscribe({
  error: (err) => {
    this.errorMessage.set(ErrorHandler.getAuthErrorMessage(err));
  }
})
```

```html
@if (errorMessage()) {
  <div class="error-alert">{{ errorMessage() }}</div>
}
```

**Pattern 2: Toast notification**
```typescript
.subscribe({
  error: (err) => {
    const message = ErrorHandler.getErrorMessage(err);
    this.notificationService.error(message);
  }
})
```

**Pattern 3: Both (belt and suspenders)**
```typescript
.subscribe({
  error: (err) => {
    const message = ErrorHandler.getAuthErrorMessage(err);
    this.errorMessage.set(message);           // In-form error
    this.notificationService.error(message);  // Toast
  }
})
```

---

## Utility Functions

### Format Utils

**File:** `frontend/src/app/shared/utils/format.util.ts`

#### Time Ago

```typescript
export function getTimeAgo(date: string | Date): string {
  const now = new Date();
  const past = new Date(date);
  const diffInSeconds = Math.floor((now.getTime() - past.getTime()) / 1000);

  if (diffInSeconds < 60) return 'just now';

  const diffInMinutes = Math.floor(diffInSeconds / 60);
  if (diffInMinutes < 60) return `${diffInMinutes} minute${diffInMinutes > 1 ? 's' : ''} ago`;

  const diffInHours = Math.floor(diffInMinutes / 60);
  if (diffInHours < 24) return `${diffInHours} hour${diffInHours > 1 ? 's' : ''} ago`;

  const diffInDays = Math.floor(diffInHours / 24);
  if (diffInDays < 7) return `${diffInDays} day${diffInDays > 1 ? 's' : ''} ago`;

  // ... weeks, months, years
}
```

**Usage:**
```typescript
getTimeAgo('2024-01-15T10:30:00Z')  // "2 hours ago"
```

**Why relative time?**
- More intuitive than absolute timestamps
- "2 hours ago" vs "2024-01-15 10:30:00"
- Common in social apps

#### Avatar URL Builder

```typescript
export function getAvatarUrl(avatar: string | null | undefined): string {
  if (!avatar) return '';

  if (avatar.startsWith('http://') || avatar.startsWith('https://')) {
    return avatar;  // Already full URL
  }

  if (avatar.startsWith('/uploads/')) {
    return `${environment.apiUrl}${avatar}`;  // Add backend URL
  }

  return `${environment.apiUrl}/uploads/${avatar}`;
}
```

**Handles multiple formats:**
```typescript
getAvatarUrl('image.jpg')              // → http://localhost:8080/uploads/image.jpg
getAvatarUrl('/uploads/image.jpg')     // → http://localhost:8080/uploads/image.jpg
getAvatarUrl('https://cdn.com/img.jpg') // → https://cdn.com/img.jpg
```

#### Excerpt Generator

```typescript
export function getExcerpt(content: string, maxLength: number = 150): string {
  if (!content) return '';

  // ✅ SECURITY: Use DOMParser instead of innerHTML
  const parser = new DOMParser();
  const doc = parser.parseFromString(content, 'text/html');
  const stripped = doc.body.textContent || '';

  if (stripped.length <= maxLength) {
    return stripped;
  }

  return stripped.substring(0, maxLength).trim() + '...';
}
```

**Why DOMParser?**
- **Secure:** Doesn't execute scripts
- **Safe:** No XSS risk
- **Clean:** Strips HTML tags

**Without DOMParser (DANGEROUS):**
```typescript
// ❌ DON'T DO THIS
const div = document.createElement('div');
div.innerHTML = content;  // XSS risk!
const text = div.textContent;
```

**Problem:**
```javascript
content = '<img src=x onerror="alert(\'XSS\')">';
div.innerHTML = content;  // Script executes!
```

**With DOMParser (SAFE):**
```typescript
const doc = parser.parseFromString(content, 'text/html');
// Script doesn't execute, only parsed
```

---

## HomeComponent Overview

**File:** `frontend/src/app/features/home/home.ts`

### Component Structure

```typescript
@Component({
  selector: 'app-home',
  standalone: true,
  imports: [
    CommonModule,          // ngIf, ngFor, pipes
    FormsModule,           // ngModel
    MatIconModule,         // Material icons
    NewPostModalComponent, // Custom components
    EditPostModalComponent
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HomeComponent {
  // Services
  private readonly apiService = inject(ApiService);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly notificationService = inject(NotificationService);
  private readonly sanitizer = inject(DomSanitizer);

  // State
  protected readonly posts = signal<PostResponse[]>([]);
  protected readonly currentUser = this.authService.currentUser;
  protected readonly loading = signal(true);
  protected readonly showNewPostModal = signal(false);
  protected readonly selectedPost = signal<PostResponse | null>(null);

  constructor() {
    this.loadFeed();  // Load data on init
  }
}
```

### Key Features

**1. Feed Loading:**
```typescript
private loadFeed() {
  this.loading.set(true);
  this.apiService.getFeed().subscribe({
    next: (posts) => {
      this.posts.set(posts);
      this.loading.set(false);
    },
    error: (error) => {
      this.notificationService.error(ErrorHandler.getErrorMessage(error));
      this.loading.set(false);
    }
  });
}
```

**2. Like/Unlike:**
```typescript
toggleLike(postId: number, event: Event) {
  event.stopPropagation();  // Don't trigger post click

  const post = this.posts().find(p => p.id === postId);
  if (!post) return;

  const currentUsername = this.authService.currentUser()?.username;
  const isLiked = post.likedByUsernames?.includes(currentUsername);

  if (isLiked) {
    this.apiService.unlikePost(postId).subscribe(() => this.loadFeed());
  } else {
    this.apiService.likePost(postId).subscribe(() => this.loadFeed());
  }
}
```

**3. Content Parsing:**
```typescript
getPostHTML(content: string): SafeHtml | null {
  try {
    const contentJSON = JSON.parse(content);  // Editor.js JSON
    const edjsParser = edjsHTML();
    const html = edjsParser.parse(contentJSON);
    return this.sanitizer.bypassSecurityTrustHtml(html);
  } catch (err) {
    // Fallback for plain text
    return this.sanitizer.bypassSecurityTrustHtml(`<p>${content}</p>`);
  }
}
```

**4. Navigation:**
```typescript
viewPost(id: number, event?: Event) {
  if (event) event.stopPropagation();
  this.router.navigate(['/post', id]);
}

viewProfile(username: string) {
  this.router.navigate(['/profile', username]);
}
```

---

## Best Practices

### 1. Use Signals for Component State

**✅ Good:**
```typescript
export class MyComponent {
  readonly count = signal(0);
  readonly loading = signal(false);
}
```

**❌ Bad:**
```typescript
export class MyComponent {
  count = 0;  // Plain property, requires manual change detection
  loading = false;
}
```

### 2. Use OnPush Change Detection

**✅ Good:**
```typescript
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush
})
```

**Why?**
- Only checks when necessary
- Better performance
- Forces better patterns (immutability)

### 3. Inject Services with inject()

**✅ Good:**
```typescript
export class MyComponent {
  private readonly apiService = inject(ApiService);
}
```

**❌ Old way:**
```typescript
export class MyComponent {
  constructor(private apiService: ApiService) {}
}
```

### 4. Use protected for Template-Accessible Properties

**✅ Good:**
```typescript
export class MyComponent {
  protected readonly posts = signal([]);  // Used in template
  private readonly apiService = inject(ApiService);  // Not used in template
}
```

### 5. Always Handle Observable Errors

**✅ Good:**
```typescript
this.http.get('/posts').subscribe({
  next: (posts) => this.posts.set(posts),
  error: (err) => this.notificationService.error(ErrorHandler.getErrorMessage(err))
});
```

**❌ Bad:**
```typescript
this.http.get('/posts').subscribe(posts => {
  this.posts.set(posts);
  // Error not handled!
});
```

### 6. Use readonly for Signals

**✅ Good:**
```typescript
protected readonly count = signal(0);
// Can still do: this.count.set(5)
```

**❌ Bad:**
```typescript
protected count = signal(0);
// Might accidentally do: this.count = signal(10) ❌
```

### 7. Stop Event Propagation When Needed

**✅ Good:**
```typescript
deletePost(id: number, event: Event) {
  event.stopPropagation();  // Prevent parent clicks
  // Delete logic
}
```

### 8. Use ErrorHandler for User-Friendly Messages

**✅ Good:**
```typescript
error: (err) => {
  const message = ErrorHandler.getAuthErrorMessage(err);
  this.notificationService.error(message);
}
```

**❌ Bad:**
```typescript
error: (err) => {
  this.notificationService.error(err.message);  // Technical error
}
```

### 9. Validate on Frontend AND Backend

**✅ Good:**
```typescript
// Frontend validation
if (!isValidEmail(this.email())) {
  this.errorMessage.set('Invalid email');
  return;
}

// Still validated on backend
this.authService.register(data).subscribe();
```

**Why both?**
- Frontend: Immediate feedback, better UX
- Backend: Security, can't be bypassed

### 10. Use Type-Safe Models

**✅ Good:**
```typescript
this.http.get<PostResponse[]>('/posts')  // Type-safe
  .subscribe(posts => {
    console.log(posts[0].title);  // TypeScript knows 'title' exists
  });
```

**❌ Bad:**
```typescript
this.http.get('/posts')  // any type
  .subscribe(posts => {
    console.log(posts[0].title);  // No type checking
  });
```

---

## Key Takeaways

### Component Anatomy

- **Three files:** .ts, .html, .css
- **@Component decorator:** Configuration
- **Class:** Logic and state
- **Template:** HTML with Angular syntax
- **Styles:** Component-scoped CSS

### Modern Angular Features

- **Standalone:** No NgModules
- **Signals:** Reactive state
- **inject():** Clean DI
- **OnPush:** Performance
- **@if/@else:** Cleaner conditionals

### Template Syntax

- **{{ }}:** Interpolation
- **[property]:** Property binding
- **(event):** Event binding
- **[(ngModel)]:** Two-way binding
- **@if/@else:** Conditionals

### Patterns

- **Signal state:** Reactive, precise updates
- **Service injection:** Shared logic, HTTP
- **Observable pattern:** Async operations
- **Error handling:** User-friendly messages
- **Utility functions:** DRY, reusable

---

**Next**: Angular Services Deep Dive - AuthService, ApiService, NotificationService line-by-line, and how frontend/backend integration works! Continue?
