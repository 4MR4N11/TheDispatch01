import { Component, inject, signal, HostListener, OnInit, OnDestroy } from '@angular/core';
import { Router, RouterModule } from '@angular/router';
import { CommonModule } from '@angular/common';
import { AuthService } from '../../../core/auth/auth.service';
import { ApiService } from '../../../core/auth/api.service';
import { NotificationResponse } from '../../models/models';
import { ConfirmationModalService } from '../../services/confirmation-modal.service';
import { ThemeService } from '../../../core/services/theme.service';
import { interval, Subscription } from 'rxjs';
import { environment } from '../../../../environments/environment';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [RouterModule, CommonModule],
  templateUrl: './navbar.component.html',
  styleUrls: ['./navbar.component.css']
})
export class NavbarComponent implements OnInit, OnDestroy {
  protected readonly authService = inject(AuthService);
  protected readonly themeService = inject(ThemeService);
  private readonly apiService = inject(ApiService);
  private readonly confirmationService = inject(ConfirmationModalService);
  private readonly router = inject(Router);
  protected readonly dropdownOpen = signal(false);
  protected readonly notifDropdownOpen = signal(false);
  protected readonly unreadCount = signal(0);
  protected readonly notifications = signal<NotificationResponse[]>([]);
  protected readonly loadingNotifications = signal(false);
  protected readonly failedAvatars = signal<Set<string>>(new Set());
  protected readonly mobileMenuOpen = signal(false);
  private pollSubscription?: Subscription;

  toggleMobileMenu() {
    this.mobileMenuOpen.set(!this.mobileMenuOpen());
  }

  closeMobileMenu() {
    this.mobileMenuOpen.set(false);
  }

  toggleNotifDropdown() {
    const isOpen = !this.notifDropdownOpen();
    this.notifDropdownOpen.set(isOpen);

    if (isOpen && this.notifications().length === 0) {
      this.loadNotifications();
    }
  }

  closeNotifDropdown() {
    this.notifDropdownOpen.set(false);
  }

  toggleDropdown() {
    this.dropdownOpen.set(!this.dropdownOpen());
  }

  toggleTheme() {
    this.themeService.toggleTheme();
  }


  closeDropdown() {
    this.dropdownOpen.set(false);
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent) {
    const target = event.target as HTMLElement;
    if (!target.closest('.user-menu')) {
      this.closeDropdown();
    }
    if (!target.closest('.notification-menu')) {
      this.closeNotifDropdown();
    }
  }

  logout() {
    this.authService.logout();
    this.router.navigate(['/login']);
  }

  isAdmin(): boolean {
    const user = this.authService.currentUser();
    return user?.role === 'ADMIN';
  }

  getUserInitial(): string {
    const user = this.authService.currentUser();
    return user?.username?.charAt(0).toUpperCase() || 'U';
  }

  getAvatarUrl(): string {
    const user = this.authService.currentUser();
    if (!user?.avatar) return '';
    // If avatar already has full URL, return it; otherwise prepend backend URL
    if (user.avatar.startsWith('http')) {
      return user.avatar;
    }
    return `${environment.apiUrl}${user.avatar}`;
  }

  onAvatarError(avatarUrl: string) {
    const currentFailed = this.failedAvatars();
    const newFailed = new Set(currentFailed);
    newFailed.add(avatarUrl);
    this.failedAvatars.set(newFailed);
  }

  isAvatarFailed(avatarUrl: string | null | undefined): boolean {
    if (!avatarUrl) return false;
    return this.failedAvatars().has(avatarUrl);
  }

  ngOnInit() {
    if (this.authService.isLoggedIn()) {
      this.loadUnreadCount();
      // Poll for new notifications every 30 seconds
      this.pollSubscription = interval(30000).subscribe(() => {
        this.loadUnreadCount();
      });
    }
  }

  ngOnDestroy() {
    this.pollSubscription?.unsubscribe();
  }

