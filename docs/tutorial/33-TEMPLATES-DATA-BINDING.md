# Templates and Data Binding

## Overview

This document explains **Angular templates** and **data binding** using examples from your Dispatch application.

**What you'll learn**:
- Modern template syntax (@if, @for, @else)
- Interpolation {{ }}
- Property binding [property]
- Event binding (event)
- Two-way binding [(ngModel)]
- Template reference variables
- Pipes for data transformation
- Safe navigation operator

**Your app**: Uses Angular 20's modern control flow (@if/@for)

---

## 1. Template Syntax Overview

### What is a Template?

**Template** = HTML with Angular-specific syntax

```typescript
// Component
@Component({
  selector: 'app-post-detail',
  templateUrl: './posts.component.html'  // ← External template
  // OR
  template: `<div>{{ title }}</div>`     // ← Inline template
})
```

**Template types**:
- **Inline**: Small templates (< 3 lines)
- **External**: Larger templates (your app uses this)

---

## 2. Interpolation {{ }}

### Basic Interpolation

**Interpolation** = Display component data in template

```html
<!-- Your template -->
<h1 class="post-detail-title">{{ p.title }}</h1>
<div class="author-name">{{ p.author }}</div>
<span>{{ p.likeCount }}</span>
```

**Component**:
```typescript
export class PostDetailComponent {
  protected readonly post = signal<PostResponse | null>(null);
}
```

**Result** (when post.title = "My First Post"):
```html
<h1 class="post-detail-title">My First Post</h1>
```

### Interpolation with Expressions

```html
<!-- Simple expressions -->
<span>{{ 2 + 2 }}</span>  <!-- 4 -->
<span>{{ title.toUpperCase() }}</span>  <!-- HELLO -->
<span>{{ items.length }}</span>  <!-- 5 -->

<!-- Method calls -->
<span>{{ getTimeAgo(p.created_at) }}</span>
<span>{{ getUserInitial(p.author) }}</span>

<!-- Ternary operator -->
<span>{{ isLiked() ? 'Unlike' : 'Like' }}</span>
<span>{{ count > 10 ? '10+' : count }}</span>

<!-- Your template example -->
<span>{{ unreadCount() > 9 ? '9+' : unreadCount() }}</span>
```

**What you CAN'T do** in interpolation:
```html
<!-- ❌ Assignments -->
<span>{{ count = 5 }}</span>

<!-- ❌ New/typeof/instanceof -->
<span>{{ new Date() }}</span>

<!-- ❌ Chaining expressions with ; -->
<span>{{ count++; total++ }}</span>

<!-- ❌ Global variables (window, console, etc.) -->
<span>{{ window.location.href }}</span>
```

---

## 3. Property Binding [property]

### Binding to Element Properties

**Property binding** = Set DOM property values dynamically

```html
<!-- Your template -->
<img
  [src]="getPostAuthorAvatar()"
  [alt]="p.author"
  class="author-avatar-img">

<video [src]="p.media_url" controls></video>

<button [disabled]="togglingLike()">Like</button>
```

**Syntax**:
```html
<!-- Property binding -->
<img [src]="imageUrl">  <!-- Binds to property -->

<!-- Interpolation (same as above for strings) -->
<img src="{{ imageUrl }}">  <!-- String interpolation -->

<!-- Static (no binding) -->
<img src="assets/logo.png">  <!-- Static string -->
```

### Binding to HTML Attributes

```html
<!-- Attribute binding with attr. prefix -->
<button [attr.aria-label]="isLiked() ? 'Unlike post' : 'Like post'">
  Like
</button>

<!-- Your theme toggle -->
<button
  [attr.aria-label]="themeService.isDarkMode() ? 'Switch to light mode' : 'Switch to dark mode'">
  Theme
</button>
```

**Why attr.?** Some attributes don't have DOM properties:
```html
<td [attr.colspan]="columnCount">  <!-- colspan is attribute-only -->
```

### Class Binding

