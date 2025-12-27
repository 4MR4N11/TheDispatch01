// src/app/features/users/profile.component.ts
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
  OnInit,
} from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ApiService } from '../../core/auth/api.service';
import { AuthService } from '../../core/auth/auth.service';
import { NotificationService } from '../../core/services/notification.service';
import {
  UserResponse,
  PostResponse,
} from '../../shared/models/models';
import { environment } from '../../../environments/environment';
import { NotFoundComponent } from '../not-found/not-found.component';
import { ErrorHandler } from '../../core/utils/error-handler';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule, DatePipe, FormsModule, MatIconModule, MatButtonModule, NotFoundComponent],
  templateUrl: './profile.component.html',
  styleUrl: './profile.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProfileComponent implements OnInit {
  private readonly apiService = inject(ApiService);
  private readonly authService = inject(AuthService);
  private readonly notificationService = inject(NotificationService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly user = signal<UserResponse | null>(null);
  protected readonly posts = signal<PostResponse[]>([]);
  protected readonly subscriptions = signal<UserResponse[]>([]);
  protected readonly followers = signal<UserResponse[]>([]);
  protected readonly isSubscribed = signal(false);
  protected readonly loading = signal(true);
  protected readonly subscribing = signal(false);
  protected readonly reporting = signal(false);
  protected readonly deleting = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly activeTab = signal<'posts' | 'followers' | 'following'>('posts');
  protected readonly reportCategory = signal('');
  protected readonly reportMessage = signal('');
  protected readonly showReportModal = signal(false);
  protected readonly promoting = signal(false);

  protected readonly reportCategories = [
    'Harassment or bullying',
    'Spam or misleading',
    'Hate speech',
    'Violence or dangerous content',
    'False information',
    'Inappropriate content',
    'Other'
  ];
  // reactive route username
  private readonly routeUsername = signal<string | null>(null);

  // Computed signals
  protected readonly currentUserUsername = computed(() => {
    const user = this.authService.currentUser();
    return user?.username ?? null;
  });

  protected readonly isOwnProfile = computed(() => {
    const currentUsername = this.currentUserUsername();
    const paramUsername = this.routeUsername();
    // If no username param → own profile
    if (paramUsername === null) return true;
    return currentUsername !== null && paramUsername === currentUsername;
  });

  protected readonly isAdmin = computed(() => {
    const user = this.authService.currentUser();
    return user?.role === 'ADMIN';
  });

  protected readonly avatarInitial = computed(() => {
    const user = this.user();
    return user?.username ? user.username.charAt(0).toUpperCase() : '?';
  });

  constructor() {
    // Subscribe to route param changes with automatic cleanup
    this.route.paramMap
      .pipe(takeUntilDestroyed())
      .subscribe((params) => {
        const username = params.get('username');
        this.routeUsername.set(username);
        this.loading.set(true);
        this.error.set(null);

        if (this.authService.isLoggedIn() && this.authService.currentUser() === null) {
          this.authService.checkAuth().subscribe({
            next: () => {
              this.initializeProfile(username);
            },
            error: () => {
              this.error.set('Authentication failed');
              this.loading.set(false);
            },
          });
        } else {
          this.initializeProfile(username);
        }
      });
  }

  ngOnInit() {
    // Initialization now handled in constructor
  }

  private initializeProfile(username: string | null) {
    this.loading.set(true);
    this.error.set(null);

    if (this.isOwnProfile()) {
      const currentUser = this.authService.currentUser();
      if (currentUser) {
        this.user.set(currentUser);
        this.loadPosts(currentUser.username);
        this.loadOwnProfileData();
      } else {
        // fallback
        this.apiService.getCurrentUser().subscribe({
          next: (u) => {
            this.user.set(u);
            this.loadPosts(u.username);
            this.loadOwnProfileData();
          },
          error: () => {
            this.error.set('Failed to load profile');
            this.loading.set(false);
          },
        });
      }
    } else if (username) {
      this.loadPosts(username);
      this.loadOtherProfileData(username);
    }
  }

  private loadPosts(username: string) {
    this.apiService.getPostsByUsername(username).subscribe({
      next: (posts) => {
        this.posts.set(posts);
      },
      error: () => {
        this.error.set('Failed to load posts');
      },
      complete: () => {
        this.loading.set(false);
      },
    });
  }

  private loadOwnProfileData() {
    // Use current user if available
    const currentUser = this.authService.currentUser();
    if (currentUser) {
      this.user.set(currentUser);
    }

    this.apiService.getSubscriptions().subscribe({
      next: (subs) => {
        this.subscriptions.set(subs);
      },
      error: () => {
        // Silently fail for subscriptions
      },
    });

    this.apiService.getFollowers().subscribe({
      next: (followers) => {
        this.followers.set(followers);
      },
      error: () => {
        // Silently fail for followers
      },
    });
  }

  private loadOtherProfileData(username: string) {
    this.apiService.getUserByUsername(username).subscribe({
      next: (u) => {
        if (u) {
          this.user.set(u);

          const currentUser = this.authService.currentUser();
          if (currentUser?.subscriptions?.includes(username)) {
            this.isSubscribed.set(true);
          }

          // Load followers and subscriptions for this user
          this.apiService.getFollowersByUsername(username).subscribe({
            next: (followers) => {
              this.followers.set(followers);
            },
            error: () => {
              // Silently fail for followers
            },
          });

          this.apiService.getSubscriptionsByUsername(username).subscribe({
            next: (subs) => {
              this.subscriptions.set(subs);
            },
            error: () => {
              // Silently fail for subscriptions
            },
          });
        } else {
          this.error.set('User not found');
        }
      },
      error: (err) => {
        if (err.status === 404) {
          this.error.set('User not found');
        } else {
          this.error.set('Failed to load user');
        }
        this.loading.set(false);
      },
    });
  }

  subscribe() {
    const targetId = this.user()?.id;
    if (!targetId) return;

    this.subscribing.set(true);
    this.apiService.subscribe(targetId).subscribe({
      next: () => {
        this.isSubscribed.set(true);
        this.subscribing.set(false);
        this.notificationService.success('Successfully subscribed!');
        this.refreshProfile();
      },
      error: (error) => {
        this.subscribing.set(false);
        this.notificationService.error(ErrorHandler.getErrorMessage(error, 'Failed to subscribe'));
      },
    });
  }

  unsubscribe() {
    const targetId = this.user()?.id;
    if (!targetId) return;

    this.subscribing.set(true);
    this.apiService.unsubscribe(targetId).subscribe({
      next: () => {
        this.isSubscribed.set(false);
        this.subscribing.set(false);
        this.notificationService.success('Successfully unsubscribed!');
        this.refreshProfile();
      },
      error: (error) => {
        this.subscribing.set(false);
        this.notificationService.error(ErrorHandler.getErrorMessage(error, 'Failed to unsubscribe'));
      },
    });
  }

  private refreshProfile() {
    if (this.isOwnProfile()) {
      this.loadOwnProfileData();
    } else {
      const username = this.routeUsername();
      if (username) {
        // Reload followers and subscriptions for the other user
        this.apiService.getFollowersByUsername(username).subscribe({
          next: (followers) => {
            this.followers.set(followers);
          },
        });

        this.apiService.getSubscriptionsByUsername(username).subscribe({
          next: (subs) => {
            this.subscriptions.set(subs);
          },
        });
      }
    }
  }

  viewPost(id: number | undefined) {
    if (id) {
      this.router.navigate(['/post', id]);
    }
  }

  viewUser(username: string | undefined) {
    if (username) {
      this.router.navigate(['/profile', username]);
    }
  }

  openReportModal() {
    this.showReportModal.set(true);
    this.reportCategory.set('');
    this.reportMessage.set('');
  }

  closeReportModal() {
    this.showReportModal.set(false);
    this.reportCategory.set('');
    this.reportMessage.set('');
  }

  submitReport() {
    const user = this.user();
    const category = this.reportCategory();
    const message = this.reportMessage();

    if (!user?.id || !category) {
      this.notificationService.warning('Please select a report category');
      return;
    }

    const reason = message
      ? `${category}: ${message}`
      : category;

    this.reporting.set(true);
    this.apiService.reportUser(user.id, { reason }).subscribe({
      next: () => {
        this.reporting.set(false);
        this.closeReportModal();
        this.notificationService.success('User reported successfully');
      },
      error: (error) => {
        this.reporting.set(false);
        this.notificationService.error(ErrorHandler.getErrorMessage(error, 'Failed to report user'));
      }
    });
  }

  deleteUser() {
    const userId = this.user()?.id;
    if (userId && this.isAdmin()) {
      if (confirm('Are you sure you want to delete this user? This action cannot be undone.')) {
        this.deleting.set(true);
        this.apiService.deleteUser(userId).subscribe({
          next: () => {
            this.deleting.set(false);
            this.notificationService.success('User deleted successfully');
            this.router.navigate(['/']);
          },
          error: (error) => {
            this.deleting.set(false);
            this.notificationService.error(ErrorHandler.getErrorMessage(error, 'Failed to delete user'));
          },
        });
      }
    }
  }

  isUserAdmin(): boolean {
    const user = this.user();
    return user?.role === 'ADMIN';
  }
  promoteToAdmin() {
    const userId = this.user()?.id;
    if (userId && this.isAdmin()) {
      this.promoting.set(true);
      this.apiService.promoteToAdmin(userId).subscribe({
        next: () => {
          this.notificationService.success('User promoted to admin successfully');
          this.promoting.set(false);
          // Optionally refresh user data
          const username = this.routeUsername();
          if (username) {
            this.loadOtherProfileData(username);
          }
        },
        error: (error) => {
          this.promoting.set(false);
          this.notificationService.error(ErrorHandler.getErrorMessage(error, 'Failed to promote user to admin'));
        },
      });
    }
  }
}
