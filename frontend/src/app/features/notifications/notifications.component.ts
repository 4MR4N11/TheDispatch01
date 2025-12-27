import { Component, inject, OnInit, signal } from '@angular/core';

import { Router } from '@angular/router';
import { ApiService } from '../../core/auth/api.service';
import { NotificationResponse } from '../../shared/models/models';

@Component({
  selector: 'app-notifications',
  standalone: true,
  imports: [],
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
}
