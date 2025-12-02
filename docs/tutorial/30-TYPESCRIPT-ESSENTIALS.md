# TypeScript Essentials for Angular

## Overview

This document covers **TypeScript features** that are essential for Angular development. We'll use examples from **your Dispatch application** to show how TypeScript enables Angular's powerful features.

**What you'll learn**:
- TypeScript fundamentals (types, interfaces, classes)
- Modern TypeScript features (signals, decorators, generics)
- TypeScript configuration for Angular
- Type safety and benefits

**Your Angular version**: Angular 20.3.7 with TypeScript 5.9.2

---

## 1. Why TypeScript for Angular?

### JavaScript vs TypeScript

**JavaScript** (what browsers understand):
```javascript
// JavaScript - no type safety
let user = { name: "John", age: 25 };
user.age = "twenty-five"; // ❌ Bug! String assigned to number
// JavaScript allows this - runtime error later
```

**TypeScript** (what you write):
```typescript
// TypeScript - type safety
interface User {
  name: string;
  age: number;
}

let user: User = { name: "John", age: 25 };
user.age = "twenty-five"; // ❌ Compile error! Type 'string' not assignable to type 'number'
// Caught at compile time, not runtime
```

**Benefits**:
- Catch errors before running code
- Better IDE support (autocomplete, refactoring)
- Self-documenting code (types as documentation)
- Easier refactoring

---

## 2. Basic Types

### Primitive Types

```typescript
// From your AuthService
readonly isLoggedIn = signal(false);  // boolean
readonly currentUser = signal<UserResponse | null>(null);  // object or null

// Basic types
let username: string = "john";
let age: number = 25;
let isActive: boolean = true;
let data: any = "anything";  // ❌ Avoid 'any'!
let value: unknown = "something";  // ✅ Better than 'any'
```

### Arrays and Objects

```typescript
// From your PostDetailComponent
protected readonly reportCategories = [
  'Harassment or bullying',
  'Spam or misleading',
  'Hate speech',
  // ...
]; // Type: string[]

// Explicit typing
let usernames: string[] = ["john", "jane"];
let ages: number[] = [25, 30];
let mixed: (string | number)[] = ["john", 25];  // Union type

// Array of objects
let users: User[] = [
  { name: "John", age: 25 },
  { name: "Jane", age: 30 }
];
```

### null and undefined

```typescript
// From your PostDetailComponent
protected readonly post = signal<PostResponse | null>(null);  // Can be Post or null

// Strict null checking (enabled in tsconfig.json)
let name: string = "John";
name = null;  // ❌ Error: Type 'null' is not assignable to type 'string'

let nullableName: string | null = "John";
nullableName = null;  // ✅ OK

// Optional chaining (TypeScript 3.7+)
const postId = this.post()?.id;  // Returns number | undefined
const authorName = this.post()?.author;  // Safe access
```

---

## 3. Interfaces and Types

### Interfaces

```typescript
// Your models.ts file
export interface UserResponse {
  id: number;
  username: string;
  email: string;
  avatar?: string;  // Optional property
  role: 'USER' | 'ADMIN';  // Union literal type
  banned: boolean;
  createdAt: Date;
}

export interface PostResponse {
  id: number;
  author: string;
  authorAvatar: string;
  title: string;
  content: string;
  mediaType: string | null;
  mediaUrl: string | null;
  hidden: boolean;
  comments: CommentResponse[];
  createdAt: Date;
  updatedAt: Date;
  likeCount: number;
  likedBy: string[];
}

export interface AuthResponse {
  token: string;  // Not used in cookie-based auth
  user: UserResponse;
}
```

### Type Aliases

```typescript
// Type aliases vs interfaces
type UserRole = 'USER' | 'ADMIN';  // Union type
type UserId = number;  // Alias for clarity

// Type alias for function
type EventHandler = (event: Event) => void;

// Interface vs Type (when to use what?)
// Interface: For object shapes, extendable
interface User {
  name: string;
}

interface Admin extends User {  // ✅ Can extend
  permissions: string[];
}

// Type: For unions, intersections, primitives
type Status = 'pending' | 'approved' | 'rejected';  // ✅ Union
type Point = { x: number; y: number };
type Circle = Point & { radius: number };  // ✅ Intersection
```

### Readonly and Optional

```typescript
// From your component
protected readonly currentUser = this.authService.currentUser;
// readonly = can't reassign (but can mutate if object)

interface Config {
  readonly apiUrl: string;  // Can't be changed after creation
  timeout?: number;  // Optional (can be undefined)
}

const config: Config = {
  apiUrl: "http://localhost:8080",
  // timeout not required
};

config.apiUrl = "new url";  // ❌ Error: Cannot assign to 'apiUrl' because it is a read-only property
```

