// src/app/features/posts/post-detail.component.ts
import edjsHTML from 'editorjs-html';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ApiService } from '../../core/auth/api.service';
import { AuthService } from '../../core/auth/auth.service';
import { NotificationService } from '../../core/services/notification.service';
import { PostResponse } from '../../shared/models/models';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { calculateReadingTime } from '../../shared/utils/reading-time.util';
import { environment } from '../../../environments/environment';
import { EditPostModalComponent } from '../../shared/components/edit-post-modal.component';
import { NotFoundComponent } from '../not-found/not-found.component';
@Component({
  selector: 'app-post-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, DatePipe, MatIconModule, MatButtonModule, EditPostModalComponent, NotFoundComponent],
  templateUrl: './posts.component.html',
  styleUrl: './posts.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PostDetailComponent {
  private readonly apiService = inject(ApiService);
  private readonly authService = inject(AuthService);
  private readonly notificationService = inject(NotificationService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  protected readonly post = signal<PostResponse | null>(null);
  protected readonly currentUser = this.authService.currentUser;
  protected readonly commentContent = signal('');
  protected readonly isLiked = signal(false);
  protected readonly commentEdit = signal<{ id: number; content: string } | null>(null);
  protected readonly submittingComment = signal(false);
  protected readonly togglingLike = signal(false);
  protected readonly updatingComment = signal(false);
  protected readonly deletingComment = signal<number | null>(null);
  protected readonly reporting = signal(false);
  protected readonly showReportModal = signal(false);
  protected readonly reportCategory = signal('');
  protected readonly reportMessage = signal('');
  protected readonly showEditPostModal = signal(false);
  protected readonly failedAvatars = signal<Set<string>>(new Set());
  private readonly sanitizer = inject(DomSanitizer);
  protected readonly deletingPost = signal(false);

  protected readonly blocks = signal<any[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
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
    const postId = Number(this.route.snapshot.paramMap.get('id'));
    this.loading.set(true);
    this.error.set(null);

    this.apiService.getPostById(postId).subscribe({
      next: (p) => {
        if (p.comments?.length) {
          p.comments = this.sortComments(p.comments);
        }
        this.post.set(p);
        this.parsePostContent();
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        if (err.status === 404) {
          this.error.set('Post not found');
        } else {
          this.error.set('Failed to load post');
        }
      }
    });
    this.apiService.checkIfPostLiked(postId).subscribe({
      next: (liked) => this.isLiked.set(liked),
      error: () => { } // Silently ignore like check errors
    });
  }


  private parsePostContent() {
    const post = this.post();
    if (!post?.content) {
      this.blocks.set([]);
      return;
    }

    try {
      const contentJSON =
        typeof post.content === 'string' ? JSON.parse(post.content) : post.content;
      this.blocks.set(contentJSON.blocks || []);
    } catch (err) {
      console.error('Error parsing post content', err);
      this.blocks.set([]);
    }
  }

  addComment() {
    const post = this.post();
    const content = this.commentContent();

    if (!post?.id || !content.trim()) {
      this.notificationService.warning('Please enter a comment');
      return;
    }

    this.submittingComment.set(true);
    this.apiService.createComment(post.id, { content })
      .subscribe({
        next: () => {
          this.commentContent.set('');
          this.submittingComment.set(false);
          this.notificationService.success('Comment added successfully!');
          this.refreshPost();
        },
        error: () => {
          this.submittingComment.set(false);
          this.notificationService.error('Failed to add comment');
        }
      });
  }

  toggleLike() {
    const post = this.post();

    if (!post?.id || this.togglingLike()) {
      return; // Prevent race condition by checking if already toggling
    }

    this.togglingLike.set(true);
    const action = this.isLiked()
      ? this.apiService.unlikePost(post.id)
      : this.apiService.likePost(post.id);

    action.subscribe({
      next: () => {
        this.isLiked.update(v => !v);
        this.togglingLike.set(false);
        // Update like count locally instead of refetching
        this.post.update(p => {
          if (p) {
            return {
              ...p,
              likeCount: this.isLiked() ? p.likeCount + 1 : p.likeCount - 1
            };
          }
          return p;
        });
      },
      error: () => {
        this.togglingLike.set(false);
        this.notificationService.error('Failed to update like');
      }
    });
  }

  startEdit(commentId: number, content: string) {
    this.commentEdit.set({ id: commentId, content });
  }

  cancelEdit() {
    this.commentEdit.set(null);
  }

  updateEditContent(content: string) {
    const editData = this.commentEdit();
    if (editData) {
      this.commentEdit.set({ ...editData, content });
    }
  }

  updateComment() {
    const editData = this.commentEdit();

    if (!editData || !editData.content.trim()) {
      this.notificationService.warning('Comment cannot be empty');
      return;
    }

    this.updatingComment.set(true);
    this.apiService.updateComment(editData.id, { content: editData.content })
      .subscribe({
        next: () => {
          this.commentEdit.set(null);
          this.updatingComment.set(false);
          this.notificationService.success('Comment updated successfully!');
          this.refreshPost();
        },
        error: () => {
          this.updatingComment.set(false);
          this.notificationService.error('Failed to update comment');
        }
      });
  }

  getPostHTML(): SafeHtml | null {
    const post = this.post();
    if (!post?.content) return null;

    try {
      const contentJSON =
        typeof post.content === 'string' ? JSON.parse(post.content) : post.content;

      const edjsParser = edjsHTML({
        video: (block: any) => {
          // Extract the video URL from block.data.file.url
          const url = block.data?.file?.url || '';
          if (!url) return '';
          const caption = block.data.caption || '';
          return `<video controls src="${url}" style="max-width:100%; border-radius:8px;"></video>
                  ${caption ? `<p class="video-caption">${caption}</p>` : ''}`;
        },
      });
      const htmlBlocks = edjsParser.parse(contentJSON);
      const html = htmlBlocks;
      console.log('Parsed HTML:', html);
      return this.sanitizer.bypassSecurityTrustHtml(html);
    } catch (err) {
      console.error('Error parsing Editor.js content', err);
      return null;
    }
  }


  deleteComment(commentId: number) {
    this.deletingComment.set(commentId);
    this.apiService.deleteComment(commentId).subscribe({
      next: () => {
        this.deletingComment.set(null);
        this.notificationService.success('Comment deleted successfully!');
        this.refreshPost();
      },
      error: () => {
        this.deletingComment.set(null);
        this.notificationService.error('Failed to delete comment');
      }
    });
  }

  deletePost(postId: number) {
    this.deletingPost.set(true);
    this.apiService.deletePost(postId).subscribe({
      next: () => {
        this.notificationService.success('Post deleted successfully');
        this.deletingPost.set(false);
        this.router.navigate(['/']);
      },
      error: () => {
        this.deletingPost.set(false);
        this.notificationService.error('Failed to delete post');
      }
    });
  }

  private refreshPost() {
    const post = this.post();
    if (post?.id) {
      this.apiService.getPostById(post.id).subscribe(p => {
        if (p.comments) {
          p.comments = this.sortComments(p.comments);
        }
        this.post.set(p);
      });
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
    const post = this.post();
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
      error: () => {
        this.reporting.set(false);
        this.notificationService.error('Failed to report post');
      }
    });
  }

  openEditPostModal() {
    this.showEditPostModal.set(true);
  }

  closeEditPostModal() {
    this.showEditPostModal.set(false);
  }

  onPostUpdated() {
    this.closeEditPostModal();
    this.refreshPost();
  }

  isPostAuthor(): boolean {
    const post = this.post();
    const user = this.currentUser();
    return post?.author === user?.username;
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

  getPostAuthorAvatar(): string {
    const post = this.post();
    return this.getAvatarUrl(post?.authorAvatar);
  }

  getCurrentUserAvatar(): string {
    const user = this.currentUser();
    return this.getAvatarUrl(user?.avatar);
  }

  getTimeAgo(date: string | Date): string {
    const now = new Date();
    const postDate = new Date(date);
    const diffMins = Math.floor((now.getTime() - postDate.getTime()) / 60000);
    if (diffMins < 1) return 'just now';
    if (diffMins < 60) return `${diffMins}m ago`;
    const diffHours = Math.floor(diffMins / 60);
    if (diffHours < 24) return `${diffHours}h ago`;
    const diffDays = Math.floor(diffHours / 24);
    if (diffDays < 7) return `${diffDays}d ago`;
    return postDate.toLocaleDateString();
  }

  viewProfile(username: string) {
    this.router.navigate(['/profile', username]);
  }

  editPost(postId: number | undefined, event: Event) {
    event.stopPropagation();
    this.router.navigate(['/edit-post', postId]);
  }

  viewPost(id: number | undefined) {
    if (id) {
      this.router.navigate(['/post', id]);
    }
  }

  getReadingTime(content: string): string {
    return calculateReadingTime(content);
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

  isCommentOwner(username: string): boolean {
    return this.currentUser()?.username === username;
  }

  isCommentUpdated(comment: any): boolean {
    return new Date(comment.updatedAt).getTime() > new Date(comment.createdAt).getTime();
  }

  private sortComments(comments: any[]) {
    console.log('Sorting comments:', comments);
    return [...comments].sort(
      (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
    );
  }

  isAdmin(): boolean {
    const user = this.currentUser();
    return user?.role?.includes('ADMIN') || false;
  }
}