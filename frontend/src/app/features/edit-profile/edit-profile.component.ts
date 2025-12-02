import { Component, inject, OnInit, signal } from '@angular/core';

import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiService } from '../../core/auth/api.service';
import { AuthService } from '../../core/auth/auth.service';
import { NotificationService } from '../../core/services/notification.service';
import { ErrorHandler } from '../../core/utils/error-handler';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-edit-profile',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './edit-profile.component.html',
  styleUrl: './edit-profile.component.css'
})
export class EditProfileComponent implements OnInit {
  private readonly apiService = inject(ApiService);
  private readonly authService = inject(AuthService);
  private readonly notificationService = inject(NotificationService);
  private readonly router = inject(Router);

  protected readonly loading = signal(false);
  protected readonly uploading = signal(false);

  // Form fields
  protected username = '';
  protected email = '';
  protected firstName = '';
  protected lastName = '';
  protected avatar = '';
  protected currentPassword = '';
  protected newPassword = '';
  protected confirmPassword = '';

  // Image preview
  protected avatarPreview = '';
  protected selectedFile: File | null = null;

  // Original values to detect changes
  private originalUsername = '';
  private originalEmail = '';

  ngOnInit() {
    this.loadCurrentUser();
  }

  private loadCurrentUser() {
    this.loading.set(true);
    this.apiService.getCurrentUser().subscribe({
      next: (user) => {
        this.username = user.username;
        this.email = user.email;
        this.firstName = user.firstname;
        this.lastName = user.lastname;
        this.avatar = user.avatar || '';

        // Set avatar preview with full URL
        if (user.avatar) {
          this.avatarPreview = user.avatar.startsWith('http')
            ? user.avatar
            : `${environment.apiUrl}${user.avatar}`;
        }

        this.originalUsername = user.username;
        this.originalEmail = user.email;

        this.loading.set(false);
      },
      error: (error) => {
        this.notificationService.error(ErrorHandler.getErrorMessage(error, 'Failed to load profile'));
        this.loading.set(false);
      }
    });
  }

  onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files[0]) {
      const file = input.files[0];

      // Validate file type
      const allowedTypes = ['image/jpeg', 'image/jpg', 'image/png', 'image/gif', 'image/webp'];
      if (!allowedTypes.includes(file.type)) {
        this.notificationService.error('Invalid file type. Only JPG, PNG, GIF, and WebP are allowed');
        return;
      }

      // Validate file size (5MB)
      if (file.size > 5 * 1024 * 1024) {
        this.notificationService.error('File size must be less than 5MB');
        return;
      }

      this.selectedFile = file;

      // Show preview
      const reader = new FileReader();
      if (file) {
        this.avatarPreview = URL.createObjectURL(file);
      }
      reader.onload = () => {
        this.avatarPreview = reader.result as string;
      };
      reader.readAsDataURL(file);
    }
  }

  removeAvatar() {
    this.selectedFile = null;
    this.avatarPreview = '';
    this.avatar = '';
  }

  async updateProfile() {
    // Validate passwords match if changing password
    if (this.newPassword) {
      if (this.newPassword !== this.confirmPassword) {
        this.notificationService.error('Passwords do not match');
        return;
      }
      if (this.newPassword.length < 8) {
        this.notificationService.error('Password must be at least 8 characters');
        return;
      }
      if (!this.currentPassword) {
        this.notificationService.error('Current password is required to change password');
        return;
      }
    }

    this.loading.set(true);

    try {
      // Upload avatar if selected
      let avatarUrl = this.avatar;
      if (this.selectedFile) {
        this.uploading.set(true);
        const formData = new FormData();
        formData.append('file', this.selectedFile);

        try {
          const uploadResponse = await this.apiService.uploadAvatar(formData).toPromise();
          avatarUrl = uploadResponse?.url || '';
          this.uploading.set(false);
        } catch (error) {
          this.uploading.set(false);
          this.notificationService.error(ErrorHandler.getUploadErrorMessage(error));
          this.loading.set(false);
          return;
        }
      }

      // Prepare update request
      const updateRequest: any = {
        username: this.username,
        email: this.email,
        firstname: this.firstName,
        lastname: this.lastName,
        avatar: avatarUrl
      };

      // Only include password fields if changing password
      if (this.newPassword) {
        updateRequest.currentPassword = this.currentPassword;
        updateRequest.newPassword = this.newPassword;
      }

      // Update profile
      this.apiService.updateProfile(updateRequest).subscribe({
        next: (updatedUser) => {
          this.notificationService.success('Profile updated successfully');

          // If username changed, update auth and redirect
          if (this.username !== this.originalUsername) {
            this.authService.logout();
            this.notificationService.info('Username changed. Please login again');
            this.router.navigate(['/login']);
          } else {
            // Refresh the current user in AuthService to update navbar
            this.authService.checkAuth().subscribe();

            // Clear password fields
            this.currentPassword = '';
            this.newPassword = '';
            this.confirmPassword = '';
            this.selectedFile = null;

            // Update original values
            this.originalUsername = updatedUser.username;
            this.originalEmail = updatedUser.email;

            this.loading.set(false);
          }
        },
        error: (error) => {
          this.notificationService.error(ErrorHandler.getErrorMessage(error, 'Failed to update profile. Please try again.'));
          this.loading.set(false);
        }
      });
    } catch (error) {
      this.notificationService.error(ErrorHandler.getErrorMessage(error, 'An unexpected error occurred'));
      this.loading.set(false);
    }
  }

  cancel() {
    this.router.navigate(['/profile', this.originalUsername]);
  }
}