---

## 4. Classes

### Basic Class Syntax

```typescript
// Traditional Angular service (class-based)
export class AuthService {
  private readonly http = inject(HttpClient);  // Private field
  private readonly baseUrl = `${environment.apiUrl}/auth`;  // Private readonly
  readonly isLoggedIn = signal(false);  // Public readonly
  readonly currentUser = signal<UserResponse | null>(null);

  // Method
  login(request: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.baseUrl}/login`, request);
  }
}
```

### Access Modifiers

```typescript
class Example {
  public publicField = "accessible anywhere";  // Default
  protected protectedField = "accessible in class and subclasses";
  private privateField = "accessible only in this class";
  readonly readonlyField = "can't be reassigned";

  private readonly http = inject(HttpClient);  // Common pattern

  public doSomething() { }  // Public method
  private helperMethod() { }  // Private helper
}
```

### Constructors

```typescript
// Old way (before inject())
class OldAuthService {
  constructor(private http: HttpClient) {
    // 'private http' creates a private field automatically
  }
}

// New way (with inject() - your app uses this)
class NewAuthService {
  private readonly http = inject(HttpClient);

  constructor() {
    // No parameters needed
    // Logic can go here
  }
}
```

---

## 5. Generics

### What are Generics?

Generics allow you to write **reusable code** that works with multiple types.

```typescript
// Without generics
function getFirstString(arr: string[]): string {
  return arr[0];
}

function getFirstNumber(arr: number[]): number {
  return arr[0];
}

// With generics - ONE function for all types!
function getFirst<T>(arr: T[]): T {
  return arr[0];
}

const firstString = getFirst(["a", "b"]);  // Type: string
const firstNumber = getFirst([1, 2, 3]);   // Type: number
```

### Generics in Your Application

```typescript
// HttpClient uses generics
this.http.get<UserResponse>('/users/me');
// T = UserResponse

this.http.post<AuthResponse>('/auth/login', request);
// T = AuthResponse

// Signal with generic type
const post = signal<PostResponse | null>(null);
// T = PostResponse | null
```

### Observable Generics (RxJS)

```typescript
// From your AuthService
checkAuth(): Observable<UserResponse> {
  return this.http.get<UserResponse>(`${environment.apiUrl}/users/me`)
    .pipe(
      tap(user => {
        this.isLoggedIn.set(true);
        this.currentUser.set(user);
      })
    );
}

// Observable<T> = stream of values of type T
// Observable<UserResponse> = stream of UserResponse objects
```

### Creating Generic Functions

```typescript
// Generic function from your code pattern
function updateSignal<T>(signal: WritableSignal<T>, value: T): void {
  signal.set(value);
}

// Usage
const count = signal(0);
updateSignal(count, 5);  // T inferred as number

const user = signal<UserResponse | null>(null);
updateSignal(user, { id: 1, username: "john", ... });  // T inferred as UserResponse | null
```

---

## 6. Union and Intersection Types

### Union Types (OR)

```typescript
// From your models
mediaType: string | null;  // Can be string OR null

// Multiple types
type Status = 'pending' | 'approved' | 'rejected';  // Only these three strings

function handleValue(value: string | number) {
  if (typeof value === 'string') {
    console.log(value.toUpperCase());  // TypeScript knows it's string here
  } else {
    console.log(value.toFixed(2));  // TypeScript knows it's number here
  }
}
```

### Intersection Types (AND)

```typescript
// Combine multiple types
type Person = {
  name: string;
  age: number;
};

type Employee = {
  employeeId: number;
  department: string;
};

type EmployeePerson = Person & Employee;  // Has ALL properties
// {
//   name: string;
//   age: number;
//   employeeId: number;
//   department: string;
// }
```

---

## 7. Type Guards and Narrowing

### typeof Type Guards

```typescript
function formatValue(value: string | number): string {
  if (typeof value === 'string') {
    return value.toUpperCase();  // TypeScript knows: string
  }
  return value.toFixed(2);  // TypeScript knows: number
}
```

### Custom Type Guards

```typescript
// Check if error is a specific type
function isHttpError(error: unknown): error is { status: number; message: string } {
  return (
    typeof error === 'object' &&
    error !== null &&
    'status' in error &&
    'message' in error
  );
}

// Usage
try {
  // some code
} catch (error) {
  if (isHttpError(error)) {
    console.log(error.status);  // TypeScript knows it's safe
    console.log(error.message);
  }
}
```

### Truthiness Narrowing

```typescript
// From your PostDetailComponent
const post = this.post();
if (!post?.id || !content.trim()) {
  return;  // Early return if post is null/undefined
}

