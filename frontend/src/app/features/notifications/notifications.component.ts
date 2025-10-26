import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { ApiService } from '../../core/auth/api.service';
import { NotificationResponse } from '../../shared/models/models';

@Component({
  selector: 'app-notifications',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './notifications.component.html',
  styleUrl: './notifications.component.css'
})
export class NotificationsComponent implements OnInit {
  private readonly apiService = inject(ApiService);
  private readonly router = inject(Router);

  protected readonly notifications = signal<NotificationResponse[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal('');
  protected readonly hasUnread = signal(false);
  protected readonly failedAvatars = signal<Set<string>>(new Set());

  ngOnInit() {
    this.loadNotifications();
  }

  private loadNotifications() {
    this.loading.set(true);
    this.apiService.getAllNotifications().subscribe({
      next: (notifications) => {
        this.notifications.set(notifications);
        this.hasUnread.set(notifications.some(n => !n.read));
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Failed to load notifications');
        this.loading.set(false);
      }
    });
  }

  protected markAsRead(notification: NotificationResponse, event: Event) {
    event.stopPropagation();
    if (notification.read) return;

    this.apiService.markNotificationAsRead(notification.id).subscribe({
      next: () => {
        // Update local state
        this.notifications.update(notifs => {
          const updated = notifs.map(n => n.id === notification.id ? { ...n, read: true } : n);
          this.hasUnread.set(updated.some(n => !n.read));
          return updated;
        });
      },
      error: () => {
        // Silently fail - not critical
      }
    });
  }

  protected markAllAsRead() {
    this.apiService.markAllNotificationsAsRead().subscribe({
      next: () => {
        this.notifications.update(notifs =>
          notifs.map(n => ({ ...n, read: true }))
        );
        this.hasUnread.set(false);
      },
      error: () => {
        // Silently fail - not critical
      }
    });
  }

  protected handleNotificationClick(notification: NotificationResponse) {
    // Mark as read
    if (!notification.read) {
      this.apiService.markNotificationAsRead(notification.id).subscribe({
        next: () => {
          this.notifications.update(notifs =>
            notifs.map(n => n.id === notification.id ? { ...n, read: true } : n)
          );
        }
      });
    }

    // Navigate based on notification type
    if (notification.type === 'NEW_FOLLOWER') {
      this.router.navigate(['/profile', notification.actorUsername]);
    } else if (notification.type === 'POST_LIKE' || notification.type === 'POST_COMMENT') {
      if (notification.postId) {
        this.router.navigate(['/posts', notification.postId]);
      }
    } else if (notification.type === 'COMMENT_REPLY') {
      if (notification.postId) {
        this.router.navigate(['/posts', notification.postId]);
      }
    }
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

  protected goBack() {
    this.router.navigate(['/']);
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
}
