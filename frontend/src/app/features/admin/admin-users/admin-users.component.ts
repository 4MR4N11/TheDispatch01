import { Component, inject, signal, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { ApiService } from '../../../core/auth/api.service';
import { NotificationService } from '../../../core/services/notification.service';
import { UserResponse } from '../../../shared/models/models';
import { environment } from '../../../../environments/environment';

@Component({
  selector: 'app-admin-users',
  standalone: true,
  imports: [],
  templateUrl: './admin-users.component.html',
  styleUrl: './admin-users.component.css'
})
export class AdminUsersComponent implements OnInit {
  private readonly apiService = inject(ApiService);
  private readonly notificationService = inject(NotificationService);
  private readonly router = inject(Router);

  protected readonly users = signal<UserResponse[]>([]);
  protected readonly loading = signal(true);
  protected readonly promoting = signal(false);

  ngOnInit() {
    this.loadUsers();
  }

  private loadUsers() {
    this.loading.set(true);
    this.apiService.getUsers().subscribe({
      next: (users) => {
        this.users.set(users);
        this.loading.set(false);
      },
      error: () => {
        this.notificationService.error('Failed to load users');
        this.loading.set(false);
      }
    });
  }

  deleteUser(userId: number) {
    if (confirm('Are you sure you want to delete this user?')) {
      this.apiService.deleteUser(userId).subscribe({
        next: () => {
          this.notificationService.success('User deleted successfully');
          this.loadUsers();
        },
        error: () => {
          this.notificationService.error('Failed to delete user');
        }
      });
    }
  }

  banUser(userId: number) {
    if (confirm('Are you sure you want to ban this user?')) {
      this.apiService.banUser(userId).subscribe({
        next: () => {
          this.notificationService.success('User banned successfully');
          this.loadUsers();
        },
        error: () => {
          this.notificationService.error('Failed to ban user');
        }
      });
    }
  }

  unbanUser(userId: number) {
    if (confirm('Are you sure you want to unban this user?')) {
      this.apiService.unbanUser(userId).subscribe({
        next: () => {
          this.notificationService.success('User unbanned successfully');
          this.loadUsers();
        },
        error: () => {
          this.notificationService.error('Failed to unban user');
        }
      });
    }
  }

  promoteToAdmin(userId: number) {
    if (confirm('Are you sure you want to promote this user to admin?')) {
      this.promoting.set(true);
      this.apiService.promoteToAdmin(userId).subscribe({
        next: () => {
          this.notificationService.success('User promoted to admin successfully');
          this.promoting.set(false);
          this.loadUsers();
        },
        error: () => {
          this.promoting.set(false);
          this.notificationService.error('Failed to promote user to admin');
        }
      });
    }
  }

  viewProfile(username: string | undefined) {
    if (username) {
      this.router.navigate(['/profile', username]);
    }
  }

  getUserInitial(username: string): string {
    return username?.charAt(0).toUpperCase() || 'U';
  }
}