// After this check, TypeScript knows post is defined
this.apiService.createComment(post.id, { content });  // Safe!
```

---

## 8. Decorators (Angular-specific)

### What are Decorators?

Decorators are **functions** that modify classes, methods, or properties. Angular uses them extensively.

```typescript
// @Component decorator
@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, CommonModule],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class AppComponent { }

// @Injectable decorator
@Injectable({
  providedIn: 'root'  // Singleton service
})
export class AuthService { }
```

### Decorator Syntax

```typescript
// A decorator is just a function
function MyDecorator(target: any) {
  console.log('Decorator called on:', target);
}

@MyDecorator
class MyClass { }

// Angular decorators take configuration
@Component({  // @Component is a decorator factory
  selector: 'app-user',
  // ...
})
```

---

## 9. Modern TypeScript Features (Your App Uses These!)

### Signals (Angular 16+)

```typescript
// From your components
protected readonly post = signal<PostResponse | null>(null);
protected readonly isLiked = signal(false);
protected readonly submittingComment = signal(false);

// Set value
this.post.set(newPost);

// Update based on current value
this.isLiked.update(v => !v);

// Read value
const currentPost = this.post();  // Call it like a function

// Computed signals
const likeCount = computed(() => this.post()?.likeCount ?? 0);
```

### Optional Chaining (?.)

```typescript
// From your PostDetailComponent
const postId = this.post()?.id;  // Returns number | undefined
const authorName = this.post()?.author;  // Safely access nested property

// Without optional chaining:
const postId = this.post() ? this.post()!.id : undefined;  // Verbose!
```

### Nullish Coalescing (??)

```typescript
// ?? returns right side only if left is null/undefined
const timeout = config.timeout ?? 5000;  // Default to 5000 if null/undefined

// Different from ||
const count = 0;
const value1 = count || 10;   // 10 (0 is falsy)
const value2 = count ?? 10;   // 0 (0 is not null/undefined)
```

### Template Literal Types

```typescript
// Type-safe string templates
type HttpMethod = 'GET' | 'POST' | 'PUT' | 'DELETE';
type Endpoint = `/users/${string}` | `/posts/${string}`;

const endpoint: Endpoint = `/users/123`;  // ✅ OK
const invalid: Endpoint = `/invalid`;     // ❌ Error
```

---

## 10. TypeScript Configuration (tsconfig.json)

### Your Application's tsconfig.json

```json
{
  "compilerOptions": {
    "target": "ES2022",  // JavaScript version to compile to
    "module": "ES2022",  // Module system
    "lib": ["ES2022", "dom"],  // Available APIs
    "strict": true,  // Enable ALL strict type checking
    "strictNullChecks": true,  // null/undefined must be explicit
    "noImplicitAny": true,  // Can't have implicit 'any'
    "strictPropertyInitialization": true,  // Properties must be initialized
    "esModuleInterop": true,  // Better import compatibility
    "skipLibCheck": true,  // Skip type checking of declaration files
    "experimentalDecorators": true,  // Enable decorators
    "emitDecoratorMetadata": true  // Emit metadata for decorators
  }
}
```

### Important Compiler Options

```typescript
// strictNullChecks: true
let name: string = null;  // ❌ Error
let nullable: string | null = null;  // ✅ OK

// noImplicitAny: true
function add(a, b) {  // ❌ Error: Parameter 'a' implicitly has an 'any' type
  return a + b;
}

function add(a: number, b: number) {  // ✅ OK
  return a + b;
}

// strictPropertyInitialization: true
class User {
  name: string;  // ❌ Error: Property 'name' has no initializer

  // Solutions:
  name: string = "";  // ✅ Initialize
  name!: string;  // ✅ Definite assignment assertion
  name?: string;  // ✅ Make optional
}
```

---

## 11. Advanced Types from Your Application

### Utility Types

```typescript
// Partial<T> - all properties optional
function updateUser(id: number, updates: Partial<UserResponse>) {
  // updates can have any subset of UserResponse properties
}

updateUser(1, { username: "newname" });  // ✅ OK

// Pick<T, K> - select specific properties
type UserBasic = Pick<UserResponse, 'id' | 'username' | 'avatar'>;
// { id: number; username: string; avatar?: string; }

// Omit<T, K> - exclude specific properties
type UserWithoutPassword = Omit<UserResponse, 'password'>;

