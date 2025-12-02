# Angular Templates and Data Binding

## Table of Contents
1. [Template Syntax Overview](#template-syntax-overview)
2. [Interpolation](#interpolation)
3. [Property Binding](#property-binding)
4. [Event Binding](#event-binding)
5. [Two-Way Data Binding](#two-way-data-binding)
6. [Template Reference Variables](#template-reference-variables)
7. [Structural Directives in Templates](#structural-directives-in-templates)
8. [Attribute Directives](#attribute-directives)
9. [Pipes in Templates](#pipes-in-templates)
10. [Safe Navigation Operator](#safe-navigation-operator)
11. [Template Expressions and Statements](#template-expressions-and-statements)

---

## Template Syntax Overview

Angular templates are HTML files that tell Angular how to render a component. They extend HTML with Angular-specific syntax that enables data binding, directives, and other dynamic behaviors.

### Key Concepts
- **Data Binding**: Synchronization between the component class and the template
- **Directives**: Instructions that modify the DOM structure or behavior
- **Pipes**: Transform data for display in the template
- **Template Expressions**: JavaScript-like code in templates that Angular evaluates

---

## Interpolation

Interpolation is the simplest form of data binding. It renders component properties as text in the template.

### Syntax
```typescript
{{ expression }}
```

### Component Example
```typescript
// user-profile.component.ts
export class UserProfileComponent {
  username: string = 'johnsmith';
  age: number = 25;
  bio: string = 'Angular developer';

  getFullDescription(): string {
    return `${this.username} (${this.age}) - ${this.bio}`;
  }
}
```

### Template Example
```html
<!-- user-profile.component.html -->
<div class="profile">
  <h2>Welcome, {{ username }}!</h2>
  <p>Age: {{ age }}</p>
  <p>Bio: {{ bio }}</p>

  <!-- Can call methods -->
  <p>{{ getFullDescription() }}</p>

  <!-- Can use expressions -->
  <p>Next year you'll be {{ age + 1 }}</p>

  <!-- String concatenation -->
  <p>Username in uppercase: {{ username.toUpperCase() }}</p>
</div>
```

### Rules for Interpolation
1. **Must resolve to a string**: Angular converts the result to a string
2. **No assignments**: Cannot use `=`, `+=`, `-=`, etc.
3. **No new keyword**: Cannot create objects with `new`
4. **No chaining expressions**: Cannot use `;` or `,`
5. **Limited operators**: No bitwise operators, `|` is pipe operator

---

## Property Binding

Property binding sets an element property to a component property value. It flows data from component to template.

### Syntax
```typescript
[property]="expression"
```

### Basic Examples

#### Binding to Element Properties
```typescript
// image-display.component.ts
export class ImageDisplayComponent {
  imageUrl: string = 'assets/logo.png';
  imageAlt: string = 'Company Logo';
  isImageDisabled: boolean = false;
  imageWidth: number = 200;
}
```

```html
<!-- image-display.component.html -->
<img
  [src]="imageUrl"
  [alt]="imageAlt"
  [disabled]="isImageDisabled"
  [width]="imageWidth">

<!-- Alternative syntax using bind- prefix -->
<img
  bind-src="imageUrl"
  bind-alt="imageAlt">
```

#### Binding to Component Properties
```typescript
// parent.component.ts
export class ParentComponent {
  userTitle: string = 'Software Engineer';
  userActive: boolean = true;
}
```

```html
<!-- parent.component.html -->
<app-user-card
  [title]="userTitle"
  [isActive]="userActive">
</app-user-card>
```

```typescript
// user-card.component.ts
export class UserCardComponent {
  @Input() title!: string;
  @Input() isActive!: boolean;
}
```

#### Binding to Attributes
Some HTML attributes don't have corresponding DOM properties. Use `attr.` prefix:

```html
<button [attr.aria-label]="actionLabel">Click</button>
<div [attr.data-id]="userId">User Info</div>
<td [attr.colspan]="columnSpan">Data</td>
```

#### Binding to Classes
```typescript
export class StyleComponent {
  isActive: boolean = true;
  isPremium: boolean = false;
}
```

```html
<!-- Single class binding -->
<div [class.active]="isActive">Status</div>
<div [class.premium]="isPremium">Account</div>

<!-- Multiple classes with object -->
<div [class]="{ active: isActive, premium: isPremium }">User</div>

<!-- Class string binding -->
<div [class]="'btn btn-primary'">Button</div>
```

#### Binding to Styles
```typescript
export class StyleComponent {
  textColor: string = 'blue';
  fontSize: number = 16;
  isLarge: boolean = true;
}
```

```html
<!-- Single style binding -->
<p [style.color]="textColor">Colored text</p>
<p [style.font-size.px]="fontSize">Sized text</p>

<!-- Multiple styles with object -->
<p [style]="{ color: textColor, 'font-size.px': fontSize }">Styled text</p>

<!-- Conditional styles -->
<p [style.font-size.px]="isLarge ? 20 : 14">Conditional size</p>
```

### Property Binding vs Interpolation

```html
<!-- Both achieve the same result for simple strings -->
<img src="{{ imageUrl }}">        <!-- Interpolation -->
<img [src]="imageUrl">             <!-- Property binding - PREFERRED -->

<!-- Property binding is required for non-string values -->
<img [width]="imageWidth">         <!-- Number -->
<button [disabled]="isDisabled">   <!-- Boolean -->
```

**When to use property binding:**
- Setting non-string properties (boolean, number, object)
- Binding to component inputs
- Better performance for complex expressions
- More explicit and readable

---

## Event Binding

Event binding listens to events (clicks, key presses, mouse movements) and executes component methods.

### Syntax
```typescript
(event)="handler($event)"
```

### Basic Examples

```typescript
// counter.component.ts
export class CounterComponent {
  count: number = 0;
  message: string = '';

  increment(): void {
    this.count++;
  }

  decrement(): void {
    this.count--;
  }

  reset(): void {
    this.count = 0;
  }

  handleClick(event: MouseEvent): void {
    console.log('Clicked at:', event.clientX, event.clientY);
    this.message = `Clicked at (${event.clientX}, ${event.clientY})`;
  }
}
```

```html
<!-- counter.component.html -->
<div class="counter">
  <h2>Count: {{ count }}</h2>

  <!-- Simple event binding -->
  <button (click)="increment()">+</button>
  <button (click)="decrement()">-</button>
  <button (click)="reset()">Reset</button>

  <!-- Passing $event object -->
  <button (click)="handleClick($event)">Click Me</button>
  <p>{{ message }}</p>

  <!-- Alternative syntax with on- prefix -->
  <button on-click="increment()">Alternative Syntax</button>
</div>
```

### Common Events

#### Mouse Events
```typescript
export class MouseEventsComponent {
  handleMouseEnter(): void {
    console.log('Mouse entered');
  }

  handleMouseLeave(): void {
    console.log('Mouse left');
  }

  handleDoubleClick(): void {
    console.log('Double clicked');
  }
}
```

```html
<div
  (click)="handleClick()"
  (dblclick)="handleDoubleClick()"
  (mouseenter)="handleMouseEnter()"
  (mouseleave)="handleMouseLeave()"
  (mousemove)="handleMouseMove($event)">
  Hover over me
</div>
```

#### Keyboard Events
```typescript
export class KeyboardComponent {
  searchText: string = '';

  handleKeyPress(event: KeyboardEvent): void {
    console.log('Key pressed:', event.key);
  }

  handleEnter(): void {
    console.log('Enter pressed, searching for:', this.searchText);
  }
}
```

```html
<!-- Keyboard events -->
<input
  type="text"
  (keyup)="handleKeyPress($event)"
  (keyup.enter)="handleEnter()"
  (keydown)="handleKeyDown($event)"
  [(ngModel)]="searchText">

<!-- Specific key filters -->
<input (keyup.escape)="clearSearch()">
<input (keyup.shift.t)="openTab()">
```

#### Form Events
```typescript
export class FormComponent {
  inputValue: string = '';

  handleInput(event: Event): void {
    const target = event.target as HTMLInputElement;
    this.inputValue = target.value;
  }

  handleSubmit(event: Event): void {
    event.preventDefault();
    console.log('Form submitted');
  }

  handleFocus(): void {
    console.log('Input focused');
  }
}
```

```html
<form (submit)="handleSubmit($event)">
  <input
    type="text"
    (input)="handleInput($event)"
    (focus)="handleFocus()"
    (blur)="handleBlur()"
    (change)="handleChange($event)">

  <button type="submit">Submit</button>
</form>
```

#### Custom Component Events
```typescript
// child.component.ts
export class ChildComponent {
  @Output() valueChanged = new EventEmitter<string>();
  @Output() itemDeleted = new EventEmitter<number>();

  onValueChange(newValue: string): void {
    this.valueChanged.emit(newValue);
  }

  deleteItem(id: number): void {
    this.itemDeleted.emit(id);
  }
}
```

```html
<!-- parent.component.html -->
<app-child
  (valueChanged)="handleValueChange($event)"
  (itemDeleted)="handleDelete($event)">
</app-child>
```

```typescript
// parent.component.ts
export class ParentComponent {
  handleValueChange(value: string): void {
    console.log('Value changed to:', value);
  }

  handleDelete(id: number): void {
    console.log('Delete item:', id);
  }
}
```

---

## Two-Way Data Binding

Two-way data binding combines property binding and event binding to create a two-way flow of data.

### Syntax
```typescript
[(ngModel)]="property"
```

This is syntactic sugar for:
```html
[ngModel]="property" (ngModelChange)="property=$event"
```

### Setup
First, import FormsModule:

```typescript
// app.module.ts
import { FormsModule } from '@angular/forms';

@NgModule({
  imports: [
    BrowserModule,
    FormsModule  // Required for ngModel
  ]
})
export class AppModule { }
```

### Basic Example

```typescript
// form.component.ts
export class FormComponent {
  username: string = '';
  email: string = '';
  age: number = 0;
  bio: string = '';
  agreeToTerms: boolean = false;
  selectedCountry: string = 'us';
}
```

```html
<!-- form.component.html -->
<div class="form">
  <!-- Text input -->
  <input
    type="text"
    [(ngModel)]="username"
    placeholder="Username">
  <p>Username: {{ username }}</p>

  <!-- Email input -->
  <input
    type="email"
    [(ngModel)]="email"
    placeholder="Email">
  <p>Email: {{ email }}</p>

  <!-- Number input -->
  <input
    type="number"
    [(ngModel)]="age"
    placeholder="Age">
  <p>Age: {{ age }}</p>

  <!-- Textarea -->
  <textarea
    [(ngModel)]="bio"
    placeholder="Bio">
  </textarea>
  <p>Bio: {{ bio }}</p>

  <!-- Checkbox -->
  <label>
    <input
      type="checkbox"
      [(ngModel)]="agreeToTerms">
    I agree to terms
  </label>
  <p>Agreed: {{ agreeToTerms }}</p>

  <!-- Select dropdown -->
  <select [(ngModel)]="selectedCountry">
    <option value="us">United States</option>
    <option value="uk">United Kingdom</option>
    <option value="ca">Canada</option>
  </select>
  <p>Country: {{ selectedCountry }}</p>
</div>
```

### Custom Two-Way Binding

You can create custom two-way binding for your own components:

```typescript
// custom-input.component.ts
export class CustomInputComponent {
  private _value: string = '';

  @Input()
  get value(): string {
    return this._value;
  }

  set value(val: string) {
    this._value = val;
    this.valueChange.emit(this._value);
  }

  @Output() valueChange = new EventEmitter<string>();

  onInputChange(newValue: string): void {
    this.value = newValue;
  }
}
```

```html
<!-- custom-input.component.html -->
<input
  [value]="value"
  (input)="onInputChange($any($event.target).value)">
```

```html
<!-- parent.component.html -->
<app-custom-input [(value)]="parentValue"></app-custom-input>
<p>Parent value: {{ parentValue }}</p>
```

**Naming Convention**: For two-way binding to work with `[(propertyName)]`:
- Input property: `propertyName`
- Output event: `propertyNameChange`

---

## Template Reference Variables

Template reference variables allow you to reference DOM elements or directives in your template.

### Syntax
```html
#variableName
```

### Examples

#### Referencing DOM Elements
```html
<!-- Basic reference -->
<input #userInput type="text">
<button (click)="handleClick(userInput.value)">Submit</button>

<!-- Access element properties -->
<input #emailInput type="email" placeholder="Email">
<p>Input length: {{ emailInput.value.length }}</p>
<button (click)="emailInput.focus()">Focus Email</button>
```

```typescript
export class TemplateRefComponent {
  handleClick(value: string): void {
    console.log('Input value:', value);
  }
}
```

#### Referencing with ViewChild
```typescript
export class FormComponent implements AfterViewInit {
  @ViewChild('userInput') userInputRef!: ElementRef<HTMLInputElement>;

  ngAfterViewInit(): void {
    // Access the native element
    this.userInputRef.nativeElement.focus();
  }

  clearInput(): void {
    this.userInputRef.nativeElement.value = '';
  }
}
```

```html
<input #userInput type="text">
<button (click)="clearInput()">Clear</button>
```

#### Referencing Components
```html
<!-- Reference child component -->
<app-timer #timerRef></app-timer>
<button (click)="timerRef.start()">Start Timer</button>
<button (click)="timerRef.stop()">Stop Timer</button>
<button (click)="timerRef.reset()">Reset Timer</button>
```

```typescript
// timer.component.ts
export class TimerComponent {
  seconds: number = 0;
  private intervalId: any;

  start(): void {
    this.intervalId = setInterval(() => this.seconds++, 1000);
  }

  stop(): void {
    clearInterval(this.intervalId);
  }

  reset(): void {
    this.seconds = 0;
  }
}
```

#### Using with ngFor
```html
<ul>
  <li *ngFor="let item of items; let i = index; let first = first">
    <input #itemInput type="text" [value]="item">
    <button (click)="updateItem(i, itemInput.value)">Update</button>
  </li>
</ul>
```

---

## Structural Directives in Templates

Structural directives change the DOM layout by adding, removing, or manipulating elements.

### *ngIf

Controls whether an element is rendered.

```typescript
export class ConditionalComponent {
  isLoggedIn: boolean = false;
  user: User | null = null;
  role: string = 'user';
}
```

```html
<!-- Basic usage -->
<div *ngIf="isLoggedIn">
  <h2>Welcome back!</h2>
</div>

<!-- With else block -->
<div *ngIf="isLoggedIn; else loginPrompt">
  <h2>Dashboard</h2>
</div>
<ng-template #loginPrompt>
  <div>Please log in</div>
</ng-template>

<!-- With then and else -->
<div *ngIf="user; then userInfo else loading"></div>

<ng-template #userInfo>
  <div>User: {{ user.name }}</div>
</ng-template>

<ng-template #loading>
  <div>Loading user info...</div>
</ng-template>

<!-- Storing result in variable -->
<div *ngIf="user$ | async as user">
  <p>{{ user.name }}</p>
</div>
```

### *ngFor

Repeats an element for each item in a collection.

```typescript
export class ListComponent {
  users: User[] = [
    { id: 1, name: 'John', active: true },
    { id: 2, name: 'Jane', active: false },
    { id: 3, name: 'Bob', active: true }
  ];

  posts: Post[] = [];
}
```

```html
<!-- Basic usage -->
<ul>
  <li *ngFor="let user of users">
    {{ user.name }}
  </li>
</ul>

<!-- With index -->
<ul>
  <li *ngFor="let user of users; let i = index">
    {{ i + 1 }}. {{ user.name }}
  </li>
</ul>

<!-- With additional variables -->
<ul>
  <li *ngFor="let user of users;
              let i = index;
              let first = first;
              let last = last;
              let even = even;
              let odd = odd">
    <span [class.first]="first" [class.last]="last">
      {{ i }}. {{ user.name }}
    </span>
  </li>
</ul>

<!-- With trackBy for performance -->
<ul>
  <li *ngFor="let user of users; trackBy: trackByUserId">
    {{ user.name }}
  </li>
</ul>
```

```typescript
trackByUserId(index: number, user: User): number {
  return user.id;
}
```

**Why trackBy?**: When the array changes, Angular can track which items were added/removed/moved by their identity rather than re-rendering everything.

### *ngSwitch

Displays one element from a set of alternatives based on a condition.

```typescript
export class SwitchComponent {
  userRole: string = 'admin';
  viewMode: string = 'grid';
}
```

```html
<!-- Basic usage -->
<div [ngSwitch]="userRole">
  <div *ngSwitchCase="'admin'">
    <h2>Admin Panel</h2>
  </div>
  <div *ngSwitchCase="'moderator'">
    <h2>Moderator Tools</h2>
  </div>
  <div *ngSwitchCase="'user'">
    <h2>User Dashboard</h2>
  </div>
  <div *ngSwitchDefault>
    <h2>Guest View</h2>
  </div>
</div>

<!-- Another example -->
<div [ngSwitch]="viewMode">
  <app-grid-view *ngSwitchCase="'grid'"></app-grid-view>
  <app-list-view *ngSwitchCase="'list'"></app-list-view>
  <app-table-view *ngSwitchCase="'table'"></app-table-view>
  <app-default-view *ngSwitchDefault></app-default-view>
</div>
```

### ng-container

A logical container that doesn't create a DOM element. Useful with structural directives.

```html
<!-- Use when you don't want an extra wrapping element -->
<ng-container *ngIf="isLoggedIn">
  <h2>Title</h2>
  <p>Content</p>
  <button>Action</button>
</ng-container>

<!-- Multiple structural directives -->
<ng-container *ngIf="items">
  <div *ngFor="let item of items">
    {{ item.name }}
  </div>
</ng-container>
```

---

## Attribute Directives

Attribute directives change the appearance or behavior of elements.

### ngClass

Dynamically add or remove CSS classes.

```typescript
export class StyleComponent {
  isActive: boolean = true;
  isDisabled: boolean = false;
  status: string = 'success';

  getClasses() {
    return {
      'active': this.isActive,
      'disabled': this.isDisabled,
      'status-success': this.status === 'success'
    };
  }
}
```

```html
<!-- String syntax -->
<div [ngClass]="'btn btn-primary'">Button</div>

<!-- Array syntax -->
<div [ngClass]="['btn', 'btn-primary', 'active']">Button</div>

<!-- Object syntax -->
<div [ngClass]="{ 'active': isActive, 'disabled': isDisabled }">
  Status
</div>

<!-- Method syntax -->
<div [ngClass]="getClasses()">Status</div>

<!-- Ternary operator -->
<div [ngClass]="isActive ? 'active' : 'inactive'">Status</div>
```

### ngStyle

Dynamically set inline styles.

```typescript
export class StyleComponent {
  textColor: string = 'blue';
  fontSize: number = 16;
  isBold: boolean = true;

  getStyles() {
    return {
      'color': this.textColor,
      'font-size': this.fontSize + 'px',
      'font-weight': this.isBold ? 'bold' : 'normal'
    };
  }
}
```

```html
<!-- Object syntax -->
<div [ngStyle]="{
  'color': textColor,
  'font-size.px': fontSize,
  'background-color': 'lightgray'
}">
  Styled text
</div>

<!-- Method syntax -->
<div [ngStyle]="getStyles()">Styled text</div>

<!-- Conditional styles -->
<div [ngStyle]="{
  'color': isError ? 'red' : 'green',
  'font-weight': isImportant ? 'bold' : 'normal'
}">
  Status message
</div>
```

---

## Pipes in Templates

Pipes transform data for display without changing the underlying data.

### Built-in Pipes

#### DatePipe
```typescript
export class DateComponent {
  today: Date = new Date();
  customDate: Date = new Date(2025, 0, 15);
}
```

```html
<!-- Default format -->
<p>{{ today | date }}</p>
<!-- Output: Jan 5, 2025 -->

<!-- Custom formats -->
<p>{{ today | date:'short' }}</p>
<!-- Output: 1/5/25, 3:30 PM -->

<p>{{ today | date:'medium' }}</p>
<!-- Output: Jan 5, 2025, 3:30:45 PM -->

<p>{{ today | date:'long' }}</p>
<!-- Output: January 5, 2025 at 3:30:45 PM GMT+1 -->

<p>{{ today | date:'fullDate' }}</p>
<!-- Output: Tuesday, January 5, 2025 -->

<!-- Custom pattern -->
<p>{{ today | date:'dd/MM/yyyy' }}</p>
<!-- Output: 05/01/2025 -->

<p>{{ today | date:'MMMM d, y, h:mm a' }}</p>
<!-- Output: January 5, 2025, 3:30 PM -->
```

#### CurrencyPipe
```typescript
export class PriceComponent {
  price: number = 1234.56;
  salary: number = 75000;
}
```

```html
<!-- Default (USD) -->
<p>{{ price | currency }}</p>
<!-- Output: $1,234.56 -->

<!-- Different currency -->
<p>{{ price | currency:'EUR' }}</p>
<!-- Output: €1,234.56 -->

<p>{{ price | currency:'GBP' }}</p>
<!-- Output: £1,234.56 -->

<!-- Custom display -->
<p>{{ price | currency:'USD':'symbol':'1.0-0' }}</p>
<!-- Output: $1,235 -->

<p>{{ salary | currency:'USD':'symbol':'1.2-2' }}</p>
<!-- Output: $75,000.00 -->
```

#### DecimalPipe
```typescript
export class NumberComponent {
  number: number = 3.14159;
  largeNumber: number = 1234567.89;
}
```

```html
<!-- Format: {minIntegerDigits}.{minFractionDigits}-{maxFractionDigits} -->
<p>{{ number | number }}</p>
<!-- Output: 3.142 -->

<p>{{ number | number:'1.0-0' }}</p>
<!-- Output: 3 -->

<p>{{ number | number:'1.1-3' }}</p>
<!-- Output: 3.142 -->

<p>{{ largeNumber | number:'1.0-0' }}</p>
<!-- Output: 1,234,568 -->
```

#### PercentPipe
```typescript
export class StatsComponent {
  completionRate: number = 0.756;
  growthRate: number = 0.125;
}
```

```html
<p>{{ completionRate | percent }}</p>
<!-- Output: 75.6% -->

<p>{{ completionRate | percent:'1.0-0' }}</p>
<!-- Output: 76% -->

<p>{{ growthRate | percent:'1.1-2' }}</p>
<!-- Output: 12.50% -->
```

#### UpperCasePipe, LowerCasePipe, TitleCasePipe
```typescript
export class TextComponent {
  text: string = 'hello world';
  mixedCase: string = 'HeLLo WoRLd';
}
```

```html
<p>{{ text | uppercase }}</p>
<!-- Output: HELLO WORLD -->

<p>{{ mixedCase | lowercase }}</p>
<!-- Output: hello world -->

<p>{{ text | titlecase }}</p>
<!-- Output: Hello World -->
```

#### JsonPipe
```typescript
export class DebugComponent {
  user = {
    id: 1,
    name: 'John Smith',
    email: 'john@example.com',
    roles: ['user', 'admin']
  };
}
```

```html
<!-- Useful for debugging -->
<pre>{{ user | json }}</pre>
<!--
Output:
{
  "id": 1,
  "name": "John Smith",
  "email": "john@example.com",
  "roles": [
    "user",
    "admin"
  ]
}
-->
```

#### SlicePipe
```typescript
export class ListComponent {
  items: string[] = ['A', 'B', 'C', 'D', 'E'];
  text: string = 'Hello World';
}
```

```html
<!-- Array slice -->
<p>{{ items | slice:1:3 }}</p>
<!-- Output: ['B', 'C'] -->

<p>{{ items | slice:2 }}</p>
<!-- Output: ['C', 'D', 'E'] -->

<!-- String slice -->
<p>{{ text | slice:0:5 }}</p>
<!-- Output: Hello -->
```

#### AsyncPipe
```typescript
export class AsyncComponent {
  time$: Observable<Date>;
  user$: Observable<User>;

  constructor(private userService: UserService) {
    this.time$ = interval(1000).pipe(
      map(() => new Date())
    );

    this.user$ = this.userService.getUser(1);
  }
}
```

```html
<!-- Automatically subscribes and unsubscribes -->
<p>Current time: {{ time$ | async | date:'medium' }}</p>

<!-- With null check -->
<div *ngIf="user$ | async as user">
  <h2>{{ user.name }}</h2>
  <p>{{ user.email }}</p>
</div>
```

### Chaining Pipes

```html
<!-- Multiple pipes -->
<p>{{ today | date:'longDate' | uppercase }}</p>
<!-- Output: JANUARY 5, 2025 -->

<p>{{ price | currency:'USD' | slice:0:5 }}</p>

<p>{{ user$ | async | json }}</p>
```

### Custom Pipes

```typescript
// truncate.pipe.ts
import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  name: 'truncate'
})
export class TruncatePipe implements PipeTransform {
  transform(value: string, limit: number = 50, trail: string = '...'): string {
    if (!value) return '';
    return value.length > limit
      ? value.substring(0, limit) + trail
      : value;
  }
}
```

```html
<p>{{ longText | truncate:100 }}</p>
<p>{{ longText | truncate:50:'---' }}</p>
```

---

## Safe Navigation Operator

The safe navigation operator (`?.`) protects against null/undefined errors.

```typescript
export class UserComponent {
  user: User | null = null;

  loadUser(): void {
    // Simulating async data loading
    setTimeout(() => {
      this.user = {
        id: 1,
        name: 'John Smith',
        profile: {
          bio: 'Developer',
          address: {
            city: 'New York'
          }
        }
      };
    }, 1000);
  }
}
```

```html
<!-- Without safe navigation - will cause errors -->
<p>{{ user.name }}</p>  <!-- ERROR if user is null -->

<!-- With safe navigation - safe -->
<p>{{ user?.name }}</p>  <!-- Shows nothing if user is null -->

<!-- Deep navigation -->
<p>{{ user?.profile?.address?.city }}</p>

<!-- With method calls -->
<p>{{ user?.getName() }}</p>

<!-- In property binding -->
<img [src]="user?.profileImage" alt="Profile">

<!-- With arrays -->
<p>{{ users?.[0]?.name }}</p>

<!-- Combined with other operators -->
<p>{{ (user$ | async)?.name }}</p>

<!-- With event binding -->
<button (click)="user?.update()">Update</button>
```

### Non-null Assertion Operator

If you're certain a value won't be null, use `!`:

```typescript
export class ComponentWithData {
  @ViewChild('input') inputRef!: ElementRef;  // ! asserts non-null

  user!: User;  // Will be assigned before use

  ngAfterViewInit(): void {
    this.inputRef.nativeElement.focus();  // Safe because of !
  }
}
```

```html
<!-- In template -->
<p>{{ user!.name }}</p>
```

**Use cautiously**: Only when you're absolutely sure the value exists.

---

## Template Expressions and Statements

### Template Expressions

Expressions are code snippets that Angular evaluates to produce a value.

#### Where Expressions Are Used
```html
<!-- Interpolation -->
{{ expression }}

<!-- Property binding -->
<div [property]="expression"></div>

<!-- Event binding (right side) -->
<button (click)="expression"></button>
```

#### Expression Guidelines

**Allowed:**
```html
<!-- Property access -->
{{ user.name }}

<!-- Method calls -->
{{ calculateTotal() }}

<!-- Math operations -->
{{ price * quantity }}

<!-- Logical operators -->
{{ isActive && isVisible }}

<!-- Ternary operator -->
{{ isLoggedIn ? 'Logout' : 'Login' }}

<!-- Template reference variables -->
{{ inputRef.value }}

<!-- Safe navigation -->
{{ user?.address?.city }}

<!-- Pipe operator -->
{{ today | date }}
```

**Not Allowed:**
```html
<!-- Assignments -->
{{ value = 10 }}  <!-- ERROR -->

<!-- new, typeof, instanceof -->
{{ new Date() }}  <!-- ERROR -->

<!-- Chaining with ; or , -->
{{ a = 1; b = 2 }}  <!-- ERROR -->

<!-- Increment/decrement -->
{{ count++ }}  <!-- ERROR -->

<!-- Bitwise operators -->
{{ a | b }}  <!-- ERROR (pipe instead) -->

<!-- Global namespace -->
{{ window.location }}  <!-- ERROR -->
{{ console.log() }}  <!-- ERROR -->
```

#### Expression Context

Expressions evaluate in the component context:

```typescript
export class ExpressionComponent {
  title: string = 'My App';
  count: number = 5;

  getDisplayText(): string {
    return `Count is ${this.count}`;
  }
}
```

```html
<!-- Refers to component properties -->
<p>{{ title }}</p>
<p>{{ count }}</p>
<p>{{ getDisplayText() }}</p>

<!-- Template variables shadow component properties -->
<input #title>
<p>{{ title.value }}</p>  <!-- Refers to template variable, not component -->
```

### Template Statements

Statements respond to events and can have side effects.

```html
<!-- Event bindings -->
<button (click)="save()">Save</button>
<button (click)="count = count + 1">Increment</button>
<button (click)="isActive = !isActive">Toggle</button>

<!-- With $event -->
<input (input)="handleInput($event)">

<!-- Multiple statements (avoid if possible) -->
<button (click)="save(); close()">Save and Close</button>

<!-- Conditional statements -->
<button (click)="isActive ? deactivate() : activate()">Toggle</button>
```

#### Statement Guidelines

**Allowed:**
```html
<!-- Assignments -->
(click)="value = 10"

<!-- Method calls -->
(click)="save()"

<!-- Property access -->
(click)="user.update()"

<!-- Basic operators -->
(click)="count = count + 1"
```

**Not Allowed:**
```html
<!-- new, typeof, instanceof -->
(click)="user = new User()"  <!-- ERROR -->

<!-- Chaining with ; (discouraged) -->
(click)="a = 1; b = 2"  <!-- Works but not recommended -->

<!-- Increment/decrement -->
(click)="count++"  <!-- ERROR -->

<!-- Bitwise operators -->
(click)="flags = a | b"  <!-- ERROR -->
```

### Best Practices

1. **Keep expressions simple**: Complex logic belongs in the component class
   ```html
   <!-- Bad -->
   <p>{{ (userAge > 18 && userAge < 65 && isActive && !isBlocked) ? 'Eligible' : 'Not Eligible' }}</p>

   <!-- Good -->
   <p>{{ isEligible() }}</p>
   ```

2. **Avoid side effects in expressions**: Don't change state in interpolation
   ```html
   <!-- Bad -->
   <p>{{ saveData() }}</p>  <!-- Don't call methods with side effects -->

   <!-- Good -->
   <button (click)="saveData()">Save</button>
   ```

3. **Use pure pipes for performance**: Pipes run on every change detection
   ```typescript
   @Pipe({
     name: 'filter',
     pure: true  // Only runs when input reference changes
   })
   ```

4. **Prefer template variables over repeated expressions**
   ```html
   <!-- Bad -->
   <div *ngIf="user$ | async">
     <p>{{ (user$ | async)?.name }}</p>
     <p>{{ (user$ | async)?.email }}</p>
   </div>

   <!-- Good -->
   <div *ngIf="user$ | async as user">
     <p>{{ user.name }}</p>
     <p>{{ user.email }}</p>
   </div>
   ```

---

## TheDispatch Example: Post List Template

Here's how the concepts apply to the blog application:

```typescript
// post-list.component.ts
export class PostListComponent implements OnInit {
  posts: Post[] = [];
  loading: boolean = true;
  error: string | null = null;
  currentUser: User | null = null;
  selectedFilter: string = 'all';
  searchQuery: string = '';

  constructor(private postService: PostService) {}

  ngOnInit(): void {
    this.loadPosts();
  }

  loadPosts(): void {
    this.loading = true;
    this.postService.getAllPosts().subscribe({
      next: (posts) => {
        this.posts = posts;
        this.loading = false;
      },
      error: (err) => {
        this.error = 'Failed to load posts';
        this.loading = false;
      }
    });
  }

  deletePost(postId: number): void {
    if (confirm('Delete this post?')) {
      this.postService.deletePost(postId).subscribe({
        next: () => this.loadPosts(),
        error: (err) => console.error('Delete failed', err)
      });
    }
  }

  trackByPostId(index: number, post: Post): number {
    return post.id;
  }

  get filteredPosts(): Post[] {
    return this.posts.filter(post =>
      post.title.toLowerCase().includes(this.searchQuery.toLowerCase())
    );
  }
}
```

```html
<!-- post-list.component.html -->
<div class="post-list-container">
  <!-- Search bar with two-way binding -->
  <div class="search-bar">
    <input
      type="text"
      [(ngModel)]="searchQuery"
      placeholder="Search posts..."
      #searchInput>
    <button (click)="searchInput.value = ''; searchQuery = ''">Clear</button>
  </div>

  <!-- Filter dropdown -->
  <select [(ngModel)]="selectedFilter">
    <option value="all">All Posts</option>
    <option value="mine">My Posts</option>
    <option value="published">Published</option>
  </select>

  <!-- Loading state -->
  <div *ngIf="loading" class="loading">
    <p>Loading posts...</p>
  </div>

  <!-- Error state -->
  <div *ngIf="error && !loading" class="error">
    <p>{{ error }}</p>
    <button (click)="loadPosts()">Retry</button>
  </div>

  <!-- Posts list -->
  <div *ngIf="!loading && !error">
    <!-- Empty state -->
    <div *ngIf="filteredPosts.length === 0" class="empty-state">
      <p>No posts found</p>
    </div>

    <!-- Post items -->
    <div class="posts">
      <article
        *ngFor="let post of filteredPosts; trackBy: trackByPostId; let i = index"
        class="post-card"
        [class.featured]="post.featured"
        [class.draft]="post.status === 'draft'">

        <!-- Post header -->
        <header>
          <h2>{{ i + 1 }}. {{ post.title }}</h2>
          <span class="date">{{ post.createdAt | date:'medium' }}</span>
        </header>

        <!-- Post image -->
        <img
          *ngIf="post.imageUrl"
          [src]="post.imageUrl"
          [alt]="post.title"
          class="post-image">

        <!-- Post content -->
        <div class="content">
          <p>{{ post.content | truncate:150 }}</p>
        </div>

        <!-- Post metadata -->
        <div class="metadata">
          <span class="author">
            By {{ post.author?.username || 'Anonymous' }}
          </span>
          <span class="views">
            {{ post.views | number }} views
          </span>
          <span class="likes">
            {{ post.likes }} likes
          </span>
        </div>

        <!-- Post actions -->
        <div class="actions">
          <button
            [routerLink]="['/posts', post.id]"
            class="btn btn-primary">
            Read More
          </button>

          <!-- Only show edit/delete for own posts -->
          <ng-container *ngIf="post.author?.id === currentUser?.id">
            <button
              [routerLink]="['/posts', post.id, 'edit']"
              class="btn btn-secondary">
              Edit
            </button>
            <button
              (click)="deletePost(post.id)"
              class="btn btn-danger"
              [disabled]="loading">
              Delete
            </button>
          </ng-container>
        </div>

        <!-- Status badge -->
        <span
          class="badge"
          [ngClass]="{
            'badge-published': post.status === 'published',
            'badge-draft': post.status === 'draft',
            'badge-archived': post.status === 'archived'
          }">
          {{ post.status | titlecase }}
        </span>
      </article>
    </div>
  </div>
</div>
```

This example demonstrates:
- **Interpolation**: `{{ post.title }}`, `{{ post.likes }}`
- **Property binding**: `[src]`, `[alt]`, `[class.featured]`
- **Event binding**: `(click)`, `(input)`
- **Two-way binding**: `[(ngModel)]`
- **Structural directives**: `*ngIf`, `*ngFor`
- **Attribute directives**: `[ngClass]`
- **Pipes**: `date`, `number`, `titlecase`, custom `truncate`
- **Template variables**: `#searchInput`
- **Safe navigation**: `post.author?.username`

---

## Summary

Angular templates provide powerful ways to bind data and handle user interactions:

1. **Interpolation** (`{{ }}`) - Display component data
2. **Property Binding** (`[property]`) - Set element/component properties
3. **Event Binding** (`(event)`) - Listen to events
4. **Two-Way Binding** (`[(ngModel)]`) - Sync form inputs with component data
5. **Template Variables** (`#ref`) - Reference elements in templates
6. **Structural Directives** (`*ngIf`, `*ngFor`, `*ngSwitch`) - Manipulate DOM structure
7. **Attribute Directives** (`ngClass`, `ngStyle`) - Change appearance/behavior
8. **Pipes** - Transform data for display
9. **Safe Navigation** (`?.`) - Handle null/undefined safely

**Best Practices:**
- Keep template logic simple
- Move complex logic to the component class
- Use trackBy with *ngFor for performance
- Prefer safe navigation over null checks
- Use pipes for data transformation
- Avoid side effects in expressions

Understanding these concepts is essential for building dynamic, interactive Angular applications like TheDispatch blog system.