```html
<!-- Single class (boolean) -->
<button [class.liked]="isLiked()">
  <!-- Adds 'liked' class if isLiked() is true -->
</button>

<div [class.unread]="!notification.read">
  <!-- Adds 'unread' class if notification is not read -->
</div>

<!-- Multiple classes (object) -->
<div [class]="{
  'active': isActive(),
  'disabled': isDisabled(),
  'error': hasError()
}"></div>

<!-- Static + dynamic classes -->
<button class="action-btn" [class.liked]="isLiked()">
  <!-- Always has 'action-btn', conditionally has 'liked' -->
</button>
```

### Style Binding

```html
<!-- Single style -->
<div [style.color]="isError ? 'red' : 'green'">Message</div>

<!-- With units -->
<div [style.width.px]="width">Content</div>
<div [style.font-size.rem]="fontSize">Text</div>

<!-- Multiple styles (object) -->
<div [style]="{
  'color': textColor,
  'font-size': fontSize + 'px',
  'font-weight': isBold ? 'bold' : 'normal'
}"></div>
```

---

## 4. Event Binding (event)

### Basic Event Binding

**Event binding** = Respond to user actions

```html
<!-- Your template -->
<button (click)="toggleLike()">Like</button>
<img (error)="onAvatarError(p.authorAvatar)">
<button (click)="viewProfile(p.author)">View Profile</button>

<!-- Click event -->
<button (click)="handleClick()">Click Me</button>

<!-- Input event -->
<input (input)="onInput($event)">

<!-- Focus/blur -->
<input (focus)="onFocus()" (blur)="onBlur()">

<!-- Mouse events -->
<div (mouseenter)="onMouseEnter()" (mouseleave)="onMouseLeave()">
  Hover me
</div>

<!-- Key events -->
<input (keyup.enter)="onEnter()" (keyup.escape)="onEscape()">
```

### Event Object ($event)

```typescript
// Template
<input (input)="onInput($event)">

// Component
onInput(event: Event) {
  const target = event.target as HTMLInputElement;
  console.log('Value:', target.value);
}
```

**Your template example**:
```html
<button class="action-btn-text" (click)="editPost(p.id, $event)">
  Edit
</button>
```

```typescript
// Component
editPost(postId: number | undefined, event: Event) {
  event.stopPropagation();  // Prevent event bubbling
  this.router.navigate(['/edit-post', postId]);
}
```

### Event Filtering

```html
<!-- Only trigger on Enter key -->
<input (keyup.enter)="onSubmit()">

<!-- Only trigger on Escape -->
<input (keyup.escape)="onCancel()">

<!-- Combination -->
<input (keyup.control.enter)="onSubmit()">
```

---

## 5. Two-Way Binding [(ngModel)]

### What is Two-Way Binding?

**Two-way binding** = Automatically sync data between component and view

```html
<!-- Import FormsModule first! -->
<input [(ngModel)]="username">
<!-- When user types → username updates -->
<!-- When username changes → input updates -->
```

**How it works**:
```html
<!-- Two-way binding -->
<input [(ngModel)]="username">

<!-- Equivalent to: -->
<input
  [ngModel]="username"
  (ngModelChange)="username = $event">

<!-- Which is equivalent to: -->
<input
  [value]="username"
  (input)="username = $event.target.value">
```

### Example with Signals

```typescript
// Component
export class MyComponent {
  protected readonly searchTerm = signal('');

  // Two-way binding with signals (manual)
  updateSearchTerm(value: string) {
    this.searchTerm.set(value);
  }
}
```

```html
<!-- Template -->
<input
  [value]="searchTerm()"
  (input)="updateSearchTerm($any($event.target).value)">
```

---

## 6. Modern Control Flow (@if, @for)

### @if Directive (New Syntax)

**Your app uses modern @if** (Angular 17+):

```html
<!-- Your template -->
@if (post(); as p) {
  <div class="post-detail-container">
    <h1>{{ p.title }}</h1>
  </div>
}

<!-- With @else -->
@if (loading()) {
  <div class="spinner">Loading...</div>
} @else {
  <div class="content">{{ data }}</div>
}

<!-- With @else if -->
@if (status === 'loading') {
  <div>Loading...</div>
} @else if (status === 'error') {
  <div>Error occurred</div>
} @else {
  <div>Success!</div>
}
```

