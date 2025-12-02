# Angular Component Architecture Deep Dive

## Table of Contents
1. [Component Lifecycle](#component-lifecycle)
2. [Lifecycle Hooks](#lifecycle-hooks)
3. [Component Interaction Patterns](#component-interaction-patterns)
4. [ViewChild and ViewChildren](#viewchild-and-viewchildren)
5. [ContentChild and ContentChildren](#contentchild-and-contentchildren)
6. [Content Projection (ng-content)](#content-projection-ng-content)
7. [Change Detection](#change-detection)
8. [Component Communication Strategies](#component-communication-strategies)
9. [Dynamic Components](#dynamic-components)
10. [Component Best Practices](#component-best-practices)

---

## Component Lifecycle

Every Angular component has a lifecycle managed by Angular. From creation to destruction, Angular calls specific lifecycle hook methods at key moments.

### Lifecycle Sequence

```
1. Constructor
   ↓
2. OnChanges (if there are @Input properties)
   ↓
3. OnInit
   ↓
4. DoCheck
   ↓
5. AfterContentInit
   ↓
6. AfterContentChecked
   ↓
7. AfterViewInit
   ↓
8. AfterViewChecked
   ↓
9. OnDestroy
```

During component lifetime, some hooks are called repeatedly during change detection:
- **OnChanges**: When @Input properties change
- **DoCheck**: Every change detection cycle
- **AfterContentChecked**: After checking projected content
- **AfterViewChecked**: After checking component's views

---

## Lifecycle Hooks

### constructor()

The class constructor is called first, before any lifecycle hooks.

```typescript
export class UserComponent {
  userId!: number;

  constructor(
    private userService: UserService,
    private route: ActivatedRoute
  ) {
    // Dependency injection happens here
    console.log('Constructor called');

    // DON'T access @Input properties here - they're not set yet
    // DON'T access DOM elements - view is not initialized
  }
}
```

**Use for:**
- Dependency injection
- Simple initialization
- Setting default values

**Don't use for:**
- Accessing @Input properties (use ngOnInit instead)
- DOM manipulation (use ngAfterViewInit instead)
- API calls (use ngOnInit instead)

---

### ngOnChanges()

Called when any data-bound input property changes. Receives a `SimpleChanges` object.

```typescript
import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';

export class UserCardComponent implements OnChanges {
  @Input() userId!: number;
  @Input() userName!: string;
  @Input() isActive!: boolean;

  ngOnChanges(changes: SimpleChanges): void {
    console.log('ngOnChanges called', changes);

    // Check if specific property changed
    if (changes['userId']) {
      const change = changes['userId'];
      console.log('userId changed from', change.previousValue, 'to', change.currentValue);
      console.log('Is first change?', change.firstChange);

      // Load new user data
      if (!change.firstChange) {
        this.loadUserData(change.currentValue);
      }
    }

    if (changes['userName']) {
      console.log('userName changed to', changes['userName'].currentValue);
    }

    if (changes['isActive']) {
      const active = changes['isActive'].currentValue;
      this.updateUserStatus(active);
    }
  }

  private loadUserData(userId: number): void {
    // Load user data
  }

  private updateUserStatus(isActive: boolean): void {
    // Update UI based on status
  }
}
```

**SimpleChanges Structure:**
```typescript
{
  propertyName: {
    currentValue: any,     // Current value
    previousValue: any,    // Previous value
    firstChange: boolean   // True if this is the first change
  }
}
```

**Use for:**
- Responding to @Input property changes
- Performing actions when specific inputs change
- Comparing previous and current values

**Note:** ngOnChanges is NOT called for object mutations (only reference changes):
```typescript
// This WILL trigger ngOnChanges
this.user = { ...this.user, name: 'New Name' };

// This WON'T trigger ngOnChanges
this.user.name = 'New Name';
```

---

### ngOnInit()

Called once after the first ngOnChanges. This is where most initialization logic goes.

```typescript
import { Component, OnInit, Input } from '@angular/core';

export class PostDetailComponent implements OnInit {
  @Input() postId!: number;

  post?: Post;
  comments: Comment[] = [];
  loading: boolean = true;

  constructor(
    private postService: PostService,
    private commentService: CommentService,
    private route: ActivatedRoute
  ) {
    console.log('Constructor - postId:', this.postId);  // undefined
  }

  ngOnInit(): void {
    console.log('ngOnInit - postId:', this.postId);  // Now available

    // Initialize component data
    this.loadPost();
    this.loadComments();

    // Subscribe to route params
    this.route.params.subscribe(params => {
      this.postId = +params['id'];
      this.loadPost();
    });
  }

  private loadPost(): void {
    this.loading = true;
    this.postService.getPost(this.postId).subscribe({
      next: (post) => {
        this.post = post;
        this.loading = false;
      },
      error: (err) => {
        console.error('Failed to load post', err);
        this.loading = false;
      }
    });
  }

  private loadComments(): void {
    this.commentService.getComments(this.postId).subscribe({
      next: (comments) => this.comments = comments
    });
  }
}
```

**Use for:**
- Fetching initial data from services
- Complex initialization logic
- Setting up subscriptions
- Accessing @Input properties (they're available now)

**Don't use for:**
- DOM manipulation (view not ready yet - use ngAfterViewInit)
- Accessing child components (use ngAfterViewInit)

---

### ngDoCheck()

Called during every change detection cycle. Use for custom change detection.

```typescript
import { Component, DoCheck, Input } from '@angular/core';

export class CustomChangeDetectionComponent implements DoCheck {
  @Input() items: any[] = [];

  private lastItemsLength: number = 0;

  ngDoCheck(): void {
    // Custom change detection for array mutations
    if (this.items.length !== this.lastItemsLength) {
      console.log('Items array length changed');
      this.lastItemsLength = this.items.length;
      this.performCustomLogic();
    }
  }

  private performCustomLogic(): void {
    // Custom logic when items change
  }
}
```

**Warning:** ngDoCheck runs very frequently. Keep it lightweight to avoid performance issues.

**Use for:**
- Detecting changes Angular doesn't catch (object mutations, array changes)
- Custom change detection logic
- Deep equality checks

**Don't use for:**
- Heavy computations
- API calls
- Complex logic

---

### ngAfterContentInit()

Called once after Angular projects external content into the component's view (via ng-content).

```typescript
import { Component, AfterContentInit, ContentChild } from '@angular/core';

@Component({
  selector: 'app-card',
  template: `
    <div class="card">
      <div class="card-header">
        <ng-content select="[card-header]"></ng-content>
      </div>
      <div class="card-body">
        <ng-content></ng-content>
      </div>
    </div>
  `
})
export class CardComponent implements AfterContentInit {
  @ContentChild(CardHeaderDirective) header?: CardHeaderDirective;

  ngAfterContentInit(): void {
    // Projected content is now available
    console.log('Projected content initialized', this.header);

    if (this.header) {
      this.header.customize();
    }
  }
}
```

**Use for:**
- Accessing projected content (ng-content)
- Initializing ContentChild/ContentChildren queries
- Setting up projected components

---

### ngAfterContentChecked()

Called after every check of projected content.

```typescript
export class CardComponent implements AfterContentChecked {
  @ContentChild(CardHeaderDirective) header?: CardHeaderDirective;

  ngAfterContentChecked(): void {
    // Called after every change detection check of projected content
    console.log('Content checked');
  }
}
```

**Warning:** Called very frequently. Keep lightweight.

**Use for:**
- Responding to changes in projected content
- Monitoring ContentChild/ContentChildren

---

### ngAfterViewInit()

Called once after Angular initializes the component's views and child views.

```typescript
import { Component, AfterViewInit, ViewChild, ElementRef } from '@angular/core';

export class SearchComponent implements AfterViewInit {
  @ViewChild('searchInput') searchInput!: ElementRef<HTMLInputElement>;
  @ViewChild(ChildComponent) childComponent!: ChildComponent;

  constructor() {
    console.log('Constructor - searchInput:', this.searchInput);  // undefined
  }

  ngOnInit(): void {
    console.log('ngOnInit - searchInput:', this.searchInput);  // still undefined
  }

  ngAfterViewInit(): void {
    console.log('ngAfterViewInit - searchInput:', this.searchInput);  // Now available!

    // Safe to access view children and DOM elements
    this.searchInput.nativeElement.focus();

    // Safe to call child component methods
    this.childComponent.initialize();

    // DOM manipulation
    this.setupCustomScrolling();
  }

  private setupCustomScrolling(): void {
    // DOM manipulation logic
  }
}
```

**Use for:**
- Accessing ViewChild/ViewChildren queries
- DOM manipulation
- Initializing third-party libraries that need DOM elements
- Interacting with child components

**Don't use for:**
- Modifying data-bound properties (causes ExpressionChangedAfterItHasBeenCheckedError)

**If you need to update properties:**
```typescript
ngAfterViewInit(): void {
  // Wrong - causes error
  this.title = 'New Title';

  // Right - schedule update for next cycle
  setTimeout(() => {
    this.title = 'New Title';
  });

  // Or use ChangeDetectorRef
  this.cdr.detectChanges();
}
```

---

### ngAfterViewChecked()

Called after every check of the component's views and child views.

```typescript
export class MonitorComponent implements AfterViewChecked {
  @ViewChild('content') content!: ElementRef;

  ngAfterViewChecked(): void {
    // Called after every change detection check
    console.log('View checked');

    // Monitor view changes
    this.checkContentHeight();
  }

  private checkContentHeight(): void {
    const height = this.content.nativeElement.offsetHeight;
    // React to height changes
  }
}
```

**Warning:** Called very frequently during change detection. Keep lightweight.

---

### ngOnDestroy()

Called just before Angular destroys the component. Critical for cleanup.

```typescript
import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';

export class DataComponent implements OnInit, OnDestroy {
  private subscriptions: Subscription[] = [];
  private intervalId?: number;

  constructor(
    private dataService: DataService,
    private websocketService: WebSocketService
  ) {}

  ngOnInit(): void {
    // Subscribe to observables
    const dataSub = this.dataService.getData().subscribe(data => {
      console.log('Data received', data);
    });
    this.subscriptions.push(dataSub);

    const eventSub = this.dataService.events$.subscribe(event => {
      console.log('Event:', event);
    });
    this.subscriptions.push(eventSub);

    // Set up interval
    this.intervalId = window.setInterval(() => {
      this.updateData();
    }, 5000);

    // Connect to WebSocket
    this.websocketService.connect();
  }

  ngOnDestroy(): void {
    console.log('Component destroyed - cleaning up');

    // Unsubscribe from all observables
    this.subscriptions.forEach(sub => sub.unsubscribe());

    // Clear intervals
    if (this.intervalId) {
      clearInterval(this.intervalId);
    }

    // Disconnect WebSocket
    this.websocketService.disconnect();

    // Clean up event listeners
    this.removeEventListeners();
  }

  private removeEventListeners(): void {
    // Remove any manual event listeners
  }

  private updateData(): void {
    // Update logic
  }
}
```

**Use for:**
- Unsubscribing from Observables
- Clearing timers (setTimeout, setInterval)
- Detaching event listeners
- Closing connections (WebSocket, SSE)
- Freeing resources

**Memory Leak Prevention:**
```typescript
// Bad - memory leak
export class LeakyComponent implements OnInit {
  ngOnInit(): void {
    this.service.getData().subscribe(data => {
      // No unsubscribe - subscription lives forever
    });
  }
}

// Good - proper cleanup
export class CleanComponent implements OnInit, OnDestroy {
  private subscription?: Subscription;

  ngOnInit(): void {
    this.subscription = this.service.getData().subscribe(data => {
      // Handle data
    });
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }
}

// Even better - use async pipe (auto-unsubscribes)
export class BestComponent {
  data$ = this.service.getData();
}
```

---

## Component Interaction Patterns

### Parent to Child: @Input

```typescript
// child.component.ts
export class ChildComponent {
  @Input() title: string = '';
  @Input() count: number = 0;
  @Input() user?: User;

  // Input with setter for validation/transformation
  private _age: number = 0;

  @Input()
  set age(value: number) {
    if (value < 0) {
      console.warn('Age cannot be negative');
      this._age = 0;
    } else {
      this._age = value;
    }
  }

  get age(): number {
    return this._age;
  }

  // Required input (Angular 16+)
  @Input({ required: true }) userId!: number;
}
```

```html
<!-- parent.component.html -->
<app-child
  [title]="parentTitle"
  [count]="itemCount"
  [user]="currentUser"
  [age]="userAge"
  [userId]="123">
</app-child>
```

### Child to Parent: @Output

```typescript
// child.component.ts
export class ChildComponent {
  @Output() itemSelected = new EventEmitter<Item>();
  @Output() countChanged = new EventEmitter<number>();
  @Output() formSubmitted = new EventEmitter<FormData>();

  selectItem(item: Item): void {
    this.itemSelected.emit(item);
  }

  incrementCount(): void {
    this.count++;
    this.countChanged.emit(this.count);
  }

  submitForm(data: FormData): void {
    this.formSubmitted.emit(data);
  }
}
```

```html
<!-- parent.component.html -->
<app-child
  (itemSelected)="handleItemSelection($event)"
  (countChanged)="handleCountChange($event)"
  (formSubmitted)="handleSubmit($event)">
</app-child>
```

```typescript
// parent.component.ts
export class ParentComponent {
  handleItemSelection(item: Item): void {
    console.log('Item selected:', item);
  }

  handleCountChange(count: number): void {
    console.log('Count changed to:', count);
  }

  handleSubmit(data: FormData): void {
    console.log('Form submitted:', data);
  }
}
```

### Parent Accessing Child: Template Reference

```html
<!-- parent.component.html -->
<app-timer #timerComponent></app-timer>
<button (click)="timerComponent.start()">Start</button>
<button (click)="timerComponent.stop()">Stop</button>
<button (click)="timerComponent.reset()">Reset</button>
<p>Time: {{ timerComponent.seconds }}</p>
```

### Parent Accessing Child: @ViewChild

```typescript
// parent.component.ts
export class ParentComponent implements AfterViewInit {
  @ViewChild(TimerComponent) timerComponent!: TimerComponent;
  @ViewChild('videoPlayer') videoElement!: ElementRef<HTMLVideoElement>;

  ngAfterViewInit(): void {
    // Access child component
    this.timerComponent.start();

    // Access DOM element
    this.videoElement.nativeElement.play();
  }

  pauseTimer(): void {
    this.timerComponent.stop();
  }
}
```

---

## ViewChild and ViewChildren

Access child components, directives, or DOM elements from the parent component.

### @ViewChild

Query for a single element.

```typescript
import { Component, ViewChild, ElementRef, AfterViewInit } from '@angular/core';

export class ViewChildExampleComponent implements AfterViewInit {
  // Query by template reference variable
  @ViewChild('myInput') inputElement!: ElementRef<HTMLInputElement>;

  // Query by component type
  @ViewChild(ChildComponent) childComponent!: ChildComponent;

  // Query by directive
  @ViewChild(HighlightDirective) highlightDirective!: HighlightDirective;

  // Query with read option to get different token
  @ViewChild('myDiv', { read: ElementRef }) divElement!: ElementRef;
  @ViewChild('myDiv', { read: ViewContainerRef }) divContainer!: ViewContainerRef;

  // Static query (available in ngOnInit, but won't update)
  @ViewChild('staticElement', { static: true }) staticElement!: ElementRef;

  ngAfterViewInit(): void {
    // Safe to access view children here
    this.inputElement.nativeElement.focus();
    this.childComponent.doSomething();
    this.highlightDirective.highlight('yellow');
  }

  focusInput(): void {
    this.inputElement.nativeElement.focus();
  }

  clearInput(): void {
    this.inputElement.nativeElement.value = '';
  }
}
```

```html
<input #myInput type="text" placeholder="Enter text">
<div #myDiv>Content</div>
<div #staticElement>Static</div>
<app-child></app-child>
<p appHighlight>Highlighted text</p>
```

### @ViewChildren

Query for multiple elements.

```typescript
import { Component, ViewChildren, QueryList, AfterViewInit } from '@angular/core';

export class ViewChildrenExampleComponent implements AfterViewInit {
  @ViewChildren('listItem') listItems!: QueryList<ElementRef>;
  @ViewChildren(CardComponent) cardComponents!: QueryList<CardComponent>;

  ngAfterViewInit(): void {
    console.log('Number of list items:', this.listItems.length);
    console.log('Number of cards:', this.cardComponents.length);

    // Iterate through query results
    this.listItems.forEach((item, index) => {
      console.log(`Item ${index}:`, item.nativeElement.textContent);
    });

    this.cardComponents.forEach(card => {
      card.expand();
    });

    // Subscribe to changes
    this.listItems.changes.subscribe((items: QueryList<ElementRef>) => {
      console.log('List items changed:', items.length);
    });
  }

  highlightAllCards(): void {
    this.cardComponents.forEach(card => card.highlight());
  }

  getCardTitles(): string[] {
    return this.cardComponents.map(card => card.title);
  }
}
```

```html
<ul>
  <li #listItem *ngFor="let item of items">{{ item }}</li>
</ul>

<app-card *ngFor="let data of cardData" [data]="data"></app-card>
```

### QueryList Methods

```typescript
export class QueryListComponent implements AfterViewInit {
  @ViewChildren(ChildComponent) children!: QueryList<ChildComponent>;

  ngAfterViewInit(): void {
    // Get as array
    const childrenArray = this.children.toArray();

    // Get first and last
    const first = this.children.first;
    const last = this.children.last;

    // Get length
    const count = this.children.length;

    // Find specific child
    const found = this.children.find(child => child.id === 5);

    // Filter children
    const active = this.children.filter(child => child.isActive);

    // Iterate
    this.children.forEach(child => child.update());

    // Map
    const ids = this.children.map(child => child.id);

    // Check if any match condition
    const hasActive = this.children.some(child => child.isActive);

    // Subscribe to changes
    this.children.changes.subscribe(() => {
      console.log('Children changed');
    });
  }
}
```

---

## ContentChild and ContentChildren

Access content projected into the component via ng-content.

### @ContentChild

```typescript
// card.component.ts
@Component({
  selector: 'app-card',
  template: `
    <div class="card">
      <div class="card-header">
        <ng-content select="[card-header]"></ng-content>
      </div>
      <div class="card-body">
        <ng-content></ng-content>
      </div>
      <div class="card-footer">
        <ng-content select="[card-footer]"></ng-content>
      </div>
    </div>
  `
})
export class CardComponent implements AfterContentInit {
  @ContentChild('headerContent') headerContent!: ElementRef;
  @ContentChild(CardHeaderDirective) headerDirective!: CardHeaderDirective;

  ngAfterContentInit(): void {
    // Access projected content
    if (this.headerContent) {
      console.log('Header content:', this.headerContent.nativeElement.textContent);
    }

    if (this.headerDirective) {
      this.headerDirective.setColor('blue');
    }
  }
}
```

```html
<!-- Usage -->
<app-card>
  <div card-header #headerContent>
    <h2>Card Title</h2>
  </div>
  <p>Card content goes here</p>
  <div card-footer>
    <button>Action</button>
  </div>
</app-card>
```

### @ContentChildren

```typescript
@Component({
  selector: 'app-tabs',
  template: `
    <div class="tabs">
      <div class="tab-headers">
        <button *ngFor="let tab of tabs; let i = index"
                (click)="selectTab(i)"
                [class.active]="selectedIndex === i">
          {{ tab.title }}
        </button>
      </div>
      <div class="tab-content">
        <ng-content></ng-content>
      </div>
    </div>
  `
})
export class TabsComponent implements AfterContentInit {
  @ContentChildren(TabComponent) tabs!: QueryList<TabComponent>;

  selectedIndex: number = 0;

  ngAfterContentInit(): void {
    // Show first tab by default
    if (this.tabs.length > 0) {
      this.selectTab(0);
    }

    // React to tab changes
    this.tabs.changes.subscribe(() => {
      console.log('Tabs changed');
      if (this.selectedIndex >= this.tabs.length) {
        this.selectTab(0);
      }
    });
  }

  selectTab(index: number): void {
    this.tabs.forEach((tab, i) => {
      tab.active = i === index;
    });
    this.selectedIndex = index;
  }
}

@Component({
  selector: 'app-tab',
  template: `
    <div class="tab-pane" *ngIf="active">
      <ng-content></ng-content>
    </div>
  `
})
export class TabComponent {
  @Input() title: string = '';
  active: boolean = false;
}
```

```html
<!-- Usage -->
<app-tabs>
  <app-tab title="Tab 1">
    <p>Content for tab 1</p>
  </app-tab>
  <app-tab title="Tab 2">
    <p>Content for tab 2</p>
  </app-tab>
  <app-tab title="Tab 3">
    <p>Content for tab 3</p>
  </app-tab>
</app-tabs>
```

---

## Content Projection (ng-content)

Project content from parent into child component templates.

### Single-slot Projection

```typescript
// card.component.ts
@Component({
  selector: 'app-card',
  template: `
    <div class="card">
      <ng-content></ng-content>
    </div>
  `
})
export class CardComponent {}
```

```html
<!-- Usage -->
<app-card>
  <h2>Title</h2>
  <p>Any content can go here</p>
  <button>Action</button>
</app-card>
```

### Multi-slot Projection

```typescript
@Component({
  selector: 'app-panel',
  template: `
    <div class="panel">
      <div class="panel-header">
        <ng-content select="[panel-header]"></ng-content>
      </div>
      <div class="panel-body">
        <ng-content select="[panel-body]"></ng-content>
      </div>
      <div class="panel-footer">
        <ng-content select="[panel-footer]"></ng-content>
      </div>
      <!-- Default slot for unmatched content -->
      <ng-content></ng-content>
    </div>
  `
})
export class PanelComponent {}
```

```html
<!-- Usage -->
<app-panel>
  <div panel-header>
    <h2>Panel Title</h2>
  </div>
  <div panel-body>
    <p>Main content</p>
  </div>
  <div panel-footer>
    <button>Save</button>
  </div>
  <!-- This goes to default slot -->
  <p>Extra content</p>
</app-panel>
```

### Conditional Projection

```typescript
@Component({
  selector: 'app-expandable-card',
  template: `
    <div class="card">
      <div class="card-header" (click)="toggle()">
        <ng-content select="[card-title]"></ng-content>
        <span class="toggle">{{ expanded ? '−' : '+' }}</span>
      </div>
      <div class="card-body" *ngIf="expanded">
        <ng-content select="[card-content]"></ng-content>
      </div>
    </div>
  `
})
export class ExpandableCardComponent {
  expanded: boolean = false;

  toggle(): void {
    this.expanded = !this.expanded;
  }
}
```

### Advanced: ng-template with ngTemplateOutlet

```typescript
@Component({
  selector: 'app-custom-list',
  template: `
    <div class="list">
      <div *ngFor="let item of items" class="list-item">
        <ng-container *ngTemplateOutlet="itemTemplate; context: { $implicit: item, index: i }">
        </ng-container>
      </div>
    </div>
  `
})
export class CustomListComponent {
  @Input() items: any[] = [];
  @ContentChild(TemplateRef) itemTemplate!: TemplateRef<any>;
}
```

```html
<!-- Usage -->
<app-custom-list [items]="users">
  <ng-template let-user let-i="index">
    <div class="user-card">
      <h3>{{ i + 1 }}. {{ user.name }}</h3>
      <p>{{ user.email }}</p>
    </div>
  </ng-template>
</app-custom-list>
```

---

## Change Detection

Angular's change detection determines when to update the view based on component data changes.

### Default Change Detection

By default, Angular checks the entire component tree on every event.

```typescript
@Component({
  selector: 'app-default',
  template: `
    <p>{{ counter }}</p>
    <button (click)="increment()">Increment</button>
  `,
  changeDetection: ChangeDetectionStrategy.Default  // Default
})
export class DefaultComponent {
  counter: number = 0;

  increment(): void {
    this.counter++;
    // Angular automatically detects change and updates view
  }
}
```

### OnPush Change Detection

With OnPush strategy, Angular only checks the component when:
1. An @Input reference changes
2. An event originates from the component or its children
3. Manual change detection is triggered

```typescript
@Component({
  selector: 'app-optimized',
  template: `
    <p>{{ user.name }}</p>
    <p>{{ counter }}</p>
    <button (click)="increment()">Increment</button>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class OptimizedComponent {
  @Input() user!: User;
  counter: number = 0;

  increment(): void {
    this.counter++;
    // View updates because event originated from this component
  }
}
```

**OnPush Requirements:**

```typescript
// Parent component
export class ParentComponent {
  user: User = { id: 1, name: 'John' };

  updateUser(): void {
    // Wrong - won't trigger change detection (mutation)
    this.user.name = 'Jane';

    // Right - triggers change detection (new reference)
    this.user = { ...this.user, name: 'Jane' };
  }
}
```

### Manual Change Detection

```typescript
import { ChangeDetectorRef, ChangeDetectionStrategy } from '@angular/core';

@Component({
  selector: 'app-manual-cd',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<p>{{ data }}</p>`
})
export class ManualCDComponent {
  data: string = '';

  constructor(private cdr: ChangeDetectorRef) {}

  updateFromCallback(): void {
    setTimeout(() => {
      this.data = 'Updated';
      // Manually trigger change detection
      this.cdr.detectChanges();
    }, 1000);
  }

  updateFromObservable(): void {
    this.service.getData().subscribe(data => {
      this.data = data;
      // Mark for check in next cycle
      this.cdr.markForCheck();
    });
  }

  detachFromCD(): void {
    // Detach component from change detection
    this.cdr.detach();

    // Component won't update automatically
    // Must manually detect changes
    this.updateData();
    this.cdr.detectChanges();
  }

  reattachToCD(): void {
    // Reattach to change detection
    this.cdr.reattach();
  }
}
```

### Change Detection Methods

```typescript
export class CDComponent {
  constructor(private cdr: ChangeDetectorRef) {}

  // Run change detection for this component and children
  detectChanges(): void {
    this.cdr.detectChanges();
  }

  // Mark component to be checked in next cycle
  markForCheck(): void {
    this.cdr.markForCheck();
  }

  // Detach component from change detection tree
  detach(): void {
    this.cdr.detach();
  }

  // Reattach component to change detection tree
  reattach(): void {
    this.cdr.reattach();
  }
}
```

---

## Component Communication Strategies

### 1. Service with Subject/BehaviorSubject

```typescript
// shared-data.service.ts
@Injectable({ providedIn: 'root' })
export class SharedDataService {
  private messageSource = new BehaviorSubject<string>('');
  currentMessage$ = this.messageSource.asObservable();

  private eventSource = new Subject<Event>();
  events$ = this.eventSource.asObservable();

  updateMessage(message: string): void {
    this.messageSource.next(message);
  }

  emitEvent(event: Event): void {
    this.eventSource.emit(event);
  }
}

// component-a.component.ts
export class ComponentA {
  constructor(private sharedData: SharedDataService) {}

  sendMessage(): void {
    this.sharedData.updateMessage('Hello from Component A');
  }
}

// component-b.component.ts
export class ComponentB implements OnInit {
  message: string = '';

  constructor(private sharedData: SharedDataService) {}

  ngOnInit(): void {
    this.sharedData.currentMessage$.subscribe(message => {
      this.message = message;
    });
  }
}
```

### 2. State Management Service

```typescript
// user-state.service.ts
@Injectable({ providedIn: 'root' })
export class UserStateService {
  private state = new BehaviorSubject<UserState>({
    user: null,
    isLoading: false,
    error: null
  });

  state$ = this.state.asObservable();

  get currentState(): UserState {
    return this.state.value;
  }

  setUser(user: User): void {
    this.state.next({
      ...this.currentState,
      user,
      isLoading: false,
      error: null
    });
  }

  setLoading(isLoading: boolean): void {
    this.state.next({
      ...this.currentState,
      isLoading
    });
  }

  setError(error: string): void {
    this.state.next({
      ...this.currentState,
      error,
      isLoading: false
    });
  }
}
```

### 3. EventBus Pattern

```typescript
// event-bus.service.ts
@Injectable({ providedIn: 'root' })
export class EventBusService {
  private eventBus = new Subject<AppEvent>();

  events$ = this.eventBus.asObservable();

  emit(event: AppEvent): void {
    this.eventBus.next(event);
  }

  on(eventType: string): Observable<AppEvent> {
    return this.events$.pipe(
      filter(event => event.type === eventType)
    );
  }
}

// Usage
export class ComponentA {
  constructor(private eventBus: EventBusService) {}

  doAction(): void {
    this.eventBus.emit({
      type: 'USER_UPDATED',
      payload: { userId: 1, name: 'John' }
    });
  }
}

export class ComponentB implements OnInit {
  constructor(private eventBus: EventBusService) {}

  ngOnInit(): void {
    this.eventBus.on('USER_UPDATED').subscribe(event => {
      console.log('User updated:', event.payload);
    });
  }
}
```

---

## Dynamic Components

Create and load components dynamically at runtime.

### ViewContainerRef Method

```typescript
import { Component, ViewChild, ViewContainerRef, ComponentRef } from '@angular/core';

@Component({
  selector: 'app-dynamic-host',
  template: `
    <div class="host-container">
      <ng-container #dynamicContainer></ng-container>
    </div>
    <button (click)="loadComponent()">Load Component</button>
    <button (click)="destroyComponent()">Destroy Component</button>
  `
})
export class DynamicHostComponent {
  @ViewChild('dynamicContainer', { read: ViewContainerRef })
  container!: ViewContainerRef;

  private componentRef?: ComponentRef<DynamicComponent>;

  loadComponent(): void {
    // Clear existing component
    this.container.clear();

    // Create component
    this.componentRef = this.container.createComponent(DynamicComponent);

    // Set inputs
    this.componentRef.instance.title = 'Dynamic Title';
    this.componentRef.instance.data = { id: 1, name: 'Test' };

    // Subscribe to outputs
    this.componentRef.instance.actionClicked.subscribe((data) => {
      console.log('Action clicked:', data);
    });

    // Manually trigger change detection if needed
    this.componentRef.changeDetectorRef.detectChanges();
  }

  destroyComponent(): void {
    if (this.componentRef) {
      this.componentRef.destroy();
      this.componentRef = undefined;
    }
  }
}

@Component({
  selector: 'app-dynamic',
  template: `
    <div>
      <h2>{{ title }}</h2>
      <p>{{ data | json }}</p>
      <button (click)="onClick()">Action</button>
    </div>
  `
})
export class DynamicComponent {
  @Input() title: string = '';
  @Input() data: any;
  @Output() actionClicked = new EventEmitter<any>();

  onClick(): void {
    this.actionClicked.emit(this.data);
  }
}
```

### Dynamic Component Factory

```typescript
@Injectable({ providedIn: 'root' })
export class DynamicComponentService {
  constructor(private injector: Injector) {}

  createComponent<T>(
    container: ViewContainerRef,
    component: Type<T>,
    inputs?: Partial<T>
  ): ComponentRef<T> {
    const componentRef = container.createComponent(component, {
      injector: this.injector
    });

    // Set inputs
    if (inputs) {
      Object.assign(componentRef.instance, inputs);
    }

    componentRef.changeDetectorRef.detectChanges();

    return componentRef;
  }
}
```

---

## Component Best Practices

### 1. Single Responsibility

```typescript
// Bad - component doing too much
export class BadComponent {
  users: User[] = [];
  posts: Post[] = [];

  loadUsers() { /* ... */ }
  saveUser() { /* ... */ }
  validateUser() { /* ... */ }
  loadPosts() { /* ... */ }
  filterPosts() { /* ... */ }
}

// Good - focused components
export class UserListComponent {
  users: User[] = [];
  constructor(private userService: UserService) {}
  loadUsers() { /* ... */ }
}

export class PostListComponent {
  posts: Post[] = [];
  constructor(private postService: PostService) {}
  loadPosts() { /* ... */ }
}
```

### 2. Smart vs Presentational Components

```typescript
// Smart (Container) Component - handles logic
@Component({
  selector: 'app-user-container',
  template: `
    <app-user-list
      [users]="users$ | async"
      [loading]="loading$ | async"
      (userSelected)="onUserSelected($event)"
      (userDeleted)="onUserDeleted($event)">
    </app-user-list>
  `
})
export class UserContainerComponent {
  users$ = this.userService.getUsers();
  loading$ = this.userService.loading$;

  constructor(private userService: UserService) {}

  onUserSelected(user: User): void {
    this.userService.selectUser(user);
  }

  onUserDeleted(userId: number): void {
    this.userService.deleteUser(userId);
  }
}

// Presentational (Dumb) Component - handles display
@Component({
  selector: 'app-user-list',
  template: `
    <div *ngIf="loading">Loading...</div>
    <div *ngFor="let user of users">
      <app-user-card
        [user]="user"
        (selected)="userSelected.emit(user)"
        (deleted)="userDeleted.emit(user.id)">
      </app-user-card>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class UserListComponent {
  @Input() users: User[] = [];
  @Input() loading: boolean = false;
  @Output() userSelected = new EventEmitter<User>();
  @Output() userDeleted = new EventEmitter<number>();
}
```

### 3. Unsubscribe from Observables

```typescript
// Method 1: Manual unsubscribe
export class Component1 implements OnInit, OnDestroy {
  private subscriptions: Subscription[] = [];

  ngOnInit(): void {
    this.subscriptions.push(
      this.service.data$.subscribe(data => {})
    );
  }

  ngOnDestroy(): void {
    this.subscriptions.forEach(sub => sub.unsubscribe());
  }
}

// Method 2: takeUntil pattern
export class Component2 implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();

  ngOnInit(): void {
    this.service.data$
      .pipe(takeUntil(this.destroy$))
      .subscribe(data => {});
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}

// Method 3: async pipe (best)
export class Component3 {
  data$ = this.service.getData();
}
```

### 4. Use OnPush When Possible

```typescript
@Component({
  selector: 'app-optimized',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `...`
})
export class OptimizedComponent {
  @Input() data!: Data;
}
```

### 5. Avoid Logic in Templates

```html
<!-- Bad -->
<div *ngIf="user && user.role === 'admin' && user.permissions.includes('write')">
  Admin content
</div>

<!-- Good -->
<div *ngIf="isAdmin">Admin content</div>
```

```typescript
get isAdmin(): boolean {
  return this.user?.role === 'admin' &&
         this.user?.permissions?.includes('write');
}
```

### 6. Use TrackBy with ngFor

```typescript
trackByUserId(index: number, user: User): number {
  return user.id;
}
```

```html
<div *ngFor="let user of users; trackBy: trackByUserId">
  {{ user.name }}
</div>
```

---

## Summary

Component architecture is central to Angular applications:

1. **Lifecycle Hooks** - Respond to component lifecycle events
2. **Component Interaction** - @Input, @Output, ViewChild, ContentChild
3. **Content Projection** - ng-content for flexible component composition
4. **Change Detection** - OnPush strategy for performance optimization
5. **Communication** - Services, Subject/BehaviorSubject for component communication
6. **Dynamic Components** - Create components at runtime

**Key Takeaways:**
- Use lifecycle hooks appropriately (ngOnInit for initialization, ngOnDestroy for cleanup)
- Prefer async pipe to avoid manual subscription management
- Use OnPush change detection for better performance
- Keep components focused with single responsibility
- Separate smart (container) and presentational components
- Always unsubscribe from observables to prevent memory leaks

Mastering component architecture enables you to build scalable, maintainable Angular applications like TheDispatch blog platform.
