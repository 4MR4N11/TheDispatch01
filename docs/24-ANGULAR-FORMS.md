# Angular Forms

## Table of Contents
1. [Forms Overview](#forms-overview)
2. [Template-Driven Forms](#template-driven-forms)
3. [Reactive Forms](#reactive-forms)
4. [Form Validation](#form-validation)
5. [Custom Validators](#custom-validators)
6. [Form Arrays](#form-arrays)
7. [Dynamic Forms](#dynamic-forms)
8. [Form Best Practices](#form-best-practices)
9. [TheDispatch Forms](#thedispatch-forms)

---

## Forms Overview

Angular provides two approaches to handling forms:

1. **Template-Driven Forms**: Simpler, uses directives in templates
2. **Reactive Forms**: More powerful, uses explicit model in component

### Comparison

| Feature | Template-Driven | Reactive |
|---------|----------------|----------|
| Setup | Easy | More code |
| Form Model | Implicit | Explicit |
| Data Flow | Asynchronous | Synchronous |
| Validation | Directives | Functions |
| Testing | Difficult | Easy |
| Scalability | Limited | Excellent |
| Type Safety | No | Yes |

---

## Template-Driven Forms

Template-driven forms use directives like `ngModel`, `ngForm`, and validation attributes in templates.

### Setup

```typescript
// app.module.ts
import { FormsModule } from '@angular/forms';

@NgModule({
  imports: [
    BrowserModule,
    FormsModule  // Required for template-driven forms
  ]
})
export class AppModule {}
```

### Basic Form

```typescript
// login.component.ts
export class LoginComponent {
  user = {
    email: '',
    password: ''
  };

  onSubmit(form: NgForm): void {
    if (form.valid) {
      console.log('Form submitted:', this.user);
      this.login(this.user);
    }
  }

  private login(credentials: any): void {
    // Login logic
  }
}
```

```html
<!-- login.component.html -->
<form #loginForm="ngForm" (ngSubmit)="onSubmit(loginForm)">
  <div>
    <label for="email">Email</label>
    <input
      type="email"
      id="email"
      name="email"
      [(ngModel)]="user.email"
      required
      email
      #emailField="ngModel">

    <div *ngIf="emailField.invalid && (emailField.dirty || emailField.touched)">
      <p *ngIf="emailField.errors?.['required']">Email is required</p>
      <p *ngIf="emailField.errors?.['email']">Invalid email format</p>
    </div>
  </div>

  <div>
    <label for="password">Password</label>
    <input
      type="password"
      id="password"
      name="password"
      [(ngModel)]="user.password"
      required
      minlength="6"
      #passwordField="ngModel">

    <div *ngIf="passwordField.invalid && (passwordField.dirty || passwordField.touched)">
      <p *ngIf="passwordField.errors?.['required']">Password is required</p>
      <p *ngIf="passwordField.errors?.['minlength']">
        Password must be at least 6 characters
      </p>
    </div>
  </div>

  <button type="submit" [disabled]="loginForm.invalid">
    Login
  </button>
</form>
```

### Form States

```html
<!-- Form-level states -->
<p>Valid: {{ loginForm.valid }}</p>
<p>Invalid: {{ loginForm.invalid }}</p>
<p>Touched: {{ loginForm.touched }}</p>
<p>Untouched: {{ loginForm.untouched }}</p>
<p>Dirty: {{ loginForm.dirty }}</p>
<p>Pristine: {{ loginForm.pristine }}</p>

<!-- Field-level states -->
<p>Email valid: {{ emailField.valid }}</p>
<p>Email touched: {{ emailField.touched }}</p>
<p>Email dirty: {{ emailField.dirty }}</p>
```

### Built-in Validators

```html
<!-- Required -->
<input ngModel required>

<!-- Email -->
<input ngModel email>

<!-- Min/Max length -->
<input ngModel minlength="3" maxlength="20">

<!-- Min/Max value -->
<input ngModel min="18" max="100">

<!-- Pattern -->
<input ngModel pattern="[A-Za-z]{3,}">
```

### Complete Template-Driven Form Example

```typescript
// register.component.ts
export class RegisterComponent {
  user = {
    username: '',
    email: '',
    password: '',
    confirmPassword: '',
    agreeToTerms: false
  };

  onSubmit(form: NgForm): void {
    if (form.valid) {
      console.log('Registration:', this.user);
      this.register(this.user);
    }
  }

  private register(userData: any): void {
    // Registration logic
  }
}
```

```html
<!-- register.component.html -->
<form #registerForm="ngForm" (ngSubmit)="onSubmit(registerForm)">
  <div>
    <label for="username">Username</label>
    <input
      type="text"
      id="username"
      name="username"
      [(ngModel)]="user.username"
      required
      minlength="3"
      #usernameField="ngModel">

    <div *ngIf="usernameField.invalid && usernameField.touched">
      <p *ngIf="usernameField.errors?.['required']">Username is required</p>
      <p *ngIf="usernameField.errors?.['minlength']">
        Username must be at least 3 characters
      </p>
    </div>
  </div>

  <div>
    <label for="email">Email</label>
    <input
      type="email"
      id="email"
      name="email"
      [(ngModel)]="user.email"
      required
      email
      #emailField="ngModel">

    <div *ngIf="emailField.invalid && emailField.touched">
      <p *ngIf="emailField.errors?.['required']">Email is required</p>
      <p *ngIf="emailField.errors?.['email']">Invalid email format</p>
    </div>
  </div>

  <div>
    <label for="password">Password</label>
    <input
      type="password"
      id="password"
      name="password"
      [(ngModel)]="user.password"
      required
      minlength="8"
      #passwordField="ngModel">

    <div *ngIf="passwordField.invalid && passwordField.touched">
      <p *ngIf="passwordField.errors?.['required']">Password is required</p>
      <p *ngIf="passwordField.errors?.['minlength']">
        Password must be at least 8 characters
      </p>
    </div>
  </div>

  <div>
    <label for="confirmPassword">Confirm Password</label>
    <input
      type="password"
      id="confirmPassword"
      name="confirmPassword"
      [(ngModel)]="user.confirmPassword"
      required
      #confirmPasswordField="ngModel">

    <div *ngIf="confirmPasswordField.touched && user.password !== user.confirmPassword">
      <p>Passwords do not match</p>
    </div>
  </div>

  <div>
    <label>
      <input
        type="checkbox"
        name="agreeToTerms"
        [(ngModel)]="user.agreeToTerms"
        required>
      I agree to the terms and conditions
    </label>
  </div>

  <button
    type="submit"
    [disabled]="registerForm.invalid || user.password !== user.confirmPassword">
    Register
  </button>
</form>
```

---

## Reactive Forms

Reactive forms provide explicit, immutable model in the component with better type safety and testability.

### Setup

```typescript
// app.module.ts
import { ReactiveFormsModule } from '@angular/forms';

@NgModule({
  imports: [
    BrowserModule,
    ReactiveFormsModule  // Required for reactive forms
  ]
})
export class AppModule {}
```

### Basic Form

```typescript
// login.component.ts
import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';

export class LoginComponent implements OnInit {
  loginForm!: FormGroup;

  constructor(private fb: FormBuilder) {}

  ngOnInit(): void {
    this.loginForm = this.fb.group({
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(6)]]
    });
  }

  onSubmit(): void {
    if (this.loginForm.valid) {
      console.log('Form value:', this.loginForm.value);
      this.login(this.loginForm.value);
    }
  }

  // Getters for easy access in template
  get email() {
    return this.loginForm.get('email');
  }

  get password() {
    return this.loginForm.get('password');
  }

  private login(credentials: any): void {
    // Login logic
  }
}
```

```html
<!-- login.component.html -->
<form [formGroup]="loginForm" (ngSubmit)="onSubmit()">
  <div>
    <label for="email">Email</label>
    <input
      type="email"
      id="email"
      formControlName="email">

    <div *ngIf="email?.invalid && (email?.dirty || email?.touched)">
      <p *ngIf="email?.errors?.['required']">Email is required</p>
      <p *ngIf="email?.errors?.['email']">Invalid email format</p>
    </div>
  </div>

  <div>
    <label for="password">Password</label>
    <input
      type="password"
      id="password"
      formControlName="password">

    <div *ngIf="password?.invalid && (password?.dirty || password?.touched)">
      <p *ngIf="password?.errors?.['required']">Password is required</p>
      <p *ngIf="password?.errors?.['minlength']">
        Password must be at least 6 characters
      </p>
    </div>
  </div>

  <button type="submit" [disabled]="loginForm.invalid">
    Login
  </button>
</form>
```

### FormControl Methods

```typescript
// Create FormControl
const nameControl = new FormControl('initial value', Validators.required);

// Get/Set value
console.log(nameControl.value);
nameControl.setValue('new value');
nameControl.patchValue('partial update');

// Reset
nameControl.reset();
nameControl.reset('default value');

// Enable/Disable
nameControl.disable();
nameControl.enable();

// Mark as touched/dirty
nameControl.markAsTouched();
nameControl.markAsDirty();
nameControl.markAsPristine();
nameControl.markAsUntouched();

// Update validity
nameControl.updateValueAndValidity();

// Status
console.log(nameControl.valid);
console.log(nameControl.invalid);
console.log(nameControl.touched);
console.log(nameControl.dirty);
console.log(nameControl.errors);
```

### FormGroup Methods

```typescript
// Create FormGroup
const userForm = new FormGroup({
  name: new FormControl(''),
  email: new FormControl('')
});

// Get value
console.log(userForm.value);  // { name: '', email: '' }

// Set value (all controls)
userForm.setValue({
  name: 'John',
  email: 'john@example.com'
});

// Patch value (partial update)
userForm.patchValue({
  name: 'Jane'  // email remains unchanged
});

// Reset form
userForm.reset();
userForm.reset({
  name: 'Default Name',
  email: ''
});

// Get specific control
const nameControl = userForm.get('name');

// Add control
userForm.addControl('phone', new FormControl(''));

// Remove control
userForm.removeControl('phone');

// Status
console.log(userForm.valid);
console.log(userForm.invalid);
console.log(userForm.errors);
```

### Nested FormGroups

```typescript
export class ProfileComponent implements OnInit {
  profileForm!: FormGroup;

  constructor(private fb: FormBuilder) {}

  ngOnInit(): void {
    this.profileForm = this.fb.group({
      name: ['', Validators.required],
      email: ['', [Validators.required, Validators.email]],
      address: this.fb.group({
        street: [''],
        city: [''],
        state: [''],
        zip: ['', Validators.pattern(/^\d{5}$/)]
      }),
      preferences: this.fb.group({
        newsletter: [false],
        notifications: [true]
      })
    });
  }

  onSubmit(): void {
    console.log(this.profileForm.value);
    /*
    {
      name: 'John',
      email: 'john@example.com',
      address: {
        street: '123 Main St',
        city: 'New York',
        state: 'NY',
        zip: '10001'
      },
      preferences: {
        newsletter: true,
        notifications: true
      }
    }
    */
  }

  // Access nested controls
  get street() {
    return this.profileForm.get('address.street');
  }

  get zip() {
    return this.profileForm.get('address.zip');
  }
}
```

```html
<form [formGroup]="profileForm" (ngSubmit)="onSubmit()">
  <input formControlName="name" placeholder="Name">
  <input formControlName="email" placeholder="Email">

  <div formGroupName="address">
    <input formControlName="street" placeholder="Street">
    <input formControlName="city" placeholder="City">
    <input formControlName="state" placeholder="State">
    <input formControlName="zip" placeholder="ZIP">
  </div>

  <div formGroupName="preferences">
    <label>
      <input type="checkbox" formControlName="newsletter">
      Subscribe to newsletter
    </label>
    <label>
      <input type="checkbox" formControlName="notifications">
      Enable notifications
    </label>
  </div>

  <button type="submit" [disabled]="profileForm.invalid">Save</button>
</form>
```

---

## Form Validation

### Built-in Validators

```typescript
import { Validators } from '@angular/forms';

this.form = this.fb.group({
  // Required
  username: ['', Validators.required],

  // Email
  email: ['', [Validators.required, Validators.email]],

  // Min/Max length
  password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(20)]],

  // Min/Max value
  age: ['', [Validators.required, Validators.min(18), Validators.max(100)]],

  // Pattern
  phone: ['', Validators.pattern(/^\d{10}$/)],

  // Required true (for checkboxes)
  agreeToTerms: [false, Validators.requiredTrue],

  // Multiple validators
  username: ['', [
    Validators.required,
    Validators.minLength(3),
    Validators.maxLength(20),
    Validators.pattern(/^[a-zA-Z0-9_]+$/)
  ]]
});
```

### Displaying Validation Errors

```html
<!-- Method 1: Direct access -->
<div *ngIf="loginForm.get('email')?.invalid && loginForm.get('email')?.touched">
  <p *ngIf="loginForm.get('email')?.errors?.['required']">Email is required</p>
  <p *ngIf="loginForm.get('email')?.errors?.['email']">Invalid email</p>
</div>

<!-- Method 2: Using getter -->
<div *ngIf="email?.invalid && email?.touched">
  <p *ngIf="email?.errors?.['required']">Email is required</p>
  <p *ngIf="email?.errors?.['email']">Invalid email</p>
</div>

<!-- Method 3: hasError method -->
<div *ngIf="email?.invalid && email?.touched">
  <p *ngIf="email?.hasError('required')">Email is required</p>
  <p *ngIf="email?.hasError('email')">Invalid email</p>
</div>
```

### CSS Classes for Validation

Angular adds these CSS classes automatically:

```css
/* Valid/Invalid */
.ng-valid { border-color: green; }
.ng-invalid { border-color: red; }

/* Touched/Untouched */
.ng-touched { /* Styles */ }
.ng-untouched { /* Styles */ }

/* Dirty/Pristine */
.ng-dirty { /* Styles */ }
.ng-pristine { /* Styles */ }

/* Combined */
.ng-invalid.ng-touched {
  border-color: red;
  background-color: #fee;
}

.ng-valid.ng-touched {
  border-color: green;
  background-color: #efe;
}
```

---

## Custom Validators

### Synchronous Validators

```typescript
// validators.ts
import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

// Username validator
export function usernameValidator(): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    if (!control.value) {
      return null;  // Don't validate empty values
    }

    const valid = /^[a-zA-Z0-9_]{3,20}$/.test(control.value);
    return valid ? null : { invalidUsername: { value: control.value } };
  };
}

// Password strength validator
export function passwordStrengthValidator(): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    if (!control.value) {
      return null;
    }

    const hasNumber = /[0-9]/.test(control.value);
    const hasUpper = /[A-Z]/.test(control.value);
    const hasLower = /[a-z]/.test(control.value);
    const hasSpecial = /[!@#$%^&*(),.?":{}|<>]/.test(control.value);

    const valid = hasNumber && hasUpper && hasLower && hasSpecial;

    return valid ? null : {
      passwordStrength: {
        hasNumber,
        hasUpper,
        hasLower,
        hasSpecial
      }
    };
  };
}

// Confirm password validator
export function confirmPasswordValidator(passwordField: string): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    if (!control.parent) {
      return null;
    }

    const password = control.parent.get(passwordField);
    const confirmPassword = control;

    if (!password || !confirmPassword) {
      return null;
    }

    if (confirmPassword.value === '') {
      return null;
    }

    return password.value === confirmPassword.value
      ? null
      : { passwordMismatch: true };
  };
}

// Age validator
export function ageValidator(min: number, max: number): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    if (!control.value) {
      return null;
    }

    const age = parseInt(control.value, 10);

    if (isNaN(age)) {
      return { invalidAge: true };
    }

    if (age < min || age > max) {
      return { ageOutOfRange: { min, max, actual: age } };
    }

    return null;
  };
}
```

### Using Custom Validators

```typescript
export class RegisterComponent implements OnInit {
  registerForm!: FormGroup;

  constructor(private fb: FormBuilder) {}

  ngOnInit(): void {
    this.registerForm = this.fb.group({
      username: ['', [
        Validators.required,
        usernameValidator()
      ]],
      email: ['', [Validators.required, Validators.email]],
      password: ['', [
        Validators.required,
        Validators.minLength(8),
        passwordStrengthValidator()
      ]],
      confirmPassword: ['', [
        Validators.required,
        confirmPasswordValidator('password')
      ]],
      age: ['', [
        Validators.required,
        ageValidator(18, 100)
      ]]
    });
  }
}
```

### Async Validators

Validators that perform asynchronous operations (e.g., checking username availability).

```typescript
// async-validators.ts
import { AbstractControl, AsyncValidatorFn, ValidationErrors } from '@angular/forms';
import { Observable, of } from 'rxjs';
import { map, delay, switchMap } from 'rxjs/operators';

export class AsyncValidators {
  static usernameExists(userService: UserService): AsyncValidatorFn {
    return (control: AbstractControl): Observable<ValidationErrors | null> => {
      if (!control.value) {
        return of(null);
      }

      return of(control.value).pipe(
        delay(500),  // Debounce
        switchMap(username => userService.checkUsername(username)),
        map(exists => exists ? { usernameExists: true } : null)
      );
    };
  }

  static emailExists(userService: UserService): AsyncValidatorFn {
    return (control: AbstractControl): Observable<ValidationErrors | null> => {
      if (!control.value) {
        return of(null);
      }

      return userService.checkEmail(control.value).pipe(
        map(exists => exists ? { emailExists: true } : null)
      );
    };
  }
}
```

```typescript
// Usage
export class RegisterComponent implements OnInit {
  constructor(
    private fb: FormBuilder,
    private userService: UserService
  ) {}

  ngOnInit(): void {
    this.registerForm = this.fb.group({
      username: ['',
        [Validators.required],
        [AsyncValidators.usernameExists(this.userService)]  // Async validator
      ],
      email: ['',
        [Validators.required, Validators.email],
        [AsyncValidators.emailExists(this.userService)]
      ]
    });
  }
}
```

```html
<!-- Display async validation state -->
<input formControlName="username">
<span *ngIf="username?.pending">Checking username...</span>
<span *ngIf="username?.errors?.['usernameExists']">
  Username already taken
</span>
```

---

## Form Arrays

FormArray manages an array of FormControls or FormGroups.

### Basic FormArray

```typescript
export class SkillsComponent implements OnInit {
  skillsForm!: FormGroup;

  constructor(private fb: FormBuilder) {}

  ngOnInit(): void {
    this.skillsForm = this.fb.group({
      skills: this.fb.array([
        this.createSkill()
      ])
    });
  }

  get skills(): FormArray {
    return this.skillsForm.get('skills') as FormArray;
  }

  createSkill(): FormGroup {
    return this.fb.group({
      name: ['', Validators.required],
      level: ['beginner', Validators.required]
    });
  }

  addSkill(): void {
    this.skills.push(this.createSkill());
  }

  removeSkill(index: number): void {
    this.skills.removeAt(index);
  }

  onSubmit(): void {
    console.log(this.skillsForm.value);
    /*
    {
      skills: [
        { name: 'Angular', level: 'advanced' },
        { name: 'TypeScript', level: 'intermediate' }
      ]
    }
    */
  }
}
```

```html
<form [formGroup]="skillsForm" (ngSubmit)="onSubmit()">
  <div formArrayName="skills">
    <div *ngFor="let skill of skills.controls; let i = index" [formGroupName]="i">
      <input formControlName="name" placeholder="Skill name">

      <select formControlName="level">
        <option value="beginner">Beginner</option>
        <option value="intermediate">Intermediate</option>
        <option value="advanced">Advanced</option>
      </select>

      <button type="button" (click)="removeSkill(i)">Remove</button>
    </div>
  </div>

  <button type="button" (click)="addSkill()">Add Skill</button>
  <button type="submit" [disabled]="skillsForm.invalid">Submit</button>
</form>
```

### Complex FormArray Example

```typescript
export class PostEditorComponent implements OnInit {
  postForm!: FormGroup;

  constructor(private fb: FormBuilder) {}

  ngOnInit(): void {
    this.postForm = this.fb.group({
      title: ['', Validators.required],
      content: ['', Validators.required],
      tags: this.fb.array([]),
      sections: this.fb.array([
        this.createSection()
      ])
    });
  }

  get tags(): FormArray {
    return this.postForm.get('tags') as FormArray;
  }

  get sections(): FormArray {
    return this.postForm.get('sections') as FormArray;
  }

  createSection(): FormGroup {
    return this.fb.group({
      heading: ['', Validators.required],
      content: ['', Validators.required],
      imageUrl: ['']
    });
  }

  addTag(tag: string): void {
    this.tags.push(this.fb.control(tag));
  }

  removeTag(index: number): void {
    this.tags.removeAt(index);
  }

  addSection(): void {
    this.sections.push(this.createSection());
  }

  removeSection(index: number): void {
    this.sections.removeAt(index);
  }

  moveSection(from: number, to: number): void {
    const section = this.sections.at(from);
    this.sections.removeAt(from);
    this.sections.insert(to, section);
  }
}
```

---

## Dynamic Forms

Generate forms dynamically based on configuration.

### Form Configuration

```typescript
// form-config.model.ts
export interface FormFieldConfig {
  type: 'text' | 'email' | 'password' | 'number' | 'select' | 'checkbox' | 'textarea';
  name: string;
  label: string;
  value?: any;
  required?: boolean;
  placeholder?: string;
  options?: { label: string; value: any }[];
  validators?: any[];
}

export interface FormConfig {
  fields: FormFieldConfig[];
}
```

### Dynamic Form Component

```typescript
// dynamic-form.component.ts
export class DynamicFormComponent implements OnInit {
  @Input() config!: FormConfig;
  @Output() formSubmit = new EventEmitter<any>();

  form!: FormGroup;

  constructor(private fb: FormBuilder) {}

  ngOnInit(): void {
    this.form = this.createForm();
  }

  createForm(): FormGroup {
    const group: any = {};

    this.config.fields.forEach(field => {
      const validators = field.validators || [];
      if (field.required) {
        validators.push(Validators.required);
      }

      group[field.name] = [field.value || '', validators];
    });

    return this.fb.group(group);
  }

  onSubmit(): void {
    if (this.form.valid) {
      this.formSubmit.emit(this.form.value);
    }
  }
}
```

```html
<!-- dynamic-form.component.html -->
<form [formGroup]="form" (ngSubmit)="onSubmit()">
  <div *ngFor="let field of config.fields">
    <label [for]="field.name">{{ field.label }}</label>

    <!-- Text input -->
    <input
      *ngIf="field.type === 'text' || field.type === 'email' || field.type === 'password'"
      [type]="field.type"
      [id]="field.name"
      [formControlName]="field.name"
      [placeholder]="field.placeholder || ''">

    <!-- Number input -->
    <input
      *ngIf="field.type === 'number'"
      type="number"
      [id]="field.name"
      [formControlName]="field.name">

    <!-- Textarea -->
    <textarea
      *ngIf="field.type === 'textarea'"
      [id]="field.name"
      [formControlName]="field.name"
      [placeholder]="field.placeholder || ''">
    </textarea>

    <!-- Select -->
    <select
      *ngIf="field.type === 'select'"
      [id]="field.name"
      [formControlName]="field.name">
      <option *ngFor="let option of field.options" [value]="option.value">
        {{ option.label }}
      </option>
    </select>

    <!-- Checkbox -->
    <input
      *ngIf="field.type === 'checkbox'"
      type="checkbox"
      [id]="field.name"
      [formControlName]="field.name">

    <!-- Validation errors -->
    <div *ngIf="form.get(field.name)?.invalid && form.get(field.name)?.touched">
      <p *ngIf="form.get(field.name)?.errors?.['required']">
        {{ field.label }} is required
      </p>
    </div>
  </div>

  <button type="submit" [disabled]="form.invalid">Submit</button>
</form>
```

### Using Dynamic Form

```typescript
// parent.component.ts
export class ParentComponent {
  formConfig: FormConfig = {
    fields: [
      {
        type: 'text',
        name: 'username',
        label: 'Username',
        required: true,
        placeholder: 'Enter username'
      },
      {
        type: 'email',
        name: 'email',
        label: 'Email',
        required: true,
        validators: [Validators.email]
      },
      {
        type: 'select',
        name: 'role',
        label: 'Role',
        required: true,
        options: [
          { label: 'User', value: 'user' },
          { label: 'Admin', value: 'admin' },
          { label: 'Moderator', value: 'moderator' }
        ]
      },
      {
        type: 'checkbox',
        name: 'subscribe',
        label: 'Subscribe to newsletter',
        value: false
      }
    ]
  };

  onFormSubmit(data: any): void {
    console.log('Form submitted:', data);
  }
}
```

```html
<app-dynamic-form [config]="formConfig" (formSubmit)="onFormSubmit($event)">
</app-dynamic-form>
```

---

## Form Best Practices

### 1. Use Reactive Forms for Complex Forms

```typescript
// Good - reactive form with validation
export class ComplexFormComponent implements OnInit {
  form!: FormGroup;

  ngOnInit(): void {
    this.form = this.fb.group({
      // Clear structure, easy to test
    });
  }
}
```

### 2. Create Reusable Validators

```typescript
// Good - reusable validator
export function emailValidator(): ValidatorFn {
  return Validators.email;
}

// Bad - inline validation
password: ['', (control) => { /* validation logic */ }]
```

### 3. Use FormBuilder

```typescript
// Good - using FormBuilder
this.form = this.fb.group({
  name: ['', Validators.required],
  email: ['', [Validators.required, Validators.email]]
});

// Bad - verbose
this.form = new FormGroup({
  name: new FormControl('', Validators.required),
  email: new FormControl('', [Validators.required, Validators.email])
});
```

### 4. Unsubscribe from valueChanges

```typescript
// Good - unsubscribe
export class MyComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();

  ngOnInit(): void {
    this.form.valueChanges
      .pipe(takeUntil(this.destroy$))
      .subscribe(value => {
        // Handle changes
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
```

### 5. Provide User Feedback

```html
<!-- Good - clear feedback -->
<div *ngIf="email?.invalid && email?.touched">
  <p *ngIf="email?.hasError('required')" class="error">
    Email is required
  </p>
  <p *ngIf="email?.hasError('email')" class="error">
    Please enter a valid email
  </p>
</div>
```

### 6. Disable Submit Button

```html
<!-- Good - prevent invalid submission -->
<button type="submit" [disabled]="form.invalid || isSubmitting">
  {{ isSubmitting ? 'Submitting...' : 'Submit' }}
</button>
```

---

## TheDispatch Forms

### Post Creation Form

```typescript
// post-create.component.ts
export class PostCreateComponent implements OnInit {
  postForm!: FormGroup;
  isSubmitting = false;

  constructor(
    private fb: FormBuilder,
    private postService: PostService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.postForm = this.fb.group({
      title: ['', [
        Validators.required,
        Validators.minLength(5),
        Validators.maxLength(200)
      ]],
      content: ['', [
        Validators.required,
        Validators.minLength(50)
      ]],
      excerpt: ['', Validators.maxLength(300)],
      category: ['', Validators.required],
      tags: this.fb.array([]),
      imageUrl: [''],
      status: ['draft', Validators.required]
    });
  }

  get title() {
    return this.postForm.get('title');
  }

  get content() {
    return this.postForm.get('content');
  }

  get tags(): FormArray {
    return this.postForm.get('tags') as FormArray;
  }

  addTag(tag: string): void {
    if (tag && !this.tags.value.includes(tag)) {
      this.tags.push(this.fb.control(tag));
    }
  }

  removeTag(index: number): void {
    this.tags.removeAt(index);
  }

  onSubmit(): void {
    if (this.postForm.valid && !this.isSubmitting) {
      this.isSubmitting = true;

      const postData = this.postForm.value;

      this.postService.createPost(postData).subscribe({
        next: (post) => {
          this.router.navigate(['/posts', post.id]);
        },
        error: (error) => {
          console.error('Failed to create post:', error);
          this.isSubmitting = false;
        }
      });
    }
  }
}
```

```html
<!-- post-create.component.html -->
<form [formGroup]="postForm" (ngSubmit)="onSubmit()" class="post-form">
  <h2>Create New Post</h2>

  <div class="form-group">
    <label for="title">Title *</label>
    <input
      type="text"
      id="title"
      formControlName="title"
      placeholder="Enter post title">

    <div *ngIf="title?.invalid && title?.touched" class="errors">
      <p *ngIf="title?.hasError('required')">Title is required</p>
      <p *ngIf="title?.hasError('minlength')">
        Title must be at least 5 characters
      </p>
      <p *ngIf="title?.hasError('maxlength')">
        Title cannot exceed 200 characters
      </p>
    </div>
  </div>

  <div class="form-group">
    <label for="content">Content *</label>
    <textarea
      id="content"
      formControlName="content"
      rows="15"
      placeholder="Write your post content...">
    </textarea>

    <div *ngIf="content?.invalid && content?.touched" class="errors">
      <p *ngIf="content?.hasError('required')">Content is required</p>
      <p *ngIf="content?.hasError('minlength')">
        Content must be at least 50 characters
      </p>
    </div>
  </div>

  <div class="form-group">
    <label for="category">Category *</label>
    <select id="category" formControlName="category">
      <option value="">Select category</option>
      <option value="technology">Technology</option>
      <option value="business">Business</option>
      <option value="lifestyle">Lifestyle</option>
      <option value="other">Other</option>
    </select>
  </div>

  <div class="form-group">
    <label>Tags</label>
    <div class="tag-input">
      <input #tagInput type="text" placeholder="Add tag">
      <button type="button" (click)="addTag(tagInput.value); tagInput.value=''">
        Add
      </button>
    </div>

    <div class="tags">
      <span *ngFor="let tag of tags.controls; let i = index" class="tag">
        {{ tag.value }}
        <button type="button" (click)="removeTag(i)">×</button>
      </span>
    </div>
  </div>

  <div class="form-group">
    <label for="status">Status *</label>
    <select id="status" formControlName="status">
      <option value="draft">Draft</option>
      <option value="published">Published</option>
    </select>
  </div>

  <div class="form-actions">
    <button type="button" routerLink="/posts" class="btn-secondary">
      Cancel
    </button>
    <button
      type="submit"
      class="btn-primary"
      [disabled]="postForm.invalid || isSubmitting">
      {{ isSubmitting ? 'Creating...' : 'Create Post' }}
    </button>
  </div>
</form>
```

---

## Summary

Angular forms provide powerful tools for handling user input:

**Template-Driven Forms:**
- Simple syntax with ngModel
- Good for simple forms
- Less code required
- Harder to test

**Reactive Forms:**
- Explicit, immutable model
- Better for complex forms
- Type-safe and testable
- More powerful validation

**Key Concepts:**
1. FormControl - Individual form field
2. FormGroup - Group of form controls
3. FormArray - Dynamic list of controls
4. Validators - Synchronous and asynchronous validation
5. Form states - valid, invalid, touched, dirty, etc.

**Best Practices:**
- Use reactive forms for complex scenarios
- Create reusable validators
- Provide clear user feedback
- Disable buttons during submission
- Unsubscribe from observables
- Use FormBuilder for cleaner code

Mastering Angular forms enables building robust, user-friendly input experiences in applications like TheDispatch blog platform.