**Old syntax** (still works but deprecated):
```html
<!-- ❌ Old way (don't use) -->
<div *ngIf="loading">Loading...</div>
<div *ngIf="!loading">Content</div>
```

### @if with as (Aliasing)

```html
<!-- Your template pattern -->
@if (post(); as p) {
  <!-- Now 'p' is available in this block -->
  <h1>{{ p.title }}</h1>
  <div>{{ p.author }}</div>
  <span>{{ p.likeCount }}</span>
}

<!-- Multiple conditions -->
@if (currentUser(); as user) {
  @if (user.role === 'ADMIN') {
    <button>Admin Panel</button>
  }
}
```

### @for Directive (New Syntax)

```html
<!-- Your template -->
@for (block of blocks(); track $index) {
  @if (block.type === 'header') {
    <h1>{{ block.data.text }}</h1>
  }
  @if (block.type === 'paragraph') {
    <p>{{ block.data.text }}</p>
  }
}

<!-- With nested @for -->
@if (block.type === 'list' && block.data.style === 'unordered') {
  <ul>
    @for (item of block.data.items; track $index) {
      <li>{{ item }}</li>
    }
  </ul>
}
```

**@for variables**:
```html
@for (post of posts(); track post.id) {
  <div>
    Index: {{ $index }}          <!-- Current index (0, 1, 2, ...) -->
    Count: {{ $count }}           <!-- Total items -->
    First: {{ $first }}           <!-- true for first item -->
    Last: {{ $last }}             <!-- true for last item -->
    Even: {{ $even }}             <!-- true for even index -->
    Odd: {{ $odd }}              <!-- true for odd index -->
  </div>
}
```

### @for with @empty

```html
<!-- Your navbar template -->
@if (notifications().length === 0) {
  <div class="empty-state">
    <p>No notifications yet</p>
  </div>
} @else {
  @for (notification of notifications(); track notification.id) {
    <div class="notification-item">
      {{ notification.message }}
    </div>
  }
}

<!-- Better with @empty (Angular 18+) -->
@for (notification of notifications(); track notification.id) {
  <div class="notification-item">
    {{ notification.message }}
  </div>
} @empty {
  <div class="empty-state">
    <p>No notifications yet</p>
  </div>
}
```

### track Expression (IMPORTANT!)

**Always use track** for performance:

```html
<!-- ✅ GOOD: track by id -->
@for (post of posts(); track post.id) {
  <div>{{ post.title }}</div>
}

<!-- ❌ BAD: track by index (recreates on data change) -->
@for (post of posts(); track $index) {
  <div>{{ post.title }}</div>
}

<!-- Why? -->
<!-- When posts change:
  - track post.id: Angular reuses existing DOM elements
  - track $index: Angular recreates ALL DOM elements (slow!)
-->
```

---

## 7. Pipes

### What are Pipes?

