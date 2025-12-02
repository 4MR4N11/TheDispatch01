// src/app/features/home/home.component.ts
import edjsHTML from 'editorjs-html';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';

import { FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiService } from '../../core/auth/api.service';
import { AuthService } from '../../core/auth/auth.service';
import { NotificationService } from '../../core/services/notification.service';
import { PostResponse } from '../../shared/models/models';
import { calculateReadingTime } from '../../shared/utils/reading-time.util';
import { environment } from '../../../environments/environment';
import { NewPostModalComponent } from '../../shared/components/new-post-modal.component';
import { EditPostModalComponent } from '../../shared/components/edit-post-modal.component';
import { ErrorHandler } from '../../core/utils/error-handler';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [FormsModule, ReactiveFormsModule, MatIconModule, NewPostModalComponent, EditPostModalComponent],
  templateUrl: './home.html',
  styleUrl: './home.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HomeComponent {
  private readonly apiService = inject(ApiService);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly notificationService = inject(NotificationService);
  private readonly sanitizer = inject(DomSanitizer);

  protected readonly posts = signal<PostResponse[]>([]);
  protected readonly currentUser = this.authService.currentUser;
  protected readonly loading = signal(true);
  protected readonly showNewPostModal = signal(false);
  protected readonly showEditPostModal = signal(false);
  protected readonly showReportModal = signal(false);
  protected readonly selectedPost = signal<PostResponse | null>(null);
  protected readonly openMenuPostId = signal<number | null>(null);
  protected readonly reportCategory = signal('');
  protected readonly reportMessage = signal('');
  protected readonly reporting = signal(false);
  protected readonly failedAvatars = signal<Set<string>>(new Set());

  protected readonly reportCategories = [
    'Harassment or bullying',
    'Spam or misleading',
    'Hate speech',
    'Violence or dangerous content',
    'False information',
    'Inappropriate content',
    'Other'
  ];

  constructor() {
    this.loadFeed();
  }

  togglePostMenu(postId: number, event: Event) {
    event.stopPropagation();
    if (this.openMenuPostId() === postId) {
      this.openMenuPostId.set(null);
    } else {
      this.openMenuPostId.set(postId);
    }
  }

  closeAllMenus() {
    this.openMenuPostId.set(null);
  }

  editPost(postId: number, event: Event) {
    event.stopPropagation();
    this.closeAllMenus();
    this.router.navigate(['/edit-post', postId]);
  }

  deletePost(postId: number, event: Event) {
    event.stopPropagation();
    if (confirm('Are you sure you want to delete this post?')) {
      this.apiService.deletePost(postId).subscribe({
        next: () => {
          this.notificationService.success('Post deleted successfully');
          this.loadPosts();
          this.closeAllMenus();
        },
        error: (error) => {
          this.notificationService.error(ErrorHandler.getErrorMessage(error, 'Failed to delete post'));
        }
      });
    }
  }

  reportPost(postId: number, event: Event) {
    event.stopPropagation();
    this.closeAllMenus();
    const post = this.posts().find(p => p.id === postId);
    if (post) {
      this.selectedPost.set(post);
      this.showReportModal.set(true);
      this.reportCategory.set('');
      this.reportMessage.set('');
    }
  }

  private loadFeed() {
    this.loading.set(true);
    this.apiService.getFeed().subscribe({
      next: (posts) => {
        this.posts.set(posts);
        this.loading.set(false);
      },
      error: (error) => {
        this.notificationService.error(ErrorHandler.getErrorMessage(error, 'Failed to load feed'));
        this.loading.set(false);
      }
    });
  }

  openNewPostModal() {
    this.router.navigate(['/create-post']);
  }

  closeNewPostModal() {
    this.showNewPostModal.set(false);
  }

  onPostCreated() {
    this.closeNewPostModal();
    this.loadFeed();
  }

  closeEditPostModal() {
    this.showEditPostModal.set(false);
    this.selectedPost.set(null);
  }

  onPostUpdated() {
    this.closeEditPostModal();
    this.loadFeed();
  }

  closeReportModal() {
    this.showReportModal.set(false);
    this.selectedPost.set(null);
    this.reportCategory.set('');
    this.reportMessage.set('');
  }

  submitReport() {
    const post = this.selectedPost();
    const category = this.reportCategory();
    const message = this.reportMessage();

    if (!post?.id || !category) {
      this.notificationService.warning('Please select a report category');
      return;
    }

    const reason = message
      ? `${category}: ${message}`
      : category;

    this.reporting.set(true);
    this.apiService.reportPost(post.id, { reason }).subscribe({
      next: () => {
        this.reporting.set(false);
        this.closeReportModal();
        this.notificationService.success('Post reported successfully');
      },
      error: (error) => {
        this.reporting.set(false);
        this.notificationService.error(ErrorHandler.getErrorMessage(error, 'Failed to report post'));
      }
    });
  }

  viewPost(id: number | undefined, event?: Event) {
    if (event) {
      event.stopPropagation();
    }
    if (id !== undefined) {
      this.router.navigate(['/post', id]);
    }
  }

  viewProfile(username: string) {
    this.router.navigate(['/profile', username]);
  }

  getReadingTime(content: string): string {
    return calculateReadingTime(content);
  }

  loadPosts() {
    this.loadFeed();
  }

  getAuthorInitial(author: string): string {
    return author?.charAt(0).toUpperCase() || 'U';
  }

  getTimeAgo(date: string | Date): string {
    const now = new Date();
    const postDate = new Date(date);
    const diffMs = now.getTime() - postDate.getTime();
    const diffMins = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMs / 3600000);
    const diffDays = Math.floor(diffMs / 86400000);

    if (diffMins < 1) return 'just now';
    if (diffMins < 60) return `${diffMins}m ago`;
    if (diffHours < 24) return `${diffHours}h ago`;
    if (diffDays < 7) return `${diffDays}d ago`;

    return postDate.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
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
    return this.getContentPreview(content, 150);
  }

  getFullAvatarUrl(avatar: string): string {
    if (!avatar) return '';
    // If avatar already has full URL, return it; otherwise prepend backend URL
    if (avatar.startsWith('http')) {
      return avatar;
    }
    return `${environment.apiUrl}${avatar}`;
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

  toggleLike(postId: number, event: Event) {
    event.stopPropagation();

    // Check if already liked
    const post = this.posts().find(p => p.id === postId);
    if (!post) return;

    const currentUsername = this.authService.currentUser()?.username;
    if (!currentUsername) return;

    const isLiked = post.likedByUsernames?.includes(currentUsername);

    if (isLiked) {
      this.apiService.unlikePost(postId).subscribe({
        next: () => {
          this.loadFeed();
        },
        error: (error) => {
          this.notificationService.error(ErrorHandler.getErrorMessage(error, 'Failed to unlike post'));
        }
      });
    } else {
      this.apiService.likePost(postId).subscribe({
        next: () => {
          this.loadFeed();
        },
        error: (error) => {
          this.notificationService.error(ErrorHandler.getErrorMessage(error, 'Failed to like post'));
        }
      });
    }
  }

  viewComments(postId: number, event: Event) {
    event.stopPropagation();
    this.router.navigate(['/post', postId]);
  }

  sharePost(postId: number, event: Event) {
    event.stopPropagation();
    const postUrl = `${window.location.origin}/post/${postId}`;
    navigator.clipboard.writeText(postUrl).then(() => {
      this.notificationService.success('Post link copied to clipboard!');
    }).catch(() => {
      this.notificationService.error('Failed to copy link');
    });
  }

}