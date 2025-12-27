import { Component, inject, signal, HostListener, OnInit, OnDestroy } from '@angular/core';
import { Router, RouterModule } from '@angular/router';

import { AuthService } from '../../../core/auth/auth.service';
import { ApiService } from '../../../core/auth/api.service';
import { NotificationResponse } from '../../models/models';
import { ConfirmationModalService } from '../../services/confirmation-modal.service';
import { ThemeService } from '../../../core/services/theme.service';
import { interval, Subscription } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { getAuthorInitial, getTimeAgo } from '../../utils/format.util';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [RouterModule],
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
  protected readonly mobileMenuOpen = signal(false);
  private pollSubscription?: Subscription;
  protected readonly userQuery = signal('');
  protected readonly userResults = signal<any[]>([]);
  protected readonly userSearchOpen = signal(false);
  private searchTimeout: any;

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
    if (!target.closest('.user-search-container')) {
      this.closeUserSearch();
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
    return getAuthorInitial(user?.username || '');
  }

  getTimeAgo = getTimeAgo;

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
    } else if (notification.type === 'POST_LIKE' || notification.type === 'POST_COMMENT' || notification.type === 'NEW_POST') {
      if (notification.postId) {
        this.router.navigate(['/post', notification.postId]);
      }
    } else if (notification.type === 'COMMENT_REPLY') {
      if (notification.postId) {
        this.router.navigate(['/post', notification.postId]);
      }
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

  onUserSearch(event: any) {
    const q = event.target.value.trim();
    this.userQuery.set(q);

    if (this.searchTimeout) clearTimeout(this.searchTimeout);

    // debounce
    this.searchTimeout = setTimeout(() => {
      if (q.length < 2) {
        this.userResults.set([]);
        this.userSearchOpen.set(false);
        return;
      }

      this.apiService.searchUsers(q).subscribe({
        next: (users) => {
          this.userResults.set(users);
          this.userSearchOpen.set(users.length > 0);
        },
        error: () => {
          this.userResults.set([]);
          this.userSearchOpen.set(false);
        }
      });
    }, 250);  
  }

  selectUser(user: any) {
    this.userSearchOpen.set(false);
    this.userQuery.set('');
    this.router.navigate(['/profile', user.username]);
  }

  // Close on outside click
  closeUserSearch() {
    this.userSearchOpen.set(false);
  }
}