**Pipes** = Transform displayed values (doesn't change actual data)

```html
<!-- Your template -->
<span>{{ p.created_at | date: 'MMM d, yyyy' }}</span>
<!-- Input: 2024-11-05T10:30:00Z -->
<!-- Output: Nov 5, 2024 -->
```

### Built-in Pipes

```html
<!-- Date pipe -->
<span>{{ createdAt | date }}</span>
<!-- Default format: Nov 5, 2024 -->

<span>{{ createdAt | date: 'short' }}</span>
<!-- 11/5/24, 10:30 AM -->

<span>{{ createdAt | date: 'medium' }}</span>
<!-- Nov 5, 2024, 10:30:00 AM -->

<span>{{ createdAt | date: 'yyyy-MM-dd' }}</span>
<!-- 2024-11-05 -->

<!-- Uppercase/Lowercase -->
<span>{{ username | uppercase }}</span>  <!-- JOHN -->
<span>{{ username | lowercase }}</span>  <!-- john -->

<!-- Currency -->
<span>{{ price | currency }}</span>      <!-- $9.99 -->
<span>{{ price | currency: 'EUR' }}</span>  <!-- €9.99 -->

<!-- Number -->
<span>{{ value | number: '1.2-2' }}</span>
<!-- 1.2-2 = min 1 digit, 2-2 decimal places -->

<!-- Percent -->
<span>{{ 0.25 | percent }}</span>  <!-- 25% -->

<!-- JSON (debugging) -->
<pre>{{ user | json }}</pre>
<!-- Pretty-printed JSON -->
```

### Chaining Pipes

```html
<!-- Apply multiple pipes -->
<span>{{ createdAt | date: 'short' | uppercase }}</span>
<!-- 11/5/24, 10:30 AM → 11/5/24, 10:30 AM (uppercase) -->

<span>{{ title | lowercase | titlecase }}</span>
<!-- "HELLO WORLD" → "hello world" → "Hello World" -->
```

### Async Pipe

**Async pipe** = Automatically subscribe to Observable

```typescript
// Component
export class MyComponent {
  posts$ = this.apiService.getAllPosts();  // Observable<PostResponse[]>
}
```

```html
<!-- Template -->
@if (posts$ | async; as posts) {
  @for (post of posts; track post.id) {
    <div>{{ post.title }}</div>
  }
}

<!-- async pipe:
  1. Subscribes to posts$
  2. Updates view when data arrives
  3. Unsubscribes when component destroyed (automatic cleanup!)
-->
```

### Custom Pipes

```typescript
// create: ng generate pipe shared/pipes/time-ago

@Pipe({
  name: 'timeAgo',
  standalone: true
})
export class TimeAgoPipe implements PipeTransform {
  transform(value: string | Date): string {
    const now = new Date();
    const date = new Date(value);
    const diffMs = now.getTime() - date.getTime();
    const diffMins = Math.floor(diffMs / 60000);

    if (diffMins < 1) return 'just now';
    if (diffMins < 60) return `${diffMins}m ago`;
    const diffHours = Math.floor(diffMins / 60);
    if (diffHours < 24) return `${diffHours}h ago`;
    const diffDays = Math.floor(diffHours / 24);
    if (diffDays < 7) return `${diffDays}d ago`;
    return date.toLocaleDateString();
  }
}
```

```html
<!-- Usage -->
<span>{{ post.createdAt | timeAgo }}</span>
<!-- Output: "5m ago" -->
```

---

## 8. Template Reference Variables

### Creating References

**Template variable** = Reference to DOM element or component

```html
<!-- Reference to input element -->
<input #usernameInput type="text">
<button (click)="log(usernameInput.value)">Log</button>

<!-- Reference to component -->
<app-navbar #navbar></app-navbar>
<button (click)="navbar.toggleDropdown()">Toggle</button>

<!-- Your form pattern -->
<input #searchInput (input)="search(searchInput.value)">
```

### Using with ngForm

```html
<form #loginForm="ngForm" (ngSubmit)="onSubmit(loginForm)">
  <input name="username" ngModel required>
  <input name="password" ngModel type="password" required>

  <button [disabled]="!loginForm.valid">Login</button>
</form>
```

```typescript
// Component
onSubmit(form: NgForm) {
  console.log('Form value:', form.value);
  // { username: '...', password: '...' }
}
```

---

## 9. Safe Navigation Operator (?)

### Preventing Null Errors

```html
<!-- ❌ Without safe navigation (error if post is null) -->
<h1>{{ post.title }}</h1>  <!-- Error: Cannot read property 'title' of null -->

<!-- ✅ With safe navigation -->
<h1>{{ post?.title }}</h1>  <!-- Returns undefined if post is null -->

<!-- Your template pattern -->
<span>{{ post()?.author }}</span>
<img [src]="post()?.mediaUrl" [alt]="post()?.title">
```

**How it works**:
```html
{{ post?.title }}

<!-- Equivalent to: -->
{{ post !== null && post !== undefined ? post.title : undefined }}
```

### Deep Navigation

```html
<!-- Access nested properties safely -->
<span>{{ user?.profile?.address?.city }}</span>
<!-- Only accesses city if all parents exist -->

<!-- Your pattern -->
<div>{{ currentUser()?.username }}</div>
<img [src]="currentUser()?.avatar">
```

---

## 10. Combining Techniques (Real Examples)

### Example 1: Conditional Rendering with Classes

```html
<!-- Your like button -->
<button
  class="action-btn"
  [class.liked]="isLiked()"
  (click)="toggleLike()"
  [disabled]="togglingLike()">

  @if (togglingLike()) {
    <span class="spinner-small"></span>
  } @else {
    <svg [attr.fill]="isLiked() ? '#8B6914' : 'currentColor'">
      <!-- SVG path -->
    </svg>
  }
  <span>{{ post()?.likeCount }}</span>
</button>
```

**What's happening**:
- `class="action-btn"` - static class
- `[class.liked]="isLiked()"` - conditional class
- `(click)="toggleLike()"` - event binding
- `[disabled]="togglingLike()"` - property binding
- `@if/@else` - conditional rendering
- `[attr.fill]="..."` - attribute binding with ternary
- `{{ post()?.likeCount }}` - interpolation with safe navigation

### Example 2: Dynamic List with Metadata

```html
<!-- Your notification dropdown -->
@if (loadingNotifications()) {
  <div class="loading-state">
    <div class="spinner"></div>
    <p>Loading...</p>
  </div>
} @else {
  @if (notifications().length === 0) {
    <div class="empty-state">
      <p>No notifications yet</p>
    </div>
  } @else {
    @for (notification of notifications(); track notification.id) {
      <div
        class="notification-item"
        [class.unread]="!notification.read"
        (click)="handleNotificationClick(notification)">

        <div class="notification-content">
          <p>{{ notification.message }}</p>
          <span>{{ getTimeAgo(notification.createdAt) }}</span>
        </div>
      </div>
    }
  }
}
```

### Example 3: Avatar Fallback Pattern

```html
<!-- Your avatar with fallback -->
@if (getPostAuthorAvatar() && !isAvatarFailed(p.authorAvatar)) {
  <img
    [src]="getPostAuthorAvatar()"
    [alt]="p.author"
    class="author-avatar-img"
    (click)="viewProfile(p.author)"
    (error)="onAvatarError(p.authorAvatar)">
} @else {
  <div class="author-avatar" (click)="viewProfile(p.author)">
    {{ getUserInitial(p.author) }}
  </div>
}
```

**Pattern**:
1. Try to show image
2. If image fails to load (`(error)` event)
3. Mark as failed (`isAvatarFailed`)
4. Show fallback (user initial)

---

## 11. Key Takeaways

### Template Syntax

- **Interpolation**: `{{ expression }}` - Display data
- **Property binding**: `[property]="value"` - Set DOM properties
- **Event binding**: `(event)="handler()"` - Handle events
- **Two-way binding**: `[(ngModel)]="value"` - Sync data

### Modern Control Flow (Your App)

- **@if/@else** - Conditional rendering (new syntax)
- **@for** - List rendering with track
- **@empty** - Handle empty lists
- ✅ Always use `track` in @for loops!

### Pipes

- Transform displayed values
- Built-in: date, uppercase, currency, etc.
- Async pipe for Observables
- Chainable

### Best Practices

- ✅ Use modern @if/@for (not *ngIf/*ngFor)
- ✅ Always track in @for loops (track post.id)
- ✅ Use safe navigation (post?.title)
- ✅ Use signals for reactive state
- ✅ Use @if with 'as' for type narrowing
- ❌ Don't put complex logic in templates
- ❌ Don't forget to track in @for
- ❌ Don't use interpolation for properties (use [property])

---

## What's Next?

Continue to `32-COMPONENT-ARCHITECTURE.md` for deep dive into component lifecycle, inputs/outputs, and change detection.

**Completed**:
- ✅ TypeScript essentials
- ✅ Angular fundamentals
- ✅ HTTP Client & Observables
- ✅ Templates & Data Binding

**Next**:
- Component lifecycle hooks
- @Input/@Output communication
- Change detection strategies
- ViewChild/ContentChild
