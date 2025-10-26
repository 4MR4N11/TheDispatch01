// app.component.ts
import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { CommonModule } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AuthService } from './core/auth/auth.service';
import { NavbarComponent } from './shared/components/navbar/navbar.component';
import { NotificationsComponent } from './shared/components/notifications/notifications.component';
import { ConfirmationModalComponent } from './shared/components/confirmation-modal/confirmation-modal.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, CommonModule, NavbarComponent, NotificationsComponent, ConfirmationModalComponent],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class AppComponent implements OnInit {
  protected readonly authService = inject(AuthService);
  protected readonly authInitialized = signal(false);

  constructor() {
    // Subscribe to auth initialization status with automatic cleanup
    this.authService.authInitialized$
      .pipe(takeUntilDestroyed())
      .subscribe(initialized => {
        this.authInitialized.set(initialized);
      });
  }

  ngOnInit() {
    // Initialize auth and wait for completion
    this.authService.initializeAuth();
  }
}