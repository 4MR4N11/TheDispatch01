// src/app/features/login/login.component.ts
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';
import { NotificationService } from '../../../core/services/notification.service';
import { ErrorHandler } from '../../../core/utils/error-handler';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginComponent {
  protected readonly usernameOrEmail = signal('');
  protected readonly password = signal('');
  protected readonly loading = signal(false);
  protected readonly errorMessage = signal<string | null>(null);

  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly notificationService = inject(NotificationService);

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
}