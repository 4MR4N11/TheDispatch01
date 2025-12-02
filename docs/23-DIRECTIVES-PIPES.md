# Directives and Pipes

## Table of Contents
1. [Directives Overview](#directives-overview)
2. [Attribute Directives](#attribute-directives)
3. [Structural Directives](#structural-directives)
4. [Pipes Overview](#pipes-overview)
5. [Built-in Pipes](#built-in-pipes)
6. [Custom Pipes](#custom-pipes)
7. [Pipe Best Practices](#pipe-best-practices)
8. [TheDispatch Custom Directives and Pipes](#thedispatch-custom-directives-and-pipes)

---

## Directives Overview

Directives are classes that add behavior to elements in your Angular applications.

### Types of Directives

1. **Components**: Directives with a template
2. **Attribute Directives**: Change appearance or behavior of an element
3. **Structural Directives**: Change DOM structure by adding/removing elements

```typescript
// Component (directive with template)
@Component({
  selector: 'app-user-card',
  template: '<div>{{ user.name }}</div>'
})

// Attribute directive (changes appearance/behavior)
@Directive({
  selector: '[appHighlight]'
})

// Structural directive (changes DOM structure)
@Directive({
  selector: '[appUnless]'
})
```

---

## Attribute Directives

Attribute directives modify the appearance or behavior of an element.

### Built-in Attribute Directives

#### ngClass
```html
<!-- Single class -->
<div [ngClass]="'active'">Static class</div>

<!-- Multiple classes -->
<div [ngClass]="['btn', 'btn-primary']">Array of classes</div>

<!-- Object syntax -->
<div [ngClass]="{
  'active': isActive,
  'disabled': isDisabled,
  'large': size === 'lg'
}">Conditional classes</div>

<!-- Method syntax -->
<div [ngClass]="getClasses()">Dynamic classes</div>
```

#### ngStyle
```html
<!-- Single style -->
<div [ngStyle]="{ 'color': textColor }">Styled</div>

<!-- Multiple styles -->
<div [ngStyle]="{
  'color': textColor,
  'font-size.px': fontSize,
  'font-weight': isBold ? 'bold' : 'normal'
}">Multiple styles</div>

<!-- Method syntax -->
<div [ngStyle]="getStyles()">Dynamic styles</div>
```

#### ngModel
```html
<!-- Two-way binding -->
<input [(ngModel)]="username" placeholder="Username">
<p>Hello, {{ username }}!</p>
```

### Creating Custom Attribute Directives

#### Simple Attribute Directive

```typescript
// highlight.directive.ts
import { Directive, ElementRef, OnInit } from '@angular/core';

@Directive({
  selector: '[appHighlight]'
})
export class HighlightDirective implements OnInit {
  constructor(private el: ElementRef) {}

  ngOnInit(): void {
    this.el.nativeElement.style.backgroundColor = 'yellow';
  }
}
```

```html
<!-- Usage -->
<p appHighlight>This text will be highlighted</p>
```

#### Directive with Input

```typescript
// highlight.directive.ts
import { Directive, ElementRef, Input, OnInit } from '@angular/core';

@Directive({
  selector: '[appHighlight]'
})
export class HighlightDirective implements OnInit {
  @Input() appHighlight: string = 'yellow';  // Color input
  @Input() defaultColor: string = 'transparent';

  constructor(private el: ElementRef) {}

  ngOnInit(): void {
    this.el.nativeElement.style.backgroundColor = this.appHighlight || this.defaultColor;
  }
}
```

```html
<!-- Usage -->
<p appHighlight="lightblue">Blue highlight</p>
<p [appHighlight]="'pink'">Pink highlight</p>
<p appHighlight>Default yellow</p>
```

#### Directive with HostListener

```typescript
// hover-highlight.directive.ts
import { Directive, ElementRef, HostListener, Input } from '@angular/core';

@Directive({
  selector: '[appHoverHighlight]'
})
export class HoverHighlightDirective {
  @Input() highlightColor: string = 'yellow';

  constructor(private el: ElementRef) {}

  @HostListener('mouseenter') onMouseEnter(): void {
    this.highlight(this.highlightColor);
  }

  @HostListener('mouseleave') onMouseLeave(): void {
    this.highlight('');
  }

  private highlight(color: string): void {
    this.el.nativeElement.style.backgroundColor = color;
  }
}
```

```html
<!-- Usage -->
<p appHoverHighlight highlightColor="lightgreen">
  Hover over me!
</p>
```

#### Directive with HostBinding

```typescript
// button-state.directive.ts
import { Directive, HostBinding, HostListener, Input } from '@angular/core';

@Directive({
  selector: '[appButtonState]'
})
export class ButtonStateDirective {
  @Input() activeClass: string = 'btn-active';

  @HostBinding('class.btn-loading')
  isLoading: boolean = false;

  @HostBinding('disabled')
  isDisabled: boolean = false;

  @HostBinding('class')
  get classList(): string {
    return this.isLoading ? 'btn-loading' : '';
  }

  @HostListener('click')
  onClick(): void {
    this.isLoading = true;
    this.isDisabled = true;

    // Simulate async operation
    setTimeout(() => {
      this.isLoading = false;
      this.isDisabled = false;
    }, 2000);
  }
}
```

```html
<button appButtonState>Click Me</button>
```

#### Advanced Directive with Renderer2

```typescript
// custom-style.directive.ts
import { Directive, ElementRef, Input, OnInit, Renderer2 } from '@angular/core';

@Directive({
  selector: '[appCustomStyle]'
})
export class CustomStyleDirective implements OnInit {
  @Input() backgroundColor: string = 'white';
  @Input() textColor: string = 'black';
  @Input() padding: string = '10px';
  @Input() borderRadius: string = '5px';

  constructor(
    private el: ElementRef,
    private renderer: Renderer2
  ) {}

  ngOnInit(): void {
    this.applyStyles();
  }

  private applyStyles(): void {
    this.renderer.setStyle(this.el.nativeElement, 'background-color', this.backgroundColor);
    this.renderer.setStyle(this.el.nativeElement, 'color', this.textColor);
    this.renderer.setStyle(this.el.nativeElement, 'padding', this.padding);
    this.renderer.setStyle(this.el.nativeElement, 'border-radius', this.borderRadius);
    this.renderer.addClass(this.el.nativeElement, 'custom-styled');
  }
}
```

**Why Renderer2?**
- Server-side rendering compatibility
- Better security (no direct DOM manipulation)
- Platform independence

---

## Structural Directives

Structural directives change the DOM layout by adding or removing elements.

### Built-in Structural Directives

#### *ngIf
```html
<!-- Basic usage -->
<div *ngIf="isVisible">Content</div>

<!-- With else -->
<div *ngIf="isLoggedIn; else loginPrompt">
  Welcome back!
</div>
<ng-template #loginPrompt>
  <p>Please log in</p>
</ng-template>

<!-- With then and else -->
<div *ngIf="user; then userInfo else loading"></div>

<ng-template #userInfo>
  <p>{{ user.name }}</p>
</ng-template>

<ng-template #loading>
  <p>Loading...</p>
</ng-template>

<!-- Store value in variable -->
<div *ngIf="user$ | async as user">
  <p>{{ user.name }}</p>
</div>
```

#### *ngFor
```html
<!-- Basic usage -->
<div *ngFor="let item of items">
  {{ item }}
</div>

<!-- With index -->
<div *ngFor="let item of items; let i = index">
  {{ i + 1 }}. {{ item }}
</div>

<!-- All exported values -->
<div *ngFor="let item of items;
             let i = index;
             let first = first;
             let last = last;
             let even = even;
             let odd = odd">
  <span [class.first]="first" [class.last]="last">
    {{ i }}. {{ item }}
  </span>
</div>

<!-- With trackBy -->
<div *ngFor="let user of users; trackBy: trackByUserId">
  {{ user.name }}
</div>
```

```typescript
trackByUserId(index: number, user: User): number {
  return user.id;
}
```

#### *ngSwitch
```html
<div [ngSwitch]="userRole">
  <div *ngSwitchCase="'admin'">Admin Panel</div>
  <div *ngSwitchCase="'moderator'">Moderator Tools</div>
  <div *ngSwitchCase="'user'">User Dashboard</div>
  <div *ngSwitchDefault>Guest View</div>
</div>
```

### Creating Custom Structural Directives

#### Simple Structural Directive

```typescript
// unless.directive.ts
import { Directive, Input, TemplateRef, ViewContainerRef } from '@angular/core';

@Directive({
  selector: '[appUnless]'
})
export class UnlessDirective {
  private hasView = false;

  @Input() set appUnless(condition: boolean) {
    if (!condition && !this.hasView) {
      // Create view if condition is false
      this.viewContainer.createEmbeddedView(this.templateRef);
      this.hasView = true;
    } else if (condition && this.hasView) {
      // Remove view if condition is true
      this.viewContainer.clear();
      this.hasView = false;
    }
  }

  constructor(
    private templateRef: TemplateRef<any>,
    private viewContainer: ViewContainerRef
  ) {}
}
```

```html
<!-- Usage - opposite of *ngIf -->
<p *appUnless="isHidden">This shows when isHidden is false</p>
```

#### Structural Directive with Context

```typescript
// repeat.directive.ts
import { Directive, Input, TemplateRef, ViewContainerRef } from '@angular/core';

@Directive({
  selector: '[appRepeat]'
})
export class RepeatDirective {
  @Input() set appRepeat(count: number) {
    this.viewContainer.clear();

    for (let i = 0; i < count; i++) {
      this.viewContainer.createEmbeddedView(this.templateRef, {
        $implicit: i,
        index: i,
        count: count,
        first: i === 0,
        last: i === count - 1
      });
    }
  }

  constructor(
    private templateRef: TemplateRef<any>,
    private viewContainer: ViewContainerRef
  ) {}
}
```

```html
<!-- Usage -->
<div *appRepeat="5; let i; let first = first; let last = last">
  <p [class.first]="first" [class.last]="last">Item {{ i }}</p>
</div>
```

#### Loading Directive

```typescript
// loading.directive.ts
import { Directive, Input, TemplateRef, ViewContainerRef, OnInit } from '@angular/core';

@Directive({
  selector: '[appLoading]'
})
export class LoadingDirective implements OnInit {
  @Input() appLoading!: boolean;
  @Input() appLoadingTemplate?: TemplateRef<any>;

  constructor(
    private templateRef: TemplateRef<any>,
    private viewContainer: ViewContainerRef
  ) {}

  ngOnInit(): void {
    this.updateView();
  }

  @Input() set appLoadingChange(isLoading: boolean) {
    this.appLoading = isLoading;
    this.updateView();
  }

  private updateView(): void {
    this.viewContainer.clear();

    if (this.appLoading) {
      // Show loading template
      if (this.appLoadingTemplate) {
        this.viewContainer.createEmbeddedView(this.appLoadingTemplate);
      }
    } else {
      // Show content
      this.viewContainer.createEmbeddedView(this.templateRef);
    }
  }
}
```

```html
<!-- Usage -->
<div *appLoading="isLoading; template: loadingTpl">
  <p>Content loaded!</p>
</div>

<ng-template #loadingTpl>
  <p>Loading...</p>
</ng-template>
```

---

## Pipes Overview

Pipes transform data for display in templates without changing the underlying data.

### Pipe Syntax

```html
<!-- Basic pipe -->
{{ value | pipeName }}

<!-- Pipe with parameters -->
{{ value | pipeName:param1:param2 }}

<!-- Chaining pipes -->
{{ value | pipe1 | pipe2 | pipe3 }}
```

---

## Built-in Pipes

### DatePipe

```typescript
today: Date = new Date();
```

```html
<!-- Default -->
{{ today | date }}
<!-- Jan 5, 2025 -->

<!-- Predefined formats -->
{{ today | date:'short' }}
<!-- 1/5/25, 3:30 PM -->

{{ today | date:'medium' }}
<!-- Jan 5, 2025, 3:30:45 PM -->

{{ today | date:'long' }}
<!-- January 5, 2025 at 3:30:45 PM GMT+1 -->

{{ today | date:'full' }}
<!-- Tuesday, January 5, 2025 at 3:30:45 PM Greenwich Mean Time -->

{{ today | date:'shortDate' }}
<!-- 1/5/25 -->

{{ today | date:'mediumDate' }}
<!-- Jan 5, 2025 -->

{{ today | date:'longDate' }}
<!-- January 5, 2025 -->

{{ today | date:'fullDate' }}
<!-- Tuesday, January 5, 2025 -->

<!-- Custom format -->
{{ today | date:'dd/MM/yyyy' }}
<!-- 05/01/2025 -->

{{ today | date:'MMMM d, y' }}
<!-- January 5, 2025 -->

{{ today | date:'h:mm a' }}
<!-- 3:30 PM -->

{{ today | date:'EEEE, MMMM d, y, h:mm:ss a' }}
<!-- Tuesday, January 5, 2025, 3:30:45 PM -->
```

### CurrencyPipe

```typescript
price: number = 1234.56;
```

```html
{{ price | currency }}
<!-- $1,234.56 -->

{{ price | currency:'EUR' }}
<!-- €1,234.56 -->

{{ price | currency:'GBP':'symbol':'1.0-0' }}
<!-- £1,235 -->

{{ price | currency:'USD':'code' }}
<!-- USD1,234.56 -->
```

### DecimalPipe

```typescript
number: number = 3.14159;
```

```html
{{ number | number }}
<!-- 3.142 -->

{{ number | number:'1.0-0' }}
<!-- 3 -->

{{ number | number:'1.2-4' }}
<!-- 3.1416 -->

{{ 1234567.89 | number }}
<!-- 1,234,567.89 -->
```

### PercentPipe

```typescript
rate: number = 0.756;
```

```html
{{ rate | percent }}
<!-- 75.6% -->

{{ rate | percent:'1.0-0' }}
<!-- 76% -->

{{ rate | percent:'1.2-2' }}
<!-- 75.60% -->
```

### SlicePipe

```typescript
items: string[] = ['A', 'B', 'C', 'D', 'E'];
text: string = 'Hello World';
```

```html
<!-- Array -->
{{ items | slice:1:3 }}
<!-- ['B', 'C'] -->

{{ items | slice:2 }}
<!-- ['C', 'D', 'E'] -->

<!-- String -->
{{ text | slice:0:5 }}
<!-- Hello -->

{{ text | slice:6 }}
<!-- World -->
```

### JsonPipe

```typescript
user = {
  id: 1,
  name: 'John',
  email: 'john@example.com'
};
```

```html
<pre>{{ user | json }}</pre>
<!--
{
  "id": 1,
  "name": "John",
  "email": "john@example.com"
}
-->
```

### AsyncPipe

```typescript
data$: Observable<Data> = this.service.getData();
```

```html
<!-- Automatically subscribes and unsubscribes -->
<div *ngIf="data$ | async as data">
  {{ data.value }}
</div>
```

### Case Pipes

```typescript
text: string = 'hello WORLD';
```

```html
{{ text | uppercase }}
<!-- HELLO WORLD -->

{{ text | lowercase }}
<!-- hello world -->

{{ text | titlecase }}
<!-- Hello World -->
```

---

## Custom Pipes

### Creating a Custom Pipe

#### Simple Pipe

```typescript
// truncate.pipe.ts
import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  name: 'truncate'
})
export class TruncatePipe implements PipeTransform {
  transform(value: string, limit: number = 50, trail: string = '...'): string {
    if (!value) return '';

    if (value.length <= limit) {
      return value;
    }

    return value.substring(0, limit) + trail;
  }
}
```

```html
<p>{{ longText | truncate:100 }}</p>
<p>{{ longText | truncate:50:'---' }}</p>
```

#### Pipe with Multiple Parameters

```typescript
// time-ago.pipe.ts
import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  name: 'timeAgo'
})
export class TimeAgoPipe implements PipeTransform {
  transform(value: Date | string, short: boolean = false): string {
    const date = value instanceof Date ? value : new Date(value);
    const now = new Date();
    const seconds = Math.floor((now.getTime() - date.getTime()) / 1000);

    const intervals = {
      year: 31536000,
      month: 2592000,
      week: 604800,
      day: 86400,
      hour: 3600,
      minute: 60,
      second: 1
    };

    for (const [unit, secondsInUnit] of Object.entries(intervals)) {
      const interval = Math.floor(seconds / secondsInUnit);

      if (interval >= 1) {
        const suffix = interval === 1 ? '' : 's';
        const unitShort = short ? unit.charAt(0) : unit;
        return `${interval}${short ? '' : ' '}${unitShort}${suffix} ago`;
      }
    }

    return 'just now';
  }
}
```

```html
<p>{{ post.createdAt | timeAgo }}</p>
<!-- 2 hours ago -->

<p>{{ post.createdAt | timeAgo:true }}</p>
<!-- 2h ago -->
```

#### Filter Pipe

```typescript
// filter.pipe.ts
import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  name: 'filter',
  pure: false  // Impure pipe - runs on every change detection
})
export class FilterPipe implements PipeTransform {
  transform<T>(items: T[], searchText: string, property?: keyof T): T[] {
    if (!items || !searchText) {
      return items;
    }

    searchText = searchText.toLowerCase();

    return items.filter(item => {
      if (property) {
        return String(item[property]).toLowerCase().includes(searchText);
      }

      // Search all properties
      return Object.values(item).some(value =>
        String(value).toLowerCase().includes(searchText)
      );
    });
  }
}
```

```html
<input [(ngModel)]="searchQuery" placeholder="Search...">

<div *ngFor="let user of users | filter:searchQuery:'name'">
  {{ user.name }}
</div>
```

#### Sort Pipe

```typescript
// sort.pipe.ts
import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  name: 'sort',
  pure: false
})
export class SortPipe implements PipeTransform {
  transform<T>(items: T[], property: keyof T, direction: 'asc' | 'desc' = 'asc'): T[] {
    if (!items || items.length === 0) {
      return items;
    }

    return [...items].sort((a, b) => {
      const aValue = a[property];
      const bValue = b[property];

      let comparison = 0;

      if (aValue > bValue) {
        comparison = 1;
      } else if (aValue < bValue) {
        comparison = -1;
      }

      return direction === 'asc' ? comparison : -comparison;
    });
  }
}
```

```html
<div *ngFor="let user of users | sort:'name':'asc'">
  {{ user.name }}
</div>

<div *ngFor="let post of posts | sort:'createdAt':'desc'">
  {{ post.title }}
</div>
```

#### Safe HTML Pipe

```typescript
// safe-html.pipe.ts
import { Pipe, PipeTransform } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';

@Pipe({
  name: 'safeHtml'
})
export class SafeHtmlPipe implements PipeTransform {
  constructor(private sanitizer: DomSanitizer) {}

  transform(html: string): SafeHtml {
    return this.sanitizer.bypassSecurityTrustHtml(html);
  }
}
```

```html
<div [innerHTML]="htmlContent | safeHtml"></div>
```

**Warning**: Only use with trusted content. Sanitizing user-generated HTML can expose XSS vulnerabilities.

---

## Pipe Best Practices

### 1. Pure vs Impure Pipes

```typescript
// Pure pipe (default) - only runs when input reference changes
@Pipe({
  name: 'myPipe',
  pure: true  // Default
})
export class MyPipe implements PipeTransform {
  transform(value: any): any {
    console.log('Pure pipe executed');
    return value;
  }
}

// Impure pipe - runs on every change detection
@Pipe({
  name: 'myPipe',
  pure: false  // Impure
})
export class MyImpurePipe implements PipeTransform {
  transform(value: any): any {
    console.log('Impure pipe executed');
    return value;
  }
}
```

**When to use impure pipes:**
- Filtering/sorting arrays
- Pipes that depend on external state
- Real-time updates (clock, countdown)

**Warning**: Impure pipes run frequently and can impact performance.

### 2. Keep Pipes Simple

```typescript
// Bad - complex logic in pipe
@Pipe({ name: 'complexPipe' })
export class ComplexPipe implements PipeTransform {
  transform(users: User[]): User[] {
    return users
      .filter(u => u.active)
      .map(u => ({ ...u, fullName: `${u.firstName} ${u.lastName}` }))
      .sort((a, b) => a.fullName.localeCompare(b.fullName))
      .slice(0, 10);
  }
}

// Good - simple, focused pipes
@Pipe({ name: 'activeUsers' })
export class ActiveUsersPipe implements PipeTransform {
  transform(users: User[]): User[] {
    return users.filter(u => u.active);
  }
}

@Pipe({ name: 'limitTo' })
export class LimitToPipe implements PipeTransform {
  transform<T>(items: T[], limit: number): T[] {
    return items.slice(0, limit);
  }
}
```

### 3. Avoid State in Pipes

```typescript
// Bad - pipe has state
@Pipe({ name: 'counter' })
export class CounterPipe implements PipeTransform {
  private count = 0;  // State - BAD

  transform(value: any): number {
    return ++this.count;
  }
}

// Good - stateless
@Pipe({ name: 'length' })
export class LengthPipe implements PipeTransform {
  transform(value: any[]): number {
    return value ? value.length : 0;
  }
}
```

### 4. Handle Edge Cases

```typescript
@Pipe({ name: 'truncate' })
export class TruncatePipe implements PipeTransform {
  transform(value: string, limit: number = 50): string {
    // Handle null/undefined
    if (!value) return '';

    // Handle invalid limit
    if (limit <= 0) return value;

    // Handle short strings
    if (value.length <= limit) return value;

    // Truncate
    return value.substring(0, limit) + '...';
  }
}
```

---

## TheDispatch Custom Directives and Pipes

### Custom Directives

#### Lazy Load Images

```typescript
// lazy-load.directive.ts
import { Directive, ElementRef, Input, OnInit } from '@angular/core';

@Directive({
  selector: '[appLazyLoad]'
})
export class LazyLoadDirective implements OnInit {
  @Input() appLazyLoad!: string;  // Image URL

  constructor(private el: ElementRef<HTMLImageElement>) {}

  ngOnInit(): void {
    const observer = new IntersectionObserver(entries => {
      entries.forEach(entry => {
        if (entry.isIntersecting) {
          this.loadImage();
          observer.disconnect();
        }
      });
    });

    observer.observe(this.el.nativeElement);
  }

  private loadImage(): void {
    const img = this.el.nativeElement;
    img.src = this.appLazyLoad;
  }
}
```

```html
<img appLazyLoad="assets/large-image.jpg" alt="Post image">
```

#### Debounce Click

```typescript
// debounce-click.directive.ts
import { Directive, EventEmitter, HostListener, Input, Output, OnDestroy } from '@angular/core';
import { Subject, Subscription } from 'rxjs';
import { debounceTime } from 'rxjs/operators';

@Directive({
  selector: '[appDebounceClick]'
})
export class DebounceClickDirective implements OnDestroy {
  @Input() debounceTime: number = 500;
  @Output() debounceClick = new EventEmitter();

  private clicks = new Subject();
  private subscription: Subscription;

  constructor() {
    this.subscription = this.clicks
      .pipe(debounceTime(this.debounceTime))
      .subscribe(event => this.debounceClick.emit(event));
  }

  @HostListener('click', ['$event'])
  clickEvent(event: MouseEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.clicks.next(event);
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}
```

```html
<button (debounceClick)="savePost()" [debounceTime]="1000">
  Save Post
</button>
```

#### Auto Focus

```typescript
// auto-focus.directive.ts
import { Directive, ElementRef, Input, OnInit } from '@angular/core';

@Directive({
  selector: '[appAutoFocus]'
})
export class AutoFocusDirective implements OnInit {
  @Input() appAutoFocus: boolean = true;

  constructor(private el: ElementRef<HTMLInputElement>) {}

  ngOnInit(): void {
    if (this.appAutoFocus) {
      setTimeout(() => {
        this.el.nativeElement.focus();
      }, 100);
    }
  }
}
```

```html
<input appAutoFocus type="text" placeholder="Search posts...">
```

### Custom Pipes

#### Relative Time

```typescript
// relative-time.pipe.ts
@Pipe({ name: 'relativeTime' })
export class RelativeTimePipe implements PipeTransform {
  transform(value: Date | string): string {
    const date = value instanceof Date ? value : new Date(value);
    const now = new Date();
    const seconds = Math.floor((now.getTime() - date.getTime()) / 1000);

    if (seconds < 60) return 'just now';
    if (seconds < 3600) return `${Math.floor(seconds / 60)}m ago`;
    if (seconds < 86400) return `${Math.floor(seconds / 3600)}h ago`;
    if (seconds < 604800) return `${Math.floor(seconds / 86400)}d ago`;
    if (seconds < 2592000) return `${Math.floor(seconds / 604800)}w ago`;
    if (seconds < 31536000) return `${Math.floor(seconds / 2592000)}mo ago`;
    return `${Math.floor(seconds / 31536000)}y ago`;
  }
}
```

#### Excerpt

```typescript
// excerpt.pipe.ts
@Pipe({ name: 'excerpt' })
export class ExcerptPipe implements PipeTransform {
  transform(content: string, length: number = 150): string {
    if (!content) return '';

    // Strip HTML tags
    const stripped = content.replace(/<[^>]*>/g, '');

    if (stripped.length <= length) {
      return stripped;
    }

    // Truncate at word boundary
    const truncated = stripped.substring(0, length);
    const lastSpace = truncated.lastIndexOf(' ');

    return (lastSpace > 0 ? truncated.substring(0, lastSpace) : truncated) + '...';
  }
}
```

#### Read Time

```typescript
// read-time.pipe.ts
@Pipe({ name: 'readTime' })
export class ReadTimePipe implements PipeTransform {
  transform(content: string, wordsPerMinute: number = 200): string {
    if (!content) return '0 min read';

    const words = content.trim().split(/\s+/).length;
    const minutes = Math.ceil(words / wordsPerMinute);

    return `${minutes} min read`;
  }
}
```

```html
<!-- Usage in post card -->
<div class="post-card">
  <h2>{{ post.title }}</h2>
  <p>{{ post.content | excerpt:200 }}</p>
  <div class="meta">
    <span>{{ post.createdAt | relativeTime }}</span>
    <span>{{ post.content | readTime }}</span>
  </div>
</div>
```

---

## Summary

Directives and pipes extend Angular's template capabilities:

**Directives:**
1. **Attribute Directives** - Modify element appearance/behavior
2. **Structural Directives** - Modify DOM structure
3. **Built-in Directives** - ngClass, ngStyle, ngIf, ngFor, ngSwitch
4. **Custom Directives** - Create reusable behaviors

**Pipes:**
1. **Transform Data** - Display formatting without changing source
2. **Built-in Pipes** - Date, currency, decimal, async, etc.
3. **Custom Pipes** - Application-specific transformations
4. **Pure vs Impure** - Performance considerations

**Key Takeaways:**
- Use Renderer2 for DOM manipulation
- Keep pipes simple and focused
- Prefer pure pipes for performance
- Use structural directives for conditional rendering
- Create reusable directives for common behaviors
- Handle edge cases in pipes
- Avoid state in pipes

Mastering directives and pipes enables building rich, interactive UIs for applications like TheDispatch blog platform.
