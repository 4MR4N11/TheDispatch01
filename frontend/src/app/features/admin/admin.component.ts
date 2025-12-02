import edjsHTML from 'editorjs-html';
import { Component, inject, signal, OnInit } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';

import { Router } from '@angular/router';
import { ApiService } from '../../core/auth/api.service';
import { NotificationService } from '../../core/services/notification.service';
import { FormsModule } from '@angular/forms';
import { ReportStatus } from '../../shared/models/models';
import { UserResponse, PostResponse } from '../../shared/models/models';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-admin',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './admin.component.html',
  styleUrl: './admin.component.css'
})
export class AdminComponent implements OnInit {
  private readonly apiService = inject(ApiService);
  private readonly notificationService = inject(NotificationService);
  private readonly router = inject(Router);
  private readonly sanitizer = inject(DomSanitizer);

  protected readonly activeTab = signal<'reports' | 'users' | 'posts'>('reports');
  protected readonly reports = signal<any[]>([]);
  protected readonly users = signal<UserResponse[]>([]);
  protected readonly posts = signal<PostResponse[]>([]);
  protected readonly loading = signal(true);
  protected readonly currentPage = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly failedAvatars = signal<Set<string>>(new Set());

  ngOnInit() {
    this.loadReports();
  }

  switchTab(tab: 'reports' | 'users' | 'posts') {
    this.activeTab.set(tab);
    if (tab === 'reports') {
      this.loadReports();
    } else if (tab === 'users') {
      this.loadUsers();
    } else if (tab === 'posts') {
      this.loadPosts();
    }
  }

  private loadReports() {
    this.loading.set(true);
    this.apiService.getAllReports(this.currentPage(), 20).subscribe({
      next: (response) => {
        this.reports.set(response.content || response);
        this.totalPages.set(response.totalPages || 1);
        this.loading.set(false);
      },
      error: () => {
        this.notificationService.error('Failed to load reports');
        this.loading.set(false);
      }
    });
  }

  handleReport(reportId: number, action: ReportStatus, adminResponse: string) {
    this.apiService.handleReport(reportId, { action, adminResponse }).subscribe({
      next: () => {
        this.notificationService.success('Report handled successfully');
        this.loadReports();
      },
      error: () => {
        this.notificationService.error('Failed to handle report');
      }
    });
  }

  approveReport(reportId: number) {
    this.handleReport(reportId, 'APPROVED', 'Report approved by admin');
  }

  rejectReport(reportId: number) {
    this.handleReport(reportId, 'REJECTED', 'Report rejected by admin');
  }

  formatDate(date: string | Date): string {
    return new Date(date).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  }

  getStatusClass(status: string): string {
    switch(status?.toUpperCase()) {
      case 'PENDING': return 'status-pending';
      case 'APPROVED': return 'status-approved';
      case 'REJECTED': return 'status-rejected';
      default: return 'status-pending';
    }
  }

  viewPost(postId: number | undefined) {
    if (postId) {
      this.router.navigate(['/post', postId]);
    }
  }

  viewProfile(username: string | undefined) {
    if (username) {
      this.router.navigate(['/profile', username]);
    }
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

  private loadPosts() {
    this.loading.set(true);
    this.apiService.getAllPostsForAdmin().subscribe({
      next: (posts) => {
        this.posts.set(posts);
        this.loading.set(false);
      },
      error: () => {
        this.notificationService.error('Failed to load posts');
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

  deletePost(postId: number) {
    if (confirm('Are you sure you want to delete this post?')) {
      this.apiService.deletePost(postId).subscribe({
        next: () => {
          this.notificationService.success('Post deleted successfully');
          this.loadPosts();
        },
        error: () => {
          this.notificationService.error('Failed to delete post');
        }
      });
    }
  }

  hidePost(postId: number) {
    this.apiService.hidePost(postId).subscribe({
      next: () => {
        this.notificationService.success('Post hidden successfully');
        this.loadPosts();
      },
      error: () => {
        this.notificationService.error('Failed to hide post');
      }
    });
  }

  unhidePost(postId: number) {
    this.apiService.unhidePost(postId).subscribe({
      next: () => {
        this.notificationService.success('Post unhidden successfully');
        this.loadPosts();
      },
      error: () => {
        this.notificationService.error('Failed to unhide post');
      }
    });
  }

  getUserInitial(username: string): string {
    return username?.charAt(0).toUpperCase() || 'U';
  }

  getAvatarUrl(avatar?: string): string {
    if (!avatar) return '';
    if (avatar.startsWith('http')) {
      return avatar;
    }
    return `${environment.apiUrl}${avatar}`;
  }

  getPostHTML(content: string): SafeHtml | null {
    if (!content) return null;

    try {
      const contentJSON = typeof content === 'string' ? JSON.parse(content) : content;
      const edjsParser = edjsHTML({
        video: (block: any) => {
          return `<video controls style="max-width: 100%; border-radius: 8px;">
            <source src="${block.data.file.url}" type="video/mp4">
            Your browser does not support the video tag.
          </video>`;
        }
      });
      const html = edjsParser.parse(contentJSON);
      return this.sanitizer.bypassSecurityTrustHtml(html);
    } catch (err) {
      console.error('Error parsing Editor.js content', err);
      // Fallback: treat as plain text
      return this.sanitizer.bypassSecurityTrustHtml(`<p>${content}</p>`);
    }
  }

  getContentPreview(content: string, maxLength: number = 200): string {
    try {
      // Parse Editor.js JSON
      const contentJSON = typeof content === 'string' ? JSON.parse(content) : content;
      const edjsParser = edjsHTML({
        video: (block: any) => {
          return '[Video]';
        },
        image: (block: any) => {
          return '[Image]';
        }
      });
      const html = edjsParser.parse(contentJSON);

      // Convert to string if it's an array
      const htmlString = Array.isArray(html) ? html.join('') : String(html);

      // Strip HTML tags
      const strippedContent = htmlString.replace(/<[^>]*>/g, '');
      // Remove extra whitespace
      const cleanContent = strippedContent.replace(/\s+/g, ' ').trim();
      // Truncate to maxLength
      if (cleanContent.length <= maxLength) {
        return cleanContent;
      }
      return cleanContent.substring(0, maxLength) + '...';
    } catch (err) {
      console.error('Error parsing content for preview', err);
      // Fallback: return truncated raw content
      if (content.length <= maxLength) {
        return content;
      }
      return content.substring(0, maxLength) + '...';
    }
  }

  getExcerpt(content: string): string {
    return this.getContentPreview(content, 200);
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