  private loadUnreadCount() {
    this.apiService.getUnreadNotificationsCount().subscribe({
      next: (count) => this.unreadCount.set(count),
      error: () => {
        // Silently fail - not critical for user experience
      }
    });
  }

  private loadNotifications() {
    this.loadingNotifications.set(true);
    this.apiService.getNotifications(0, 10).subscribe({
      next: (notifications) => {
        this.notifications.set(notifications);
        this.loadingNotifications.set(false);
      },
      error: () => {
        // Silently fail - not critical for user experience
        this.loadingNotifications.set(false);
      }
    });
  }

  protected goToNotifications() {
    this.closeNotifDropdown();
    this.router.navigate(['/notifications']);
  }

  protected handleNotificationClick(notification: NotificationResponse) {
    // Mark as read
    if (!notification.read) {
      this.apiService.markNotificationAsRead(notification.id).subscribe({
        next: () => {
          this.notifications.update(notifs =>
            notifs.map(n => n.id === notification.id ? { ...n, read: true } : n)
          );
          this.loadUnreadCount();
        }
      });
    }

    this.closeNotifDropdown();

    // Navigate based on notification type
    if (notification.type === 'NEW_FOLLOWER') {
      this.router.navigate(['/profile', notification.actorUsername]);
    } else if (notification.type === 'POST_LIKE' || notification.type === 'POST_COMMENT') {
      if (notification.postId) {
        this.router.navigate(['/post', notification.postId]);
      }
    } else if (notification.type === 'COMMENT_REPLY') {
      if (notification.postId) {
        this.router.navigate(['/post', notification.postId]);
      }
    }
  }

  protected getTimeAgo(date: string | Date): string {
    const now = new Date();
    const notificationDate = new Date(date);
    const diffMs = now.getTime() - notificationDate.getTime();
    const diffMins = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMs / 3600000);
    const diffDays = Math.floor(diffMs / 86400000);

    if (diffMins < 1) return 'Just now';
    if (diffMins < 60) return `${diffMins}m ago`;
    if (diffHours < 24) return `${diffHours}h ago`;
    if (diffDays < 7) return `${diffDays}d ago`;

    return notificationDate.toLocaleDateString();
  }

  protected getNotificationIconSvg(type: string): string {
    switch (type) {
      case 'NEW_FOLLOWER':
        return '<svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor"><path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z"/></svg>';
      case 'POST_LIKE':
        return '<svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor"><path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"/></svg>';
      case 'POST_COMMENT':
        return '<svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor"><path d="M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2z"/></svg>';
      case 'COMMENT_REPLY':
        return '<svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor"><path d="M10 9V5l-7 7 7 7v-4.1c5 0 8.5 1.6 11 5.1-1-5-4-10-11-11z"/></svg>';
      case 'POST_MENTIONED':
        return '<svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10h5v-2h-5c-4.34 0-8-3.66-8-8s3.66-8 8-8 8 3.66 8 8v1.43c0 .79-.71 1.57-1.5 1.57s-1.5-.78-1.5-1.57V12c0-2.76-2.24-5-5-5s-5 2.24-5 5 2.24 5 5 5c1.38 0 2.64-.56 3.54-1.47.65.89 1.77 1.47 2.96 1.47 1.97 0 3.5-1.6 3.5-3.57V12c0-5.52-4.48-10-10-10zm0 13c-1.66 0-3-1.34-3-3s1.34-3 3-3 3 1.34 3 3-1.34 3-3 3z"/></svg>';
      default:
        return '<svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor"><path d="M12 22c1.1 0 2-.9 2-2h-4c0 1.1.89 2 2 2zm6-6v-5c0-3.07-1.64-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.63 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2z"/></svg>';
    }
  }

  protected markAllAsRead() {
    this.apiService.markAllNotificationsAsRead().subscribe({
      next: () => {
        this.notifications.update(notifs =>
          notifs.map(n => ({ ...n, read: true }))
        );
        this.unreadCount.set(0);
      },
      error: () => {
        // Silently fail - not critical
      }
    });
  }
}
