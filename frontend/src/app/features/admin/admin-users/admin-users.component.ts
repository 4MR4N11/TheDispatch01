import { Component, inject, signal, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { ApiService } from '../../../core/auth/api.service';
import { NotificationService } from '../../../core/services/notification.service';
import { ConfirmationModalService } from '../../../shared/services/confirmation-modal.service';
import { UserResponse } from '../../../shared/models/models';
import { environment } from '../../../../environments/environment';
import { ErrorHandler } from '../../../core/utils/error-handler';
import { getAuthorInitial } from '../../../shared/utils/format.util';

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
  private readonly confirmationService = inject(ConfirmationModalService);
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
      error: (error) => {
        this.notificationService.error(ErrorHandler.getErrorMessage(error, 'Failed to load users'));
        this.loading.set(false);
      }
    });
  }

  async deleteUser(userId: number) {
    const confirmed = await this.confirmationService.open({
      title: 'Delete User',
      message: 'Are you sure you want to delete this user?',
      confirmText: 'Delete',
      cancelText: 'Cancel'
    });

    if (confirmed) {
      this.apiService.deleteUser(userId).subscribe({
        next: () => {
          this.notificationService.success('User deleted successfully');
          this.loadUsers();
        },
        error: (error) => {
          this.notificationService.error(ErrorHandler.getErrorMessage(error, 'Failed to delete user'));
        }
      });
    }
  }

  async banUser(userId: number) {
    const confirmed = await this.confirmationService.open({
      title: 'Ban User',
      message: 'Are you sure you want to ban this user?',
      confirmText: 'Ban',
      cancelText: 'Cancel'
    });

    if (confirmed) {
      this.apiService.banUser(userId).subscribe({
        next: () => {
          this.notificationService.success('User banned successfully');
          this.loadUsers();
        },
        error: (error) => {
          this.notificationService.error(ErrorHandler.getErrorMessage(error, 'Failed to ban user'));
        }
      });
    }
  }

  async unbanUser(userId: number) {
    const confirmed = await this.confirmationService.open({
      title: 'Unban User',
      message: 'Are you sure you want to unban this user?',
      confirmText: 'Unban',
      cancelText: 'Cancel'
    });

    if (confirmed) {
      this.apiService.unbanUser(userId).subscribe({
        next: () => {
          this.notificationService.success('User unbanned successfully');
          this.loadUsers();
        },
        error: (error) => {
          this.notificationService.error(ErrorHandler.getErrorMessage(error, 'Failed to unban user'));
        }
      });
    }
  }

  async promoteToAdmin(userId: number) {
    const confirmed = await this.confirmationService.open({
      title: 'Promote to Admin',
      message: 'Are you sure you want to promote this user to admin?',
      confirmText: 'Promote',
      cancelText: 'Cancel'
    });

    if (confirmed) {
      this.promoting.set(true);
      this.apiService.promoteToAdmin(userId).subscribe({
        next: () => {
          this.notificationService.success('User promoted to admin successfully');
          this.promoting.set(false);
          this.loadUsers();
        },
        error: (error) => {
          this.promoting.set(false);
          this.notificationService.error(ErrorHandler.getErrorMessage(error, 'Failed to promote user to admin'));
        }
      });
    }
  }

  viewProfile(username: string | undefined) {
    if (username) {
      this.router.navigate(['/profile', username]);
    }
  }

  getUserInitial = getAuthorInitial;
}
