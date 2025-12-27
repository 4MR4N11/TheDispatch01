// src/app/features/register/register.component.ts
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';
import { NotificationService } from '../../../core/services/notification.service';
import { ErrorHandler } from '../../../core/utils/error-handler';
import { isValidEmail, validatePassword, validateUsername } from '../../../shared/utils/format.util';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [FormsModule, RouterModule],
  templateUrl: './register.component.html',
  styleUrl: './register.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RegisterComponent {
  protected readonly firstname = signal('');
  protected readonly lastname = signal('');
  protected readonly username = signal('');
  protected readonly email = signal('');
  protected readonly password = signal('');
  protected readonly loading = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly notificationService = inject(NotificationService);

  register() {
    // Basic validation
    if (!this.firstname() || !this.lastname() || !this.username() || !this.email() || !this.password()) {
      this.errorMessage.set('All fields are required');
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
      this.errorMessage.set(passwordValidation.errors[0]); // Show first error
      return;
    }

    if (!validateUsername(this.username())) {
      this.errorMessage.set('Username must be between 4 and 20 characters');
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);

    this.authService.register({
      firstname: this.firstname(),
      lastname: this.lastname(),
      username: this.username(),
      email: this.email(),
      password: this.password()
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
  
}