// Record<K, V> - object with keys K and values V
type UserMap = Record<number, UserResponse>;  // { [id: number]: UserResponse }
```

### Mapped Types

```typescript
// Make all properties readonly
type Readonly<T> = {
  readonly [P in keyof T]: T[P];
};

type ReadonlyUser = Readonly<UserResponse>;

// Make all properties optional
type Optional<T> = {
  [P in keyof T]?: T[P];
};
```

---

## 12. Real Examples from Your Codebase

### Example 1: Auth Service (Type Safety Throughout)

```typescript
@Injectable({
  providedIn: 'root'
})
export class AuthService {
  // Signals with explicit types
  readonly isLoggedIn = signal(false);  // WritableSignal<boolean>
  readonly currentUser = signal<UserResponse | null>(null);  // WritableSignal<UserResponse | null>

  // Observable with generic type
  login(request: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.baseUrl}/login`, request, {
      withCredentials: true
    }).pipe(
      tap(response => {
        // response is typed as AuthResponse
        this.isLoggedIn.set(true);
        this.checkAuth().subscribe();
      })
    );
  }

  // Return type explicitly defined
  getCurrentUser(): UserResponse | null {
    return this.currentUser();
  }
}
```

### Example 2: Post Component (Complex Types)

```typescript
@Component({
  selector: 'app-post-detail',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PostDetailComponent {
  // Signal with union type
  protected readonly post = signal<PostResponse | null>(null);

  // Signal with object type
  protected readonly commentEdit = signal<{ id: number; content: string } | null>(null);

  // Signal with Set type
  protected readonly failedAvatars = signal<Set<string>>(new Set());

  // Method with explicit return type
  getTimeAgo(date: string | Date): string {
    const now = new Date();
    const postDate = new Date(date);
    const diffMins = Math.floor((now.getTime() - postDate.getTime()) / 60000);

    if (diffMins < 1) return 'just now';
    if (diffMins < 60) return `${diffMins}m ago`;
    // ...
    return postDate.toLocaleDateString();
  }

  // Type guard usage
  isPostAuthor(): boolean {
    const post = this.post();
    const user = this.currentUser();
    return post?.author === user?.username;  // Optional chaining
  }
}
```

---

## 13. Common TypeScript Patterns in Angular

### Pattern 1: Dependency Injection with inject()

```typescript
// Modern approach (your app uses this)
export class MyComponent {
  private readonly apiService = inject(ApiService);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  // Constructor is clean
  constructor() {
    // Logic here
  }
}
```

### Pattern 2: Signal State Management

```typescript
// Reactive state with signals
export class MyComponent {
  protected readonly loading = signal(false);
  protected readonly data = signal<Data[]>([]);
  protected readonly error = signal<string | null>(null);

  loadData() {
    this.loading.set(true);
    this.apiService.getData().subscribe({
      next: data => {
        this.data.set(data);
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

### Pattern 3: Type-Safe HTTP Requests

```typescript
// Generic HTTP methods
getData<T>(url: string): Observable<T> {
  return this.http.get<T>(url);
}

// Usage with type inference
this.getData<UserResponse[]>('/users').subscribe(users => {
  // users is typed as UserResponse[]
  users.forEach(user => {
    console.log(user.username);  // Autocomplete works!
  });
});
```

---

## 14. Key Takeaways

### TypeScript Benefits

- ✅ **Type safety**: Catch errors at compile time
- ✅ **Better IDE support**: Autocomplete, refactoring, navigation
- ✅ **Self-documenting**: Types serve as documentation
- ✅ **Refactoring confidence**: Compiler catches breaking changes
- ✅ **Modern features**: Optional chaining, nullish coalescing, signals

### Best Practices

- ✅ Always enable `strict` mode in tsconfig.json
- ✅ Avoid `any` type (use `unknown` if needed)
- ✅ Use interfaces for object shapes
- ✅ Use type aliases for unions and primitives
- ✅ Leverage type inference (don't over-annotate)
- ✅ Use generics for reusable code
- ❌ Don't use `as any` to bypass type checking
- ❌ Don't disable strict checks

### TypeScript in Angular

- **Components**: Classes with decorators
- **Services**: Classes with dependency injection
- **Interfaces**: Model data structures
- **Generics**: Type-safe HTTP requests and Observables
- **Signals**: Reactive state management with types

---

## What's Next?

Continue to `31-ANGULAR-FUNDAMENTALS.md` to learn Angular core concepts using your Dispatch application.

**Completed**:
- ✅ TypeScript fundamentals
- ✅ Types, interfaces, classes
- ✅ Generics and advanced types
- ✅ Modern TypeScript features

**Next**:
- Angular architecture
- Components and templates
- Services and dependency injection
- Routing and navigation
