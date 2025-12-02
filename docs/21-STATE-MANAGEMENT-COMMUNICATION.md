# State Management and Component Communication

## Table of Contents
1. [State Management Overview](#state-management-overview)
2. [Component Communication Patterns](#component-communication-patterns)
3. [Service-Based State Management](#service-based-state-management)
4. [RxJS for State Management](#rxjs-for-state-management)
5. [State Management Best Practices](#state-management-best-practices)
6. [Advanced Patterns](#advanced-patterns)
7. [TheDispatch State Management Example](#thedispatch-state-management-example)

---

## State Management Overview

State management is how your application stores, accesses, and updates data. Good state management makes your app predictable, testable, and maintainable.

### Types of State

1. **Component State**: Data owned by a single component
2. **Shared State**: Data shared between multiple components
3. **Application State**: Global data (user info, settings)
4. **Route State**: Data tied to the current route
5. **Server State**: Data synchronized with backend

### State Management Strategies

```typescript
// 1. Local Component State
export class CounterComponent {
  count: number = 0;  // Simple component state

  increment(): void {
    this.count++;
  }
}

// 2. Service State
export class UserService {
  private user: User | null = null;  // Shared state

  getUser(): User | null {
    return this.user;
  }

  setUser(user: User): void {
    this.user = user;
  }
}

// 3. Observable State
export class DataService {
  private dataSubject = new BehaviorSubject<Data[]>([]);
  data$ = this.dataSubject.asObservable();

  updateData(data: Data[]): void {
    this.dataSubject.next(data);
  }
}
```

---

## Component Communication Patterns

### 1. Parent to Child (@Input)

The simplest form of communication - passing data down.

```typescript
// parent.component.ts
export class ParentComponent {
  userName: string = 'John Smith';
  userAge: number = 25;
  userProfile: UserProfile = {
    email: 'john@example.com',
    role: 'admin'
  };
}
```

```html
<!-- parent.component.html -->
<app-child
  [name]="userName"
  [age]="userAge"
  [profile]="userProfile">
</app-child>
```

```typescript
// child.component.ts
export class ChildComponent {
  @Input() name!: string;
  @Input() age!: number;
  @Input() profile!: UserProfile;

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['name']) {
      console.log('Name changed to:', changes['name'].currentValue);
    }
  }
}
```

### 2. Child to Parent (@Output)

Child emits events that parent handles.

```typescript
// child.component.ts
export class ChildComponent {
  @Output() dataSubmitted = new EventEmitter<FormData>();
  @Output() itemDeleted = new EventEmitter<number>();
  @Output() statusChanged = new EventEmitter<Status>();

  submitForm(data: FormData): void {
    // Validate and emit
    if (this.isValid(data)) {
      this.dataSubmitted.emit(data);
    }
  }

  deleteItem(id: number): void {
    this.itemDeleted.emit(id);
  }

  changeStatus(status: Status): void {
    this.statusChanged.emit(status);
  }

  private isValid(data: FormData): boolean {
    return true;  // Validation logic
  }
}
```

```html
<!-- parent.component.html -->
<app-child
  (dataSubmitted)="handleSubmit($event)"
  (itemDeleted)="handleDelete($event)"
  (statusChanged)="handleStatusChange($event)">
</app-child>
```

```typescript
// parent.component.ts
export class ParentComponent {
  handleSubmit(data: FormData): void {
    console.log('Form submitted:', data);
    this.saveData(data);
  }

  handleDelete(id: number): void {
    console.log('Delete item:', id);
    this.deleteItem(id);
  }

  handleStatusChange(status: Status): void {
    console.log('Status changed:', status);
    this.updateStatus(status);
  }

  private saveData(data: FormData): void {
    // Save logic
  }

  private deleteItem(id: number): void {
    // Delete logic
  }

  private updateStatus(status: Status): void {
    // Update logic
  }
}
```

### 3. Sibling Communication via Parent

Siblings communicate through their common parent.

```typescript
// parent.component.ts
export class ParentComponent {
  selectedUser: User | null = null;

  onUserSelected(user: User): void {
    this.selectedUser = user;
  }
}
```

```html
<!-- parent.component.html -->
<app-user-list (userSelected)="onUserSelected($event)"></app-user-list>
<app-user-detail [user]="selectedUser"></app-user-detail>
```

### 4. Sibling Communication via Service

For complex scenarios, use a shared service.

```typescript
// user-selection.service.ts
@Injectable({ providedIn: 'root' })
export class UserSelectionService {
  private selectedUserSubject = new BehaviorSubject<User | null>(null);
  selectedUser$ = this.selectedUserSubject.asObservable();

  selectUser(user: User): void {
    this.selectedUserSubject.next(user);
  }

  clearSelection(): void {
    this.selectedUserSubject.next(null);
  }

  getCurrentUser(): User | null {
    return this.selectedUserSubject.value;
  }
}

// user-list.component.ts
export class UserListComponent {
  constructor(private selectionService: UserSelectionService) {}

  onUserClick(user: User): void {
    this.selectionService.selectUser(user);
  }
}

// user-detail.component.ts
export class UserDetailComponent implements OnInit {
  user: User | null = null;

  constructor(private selectionService: UserSelectionService) {}

  ngOnInit(): void {
    this.selectionService.selectedUser$.subscribe(user => {
      this.user = user;
    });
  }
}
```

### 5. ViewChild/ViewChildren

Parent directly accesses child component.

```typescript
// parent.component.ts
export class ParentComponent implements AfterViewInit {
  @ViewChild(ChildComponent) child!: ChildComponent;

  ngAfterViewInit(): void {
    // Directly call child methods
    this.child.initialize();
    this.child.loadData();

    // Access child properties
    console.log('Child status:', this.child.status);
  }

  refreshChild(): void {
    this.child.refresh();
  }
}

// child.component.ts
export class ChildComponent {
  status: string = 'idle';

  initialize(): void {
    console.log('Child initialized');
  }

  loadData(): void {
    console.log('Loading data');
  }

  refresh(): void {
    console.log('Refreshing');
  }
}
```

### 6. Template Reference Variables

Access child from template.

```html
<!-- parent.component.html -->
<app-timer #timer></app-timer>

<div class="controls">
  <button (click)="timer.start()">Start</button>
  <button (click)="timer.stop()">Stop</button>
  <button (click)="timer.reset()">Reset</button>
</div>

<p>Elapsed: {{ timer.seconds }} seconds</p>
```

---

## Service-Based State Management

Services provide a centralized place to manage state.

### Simple State Service

```typescript
// user-state.service.ts
@Injectable({ providedIn: 'root' })
export class UserStateService {
  private currentUser: User | null = null;

  setUser(user: User): void {
    this.currentUser = user;
    localStorage.setItem('user', JSON.stringify(user));
  }

  getUser(): User | null {
    if (!this.currentUser) {
      const stored = localStorage.getItem('user');
      this.currentUser = stored ? JSON.parse(stored) : null;
    }
    return this.currentUser;
  }

  clearUser(): void {
    this.currentUser = null;
    localStorage.removeItem('user');
  }

  isAuthenticated(): boolean {
    return this.currentUser !== null;
  }
}
```

### Observable State Service

```typescript
// post-state.service.ts
@Injectable({ providedIn: 'root' })
export class PostStateService {
  private postsSubject = new BehaviorSubject<Post[]>([]);
  posts$ = this.postsSubject.asObservable();

  private loadingSubject = new BehaviorSubject<boolean>(false);
  loading$ = this.loadingSubject.asObservable();

  private errorSubject = new BehaviorSubject<string | null>(null);
  error$ = this.errorSubject.asObservable();

  constructor(private postService: PostService) {}

  loadPosts(): void {
    this.loadingSubject.next(true);
    this.errorSubject.next(null);

    this.postService.getAllPosts().subscribe({
      next: (posts) => {
        this.postsSubject.next(posts);
        this.loadingSubject.next(false);
      },
      error: (error) => {
        this.errorSubject.next('Failed to load posts');
        this.loadingSubject.next(false);
      }
    });
  }

  addPost(post: Post): void {
    const currentPosts = this.postsSubject.value;
    this.postsSubject.next([...currentPosts, post]);
  }

  updatePost(updatedPost: Post): void {
    const currentPosts = this.postsSubject.value;
    const index = currentPosts.findIndex(p => p.id === updatedPost.id);

    if (index !== -1) {
      const newPosts = [...currentPosts];
      newPosts[index] = updatedPost;
      this.postsSubject.next(newPosts);
    }
  }

  deletePost(postId: number): void {
    const currentPosts = this.postsSubject.value;
    this.postsSubject.next(currentPosts.filter(p => p.id !== postId));
  }

  getPostById(id: number): Observable<Post | undefined> {
    return this.posts$.pipe(
      map(posts => posts.find(p => p.id === id))
    );
  }
}
```

### Usage in Components

```typescript
// post-list.component.ts
export class PostListComponent implements OnInit {
  posts$ = this.postState.posts$;
  loading$ = this.postState.loading$;
  error$ = this.postState.error$;

  constructor(private postState: PostStateService) {}

  ngOnInit(): void {
    this.postState.loadPosts();
  }

  deletePost(postId: number): void {
    if (confirm('Delete this post?')) {
      this.postState.deletePost(postId);
    }
  }
}
```

```html
<!-- post-list.component.html -->
<div class="post-list">
  <div *ngIf="loading$ | async" class="loading">Loading...</div>
  <div *ngIf="error$ | async as error" class="error">{{ error }}</div>

  <div *ngIf="posts$ | async as posts" class="posts">
    <app-post-card
      *ngFor="let post of posts"
      [post]="post"
      (delete)="deletePost(post.id)">
    </app-post-card>
  </div>
</div>
```

---

## RxJS for State Management

### Subject vs BehaviorSubject vs ReplaySubject

```typescript
// 1. Subject - No initial value, no replay
const subject = new Subject<number>();

subject.subscribe(val => console.log('Sub 1:', val));
subject.next(1);  // Sub 1: 1
subject.next(2);  // Sub 1: 2
subject.subscribe(val => console.log('Sub 2:', val));  // Doesn't get previous values
subject.next(3);  // Sub 1: 3, Sub 2: 3

// 2. BehaviorSubject - Has initial value, replays last value
const behaviorSubject = new BehaviorSubject<number>(0);

behaviorSubject.subscribe(val => console.log('Sub 1:', val));  // Sub 1: 0 (initial)
behaviorSubject.next(1);  // Sub 1: 1
behaviorSubject.next(2);  // Sub 1: 2
behaviorSubject.subscribe(val => console.log('Sub 2:', val));  // Sub 2: 2 (last value)
behaviorSubject.next(3);  // Sub 1: 3, Sub 2: 3

// 3. ReplaySubject - Replays specified number of values
const replaySubject = new ReplaySubject<number>(2);  // Replay last 2 values

replaySubject.subscribe(val => console.log('Sub 1:', val));
replaySubject.next(1);  // Sub 1: 1
replaySubject.next(2);  // Sub 1: 2
replaySubject.next(3);  // Sub 1: 3
replaySubject.subscribe(val => console.log('Sub 2:', val));
// Sub 2: 2, Sub 2: 3 (replays last 2)
```

### State Store Pattern

```typescript
// state.model.ts
export interface AppState {
  user: UserState;
  posts: PostState;
  ui: UIState;
}

export interface UserState {
  currentUser: User | null;
  isAuthenticated: boolean;
  loading: boolean;
  error: string | null;
}

export interface PostState {
  posts: Post[];
  selectedPost: Post | null;
  loading: boolean;
  error: string | null;
}

export interface UIState {
  sidebarOpen: boolean;
  theme: 'light' | 'dark';
  notifications: Notification[];
}

// state.service.ts
@Injectable({ providedIn: 'root' })
export class StateService {
  private state = new BehaviorSubject<AppState>({
    user: {
      currentUser: null,
      isAuthenticated: false,
      loading: false,
      error: null
    },
    posts: {
      posts: [],
      selectedPost: null,
      loading: false,
      error: null
    },
    ui: {
      sidebarOpen: false,
      theme: 'light',
      notifications: []
    }
  });

  // Expose state as observable
  state$ = this.state.asObservable();

  // Selectors - derived state streams
  user$ = this.state$.pipe(map(state => state.user));
  posts$ = this.state$.pipe(map(state => state.posts));
  ui$ = this.state$.pipe(map(state => state.ui));

  currentUser$ = this.user$.pipe(map(user => user.currentUser));
  isAuthenticated$ = this.user$.pipe(map(user => user.isAuthenticated));
  allPosts$ = this.posts$.pipe(map(posts => posts.posts));
  selectedPost$ = this.posts$.pipe(map(posts => posts.selectedPost));

  // Get current state value
  get currentState(): AppState {
    return this.state.value;
  }

  // Update user state
  setUser(user: User): void {
    this.updateState({
      user: {
        ...this.currentState.user,
        currentUser: user,
        isAuthenticated: true,
        loading: false,
        error: null
      }
    });
  }

  clearUser(): void {
    this.updateState({
      user: {
        currentUser: null,
        isAuthenticated: false,
        loading: false,
        error: null
      }
    });
  }

  setUserLoading(loading: boolean): void {
    this.updateState({
      user: {
        ...this.currentState.user,
        loading
      }
    });
  }

  setUserError(error: string): void {
    this.updateState({
      user: {
        ...this.currentState.user,
        error,
        loading: false
      }
    });
  }

  // Update posts state
  setPosts(posts: Post[]): void {
    this.updateState({
      posts: {
        ...this.currentState.posts,
        posts,
        loading: false,
        error: null
      }
    });
  }

  selectPost(post: Post): void {
    this.updateState({
      posts: {
        ...this.currentState.posts,
        selectedPost: post
      }
    });
  }

  addPost(post: Post): void {
    const currentPosts = this.currentState.posts.posts;
    this.updateState({
      posts: {
        ...this.currentState.posts,
        posts: [...currentPosts, post]
      }
    });
  }

  updatePost(updatedPost: Post): void {
    const currentPosts = this.currentState.posts.posts;
    const index = currentPosts.findIndex(p => p.id === updatedPost.id);

    if (index !== -1) {
      const newPosts = [...currentPosts];
      newPosts[index] = updatedPost;
      this.updateState({
        posts: {
          ...this.currentState.posts,
          posts: newPosts
        }
      });
    }
  }

  deletePost(postId: number): void {
    const currentPosts = this.currentState.posts.posts;
    this.updateState({
      posts: {
        ...this.currentState.posts,
        posts: currentPosts.filter(p => p.id !== postId)
      }
    });
  }

  // Update UI state
  toggleSidebar(): void {
    this.updateState({
      ui: {
        ...this.currentState.ui,
        sidebarOpen: !this.currentState.ui.sidebarOpen
      }
    });
  }

  setTheme(theme: 'light' | 'dark'): void {
    this.updateState({
      ui: {
        ...this.currentState.ui,
        theme
      }
    });
  }

  addNotification(notification: Notification): void {
    const notifications = [...this.currentState.ui.notifications, notification];
    this.updateState({
      ui: {
        ...this.currentState.ui,
        notifications
      }
    });
  }

  removeNotification(id: string): void {
    const notifications = this.currentState.ui.notifications.filter(n => n.id !== id);
    this.updateState({
      ui: {
        ...this.currentState.ui,
        notifications
      }
    });
  }

  // Helper to update state
  private updateState(partial: Partial<AppState>): void {
    this.state.next({
      ...this.currentState,
      ...partial
    });
  }
}
```

### Usage in Components

```typescript
// app.component.ts
export class AppComponent implements OnInit {
  user$ = this.stateService.currentUser$;
  isAuthenticated$ = this.stateService.isAuthenticated$;
  sidebarOpen$ = this.stateService.ui$.pipe(map(ui => ui.sidebarOpen));
  theme$ = this.stateService.ui$.pipe(map(ui => ui.theme));

  constructor(private stateService: StateService) {}

  ngOnInit(): void {
    // Load initial data
    this.loadUserData();
  }

  toggleSidebar(): void {
    this.stateService.toggleSidebar();
  }

  toggleTheme(): void {
    const currentTheme = this.stateService.currentState.ui.theme;
    const newTheme = currentTheme === 'light' ? 'dark' : 'light';
    this.stateService.setTheme(newTheme);
  }

  private loadUserData(): void {
    // Load user from storage or API
  }
}

// post-list.component.ts
export class PostListComponent implements OnInit {
  posts$ = this.stateService.allPosts$;
  loading$ = this.stateService.posts$.pipe(map(posts => posts.loading));
  error$ = this.stateService.posts$.pipe(map(posts => posts.error));

  constructor(
    private stateService: StateService,
    private postService: PostService
  ) {}

  ngOnInit(): void {
    this.loadPosts();
  }

  loadPosts(): void {
    this.postService.getAllPosts().subscribe({
      next: (posts) => this.stateService.setPosts(posts),
      error: (error) => console.error('Failed to load posts', error)
    });
  }

  selectPost(post: Post): void {
    this.stateService.selectPost(post);
  }

  deletePost(postId: number): void {
    this.postService.deletePost(postId).subscribe({
      next: () => this.stateService.deletePost(postId),
      error: (error) => console.error('Failed to delete post', error)
    });
  }
}
```

---

## State Management Best Practices

### 1. Immutability

Always create new objects instead of mutating existing ones.

```typescript
// Bad - mutating state
this.posts.push(newPost);

// Good - creating new array
this.posts = [...this.posts, newPost];

// Bad - mutating object
this.user.name = 'New Name';

// Good - creating new object
this.user = { ...this.user, name: 'New Name' };

// Bad - mutating nested object
this.user.profile.email = 'new@email.com';

// Good - creating new nested object
this.user = {
  ...this.user,
  profile: {
    ...this.user.profile,
    email: 'new@email.com'
  }
};
```

### 2. Single Source of Truth

Don't duplicate state across multiple services or components.

```typescript
// Bad - duplicated state
export class PostListComponent {
  posts: Post[] = [];  // Local copy
}

export class PostService {
  posts: Post[] = [];  // Another copy - which is correct?
}

// Good - single source
export class PostStateService {
  private postsSubject = new BehaviorSubject<Post[]>([]);
  posts$ = this.postsSubject.asObservable();
}

export class PostListComponent {
  posts$ = this.postState.posts$;  // Reference to single source
}
```

### 3. Unidirectional Data Flow

Data flows down, events flow up.

```typescript
// Parent
export class ParentComponent {
  data: Data[] = [];

  handleUpdate(updatedData: Data): void {
    // Update flows through service
    this.dataService.update(updatedData).subscribe(() => {
      this.loadData();  // Reload from single source
    });
  }
}

// Child
export class ChildComponent {
  @Input() data!: Data;  // Data flows down
  @Output() update = new EventEmitter<Data>();  // Events flow up

  save(): void {
    this.update.emit(this.data);  // Emit, don't modify directly
  }
}
```

### 4. Async Pipe for Subscriptions

Use async pipe to avoid manual subscription management.

```typescript
// Bad - manual subscription
export class Component implements OnInit, OnDestroy {
  posts: Post[] = [];
  private subscription?: Subscription;

  ngOnInit(): void {
    this.subscription = this.postService.getPosts().subscribe(posts => {
      this.posts = posts;
    });
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }
}

// Good - async pipe
export class Component {
  posts$ = this.postService.getPosts();
}
```

```html
<div *ngFor="let post of posts$ | async">
  {{ post.title }}
</div>
```

### 5. Separate Presentation and Logic

Use smart (container) and dumb (presentational) components.

```typescript
// Smart component - handles logic
export class PostContainerComponent {
  posts$ = this.postState.posts$;
  loading$ = this.postState.loading$;

  constructor(
    private postState: PostStateService,
    private postService: PostService
  ) {}

  loadPosts(): void {
    this.postService.getAllPosts().subscribe(posts => {
      this.postState.setPosts(posts);
    });
  }

  deletePost(id: number): void {
    this.postService.deletePost(id).subscribe(() => {
      this.postState.deletePost(id);
    });
  }
}

// Dumb component - handles presentation
@Component({
  selector: 'app-post-list',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class PostListComponent {
  @Input() posts: Post[] = [];
  @Input() loading: boolean = false;
  @Output() delete = new EventEmitter<number>();

  onDelete(id: number): void {
    this.delete.emit(id);
  }
}
```

### 6. Error Handling

Always handle errors in state.

```typescript
@Injectable({ providedIn: 'root' })
export class PostStateService {
  private state = new BehaviorSubject<PostState>({
    posts: [],
    loading: false,
    error: null
  });

  state$ = this.state.asObservable();

  loadPosts(): void {
    this.setState({ loading: true, error: null });

    this.postService.getAllPosts().subscribe({
      next: (posts) => {
        this.setState({ posts, loading: false, error: null });
      },
      error: (error) => {
        console.error('Failed to load posts:', error);
        this.setState({
          loading: false,
          error: 'Failed to load posts. Please try again.'
        });
      }
    });
  }

  private setState(partial: Partial<PostState>): void {
    this.state.next({
      ...this.state.value,
      ...partial
    });
  }
}
```

---

## Advanced Patterns

### Event Bus Pattern

For decoupled communication across the app.

```typescript
// event-bus.service.ts
export interface AppEvent {
  type: string;
  payload?: any;
}

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

// Usage - Emit event
export class PostComponent {
  constructor(private eventBus: EventBusService) {}

  deletePost(id: number): void {
    this.eventBus.emit({
      type: 'POST_DELETED',
      payload: { postId: id }
    });
  }
}

// Usage - Listen to event
export class NotificationComponent implements OnInit {
  constructor(private eventBus: EventBusService) {}

  ngOnInit(): void {
    this.eventBus.on('POST_DELETED').subscribe(event => {
      this.showNotification(`Post ${event.payload.postId} deleted`);
    });
  }

  showNotification(message: string): void {
    // Show notification
  }
}
```

### Command Pattern

Encapsulate actions as objects.

```typescript
// command.interface.ts
export interface Command {
  execute(): void;
  undo(): void;
}

// commands.ts
export class AddPostCommand implements Command {
  constructor(
    private postState: PostStateService,
    private post: Post
  ) {}

  execute(): void {
    this.postState.addPost(this.post);
  }

  undo(): void {
    this.postState.deletePost(this.post.id);
  }
}

export class DeletePostCommand implements Command {
  private deletedPost?: Post;

  constructor(
    private postState: PostStateService,
    private postId: number
  ) {}

  execute(): void {
    // Save post before deleting for undo
    this.postState.getPostById(this.postId).subscribe(post => {
      this.deletedPost = post;
      this.postState.deletePost(this.postId);
    });
  }

  undo(): void {
    if (this.deletedPost) {
      this.postState.addPost(this.deletedPost);
    }
  }
}

// command-manager.service.ts
@Injectable({ providedIn: 'root' })
export class CommandManagerService {
  private history: Command[] = [];
  private currentIndex: number = -1;

  execute(command: Command): void {
    // Remove any commands after current index
    this.history = this.history.slice(0, this.currentIndex + 1);

    // Execute and add to history
    command.execute();
    this.history.push(command);
    this.currentIndex++;
  }

  undo(): void {
    if (this.canUndo()) {
      const command = this.history[this.currentIndex];
      command.undo();
      this.currentIndex--;
    }
  }

  redo(): void {
    if (this.canRedo()) {
      this.currentIndex++;
      const command = this.history[this.currentIndex];
      command.execute();
    }
  }

  canUndo(): boolean {
    return this.currentIndex >= 0;
  }

  canRedo(): boolean {
    return this.currentIndex < this.history.length - 1;
  }
}
```

---

## TheDispatch State Management Example

Complete state management for the blog application.

```typescript
// models/state.model.ts
export interface BlogState {
  auth: AuthState;
  posts: PostsState;
  comments: CommentsState;
  ui: UIState;
}

export interface AuthState {
  user: User | null;
  token: string | null;
  isAuthenticated: boolean;
  loading: boolean;
  error: string | null;
}

export interface PostsState {
  posts: Post[];
  selectedPost: Post | null;
  filter: PostFilter;
  loading: boolean;
  error: string | null;
}

export interface CommentsState {
  commentsByPost: Map<number, Comment[]>;
  loading: boolean;
  error: string | null;
}

export interface UIState {
  sidebarOpen: boolean;
  theme: 'light' | 'dark';
  notifications: Notification[];
}

export interface PostFilter {
  searchQuery: string;
  category: string | null;
  author: string | null;
  sortBy: 'date' | 'title' | 'views';
}

// blog-state.service.ts
@Injectable({ providedIn: 'root' })
export class BlogStateService {
  private state = new BehaviorSubject<BlogState>({
    auth: {
      user: null,
      token: null,
      isAuthenticated: false,
      loading: false,
      error: null
    },
    posts: {
      posts: [],
      selectedPost: null,
      filter: {
        searchQuery: '',
        category: null,
        author: null,
        sortBy: 'date'
      },
      loading: false,
      error: null
    },
    comments: {
      commentsByPost: new Map(),
      loading: false,
      error: null
    },
    ui: {
      sidebarOpen: false,
      theme: 'light',
      notifications: []
    }
  });

  // State observables
  state$ = this.state.asObservable();
  auth$ = this.state$.pipe(map(state => state.auth));
  posts$ = this.state$.pipe(map(state => state.posts));
  comments$ = this.state$.pipe(map(state => state.comments));
  ui$ = this.state$.pipe(map(state => state.ui));

  // Derived selectors
  currentUser$ = this.auth$.pipe(map(auth => auth.user));
  isAuthenticated$ = this.auth$.pipe(map(auth => auth.isAuthenticated));
  allPosts$ = this.posts$.pipe(map(posts => posts.posts));
  filteredPosts$ = this.posts$.pipe(
    map(posts => this.filterPosts(posts.posts, posts.filter))
  );
  selectedPost$ = this.posts$.pipe(map(posts => posts.selectedPost));

  constructor(
    private authService: AuthService,
    private postService: PostService,
    private commentService: CommentService
  ) {
    this.initializeState();
  }

  // Initialize state from storage
  private initializeState(): void {
    const token = localStorage.getItem('token');
    const user = localStorage.getItem('user');

    if (token && user) {
      this.updateState({
        auth: {
          ...this.currentState.auth,
          token,
          user: JSON.parse(user),
          isAuthenticated: true
        }
      });
    }
  }

  // Auth actions
  login(credentials: Credentials): void {
    this.updateState({
      auth: { ...this.currentState.auth, loading: true, error: null }
    });

    this.authService.login(credentials).subscribe({
      next: (response) => {
        localStorage.setItem('token', response.token);
        localStorage.setItem('user', JSON.stringify(response.user));

        this.updateState({
          auth: {
            user: response.user,
            token: response.token,
            isAuthenticated: true,
            loading: false,
            error: null
          }
        });
      },
      error: (error) => {
        this.updateState({
          auth: {
            ...this.currentState.auth,
            loading: false,
            error: 'Login failed. Please check your credentials.'
          }
        });
      }
    });
  }

  logout(): void {
    localStorage.removeItem('token');
    localStorage.removeItem('user');

    this.updateState({
      auth: {
        user: null,
        token: null,
        isAuthenticated: false,
        loading: false,
        error: null
      }
    });
  }

  // Post actions
  loadPosts(): void {
    this.updateState({
      posts: { ...this.currentState.posts, loading: true, error: null }
    });

    this.postService.getAllPosts().subscribe({
      next: (posts) => {
        this.updateState({
          posts: {
            ...this.currentState.posts,
            posts,
            loading: false,
            error: null
          }
        });
      },
      error: (error) => {
        this.updateState({
          posts: {
            ...this.currentState.posts,
            loading: false,
            error: 'Failed to load posts'
          }
        });
      }
    });
  }

  selectPost(post: Post): void {
    this.updateState({
      posts: {
        ...this.currentState.posts,
        selectedPost: post
      }
    });
  }

  setPostFilter(filter: Partial<PostFilter>): void {
    this.updateState({
      posts: {
        ...this.currentState.posts,
        filter: {
          ...this.currentState.posts.filter,
          ...filter
        }
      }
    });
  }

  // Comment actions
  loadComments(postId: number): void {
    this.updateState({
      comments: { ...this.currentState.comments, loading: true, error: null }
    });

    this.commentService.getComments(postId).subscribe({
      next: (comments) => {
        const commentsByPost = new Map(this.currentState.comments.commentsByPost);
        commentsByPost.set(postId, comments);

        this.updateState({
          comments: {
            commentsByPost,
            loading: false,
            error: null
          }
        });
      },
      error: (error) => {
        this.updateState({
          comments: {
            ...this.currentState.comments,
            loading: false,
            error: 'Failed to load comments'
          }
        });
      }
    });
  }

  // UI actions
  toggleSidebar(): void {
    this.updateState({
      ui: {
        ...this.currentState.ui,
        sidebarOpen: !this.currentState.ui.sidebarOpen
      }
    });
  }

  setTheme(theme: 'light' | 'dark'): void {
    this.updateState({
      ui: {
        ...this.currentState.ui,
        theme
      }
    });
    localStorage.setItem('theme', theme);
  }

  showNotification(message: string, type: 'success' | 'error' | 'info' = 'info'): void {
    const notification: Notification = {
      id: Date.now().toString(),
      message,
      type,
      timestamp: new Date()
    };

    const notifications = [...this.currentState.ui.notifications, notification];

    this.updateState({
      ui: {
        ...this.currentState.ui,
        notifications
      }
    });

    // Auto-dismiss after 5 seconds
    setTimeout(() => {
      this.dismissNotification(notification.id);
    }, 5000);
  }

  dismissNotification(id: string): void {
    const notifications = this.currentState.ui.notifications.filter(n => n.id !== id);

    this.updateState({
      ui: {
        ...this.currentState.ui,
        notifications
      }
    });
  }

  // Helper methods
  get currentState(): BlogState {
    return this.state.value;
  }

  private updateState(partial: Partial<BlogState>): void {
    this.state.next({
      ...this.currentState,
      ...partial
    });
  }

  private filterPosts(posts: Post[], filter: PostFilter): Post[] {
    return posts
      .filter(post => {
        // Search query
        if (filter.searchQuery) {
          const query = filter.searchQuery.toLowerCase();
          return post.title.toLowerCase().includes(query) ||
                 post.content.toLowerCase().includes(query);
        }
        return true;
      })
      .filter(post => {
        // Category filter
        if (filter.category) {
          return post.category === filter.category;
        }
        return true;
      })
      .filter(post => {
        // Author filter
        if (filter.author) {
          return post.author.username === filter.author;
        }
        return true;
      })
      .sort((a, b) => {
        // Sort
        switch (filter.sortBy) {
          case 'date':
            return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime();
          case 'title':
            return a.title.localeCompare(b.title);
          case 'views':
            return b.views - a.views;
          default:
            return 0;
        }
      });
  }
}
```

---

## Summary

Effective state management is crucial for maintainable Angular applications:

1. **Component Communication** - @Input, @Output, Services
2. **Service-Based State** - Centralized state management
3. **RxJS** - Reactive state with Subjects and Observables
4. **Best Practices** - Immutability, single source of truth, unidirectional flow
5. **Advanced Patterns** - Event bus, command pattern, state stores

**Key Takeaways:**
- Keep state immutable
- Use services for shared state
- Prefer observables over promises for state
- Use async pipe to avoid manual subscriptions
- Separate smart and presentational components
- Handle errors in state management
- Use TypeScript for type-safe state

Mastering state management enables building scalable, predictable applications like TheDispatch blog platform.
