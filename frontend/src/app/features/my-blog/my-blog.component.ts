import { Component, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { ApiService } from '../../core/auth/api.service';
import { AuthService } from '../../core/auth/auth.service';
import { NotificationService } from '../../core/services/notification.service';
import { PostResponse } from '../../shared/models/models';
import { calculateReadingTime } from '../../shared/utils/reading-time.util';
import { NewPostModalComponent } from '../../shared/components/new-post-modal.component';
import { EditPostModalComponent } from '../../shared/components/edit-post-modal.component';

@Component({
  selector: 'app-my-blog',
  standalone: true,
  imports: [CommonModule, NewPostModalComponent, EditPostModalComponent],
  templateUrl: './my-blog.component.html',
  styleUrl: './my-blog.component.css',
})
export class MyBlogComponent implements OnInit {
  private readonly apiService = inject(ApiService);
  private readonly authService = inject(AuthService);
  private readonly notificationService = inject(NotificationService);
  private readonly router = inject(Router);

  protected readonly posts = signal<PostResponse[]>([]);
  protected readonly loading = signal(true);
  protected readonly deleting = signal<number | null>(null);
  protected readonly showNewPostModal = signal(false);
  protected readonly showEditPostModal = signal(false);
  protected readonly selectedPost = signal<PostResponse | null>(null);

  protected readonly totalPosts = signal(0);
  protected readonly totalLikes = signal(0);
  protected readonly totalComments = signal(0);

  ngOnInit() {
    this.loadMyPosts();
  }

  private loadMyPosts() {
    const currentUser = this.authService.currentUser();
    if (!currentUser) {
      this.router.navigate(['/login']);
      return;
    }

    this.loading.set(true);
    this.apiService.getMyPosts().subscribe({
      next: (posts) => {
        this.posts.set(posts);
        this.calculateStats(posts);
        this.loading.set(false);
      },
      error: () => {
        this.notificationService.error('Failed to load posts');
        this.loading.set(false);
      },
    });
  }

  private calculateStats(posts: PostResponse[]) {
    const likes = posts.reduce((sum, p) => sum + (p.likeCount || 0), 0);
    const comments = posts.reduce((sum, p) => sum + (p.comments?.length || 0), 0);

    this.totalPosts.set(posts.length);
    this.totalLikes.set(likes);
    this.totalComments.set(comments);
  }

  getReadingTime(content: string): string {
    return `${calculateReadingTime(content)} min read`;
  }

  getContentPreview(content: string, maxLength: number = 120): string {
    if (!content) return '';
    // Plain text content - just truncate
    if (content.length <= maxLength) {
      return content;
    }
    return content.substring(0, maxLength) + '...';
  }

  viewPost(id: number | undefined) {
    if (id) {
      this.router.navigate(['/post', id]);
    }
  }

  editPost(id: number | undefined, event: Event) {
    event.stopPropagation();
    if (id) {
      const post = this.posts().find(p => p.id === id);
      if (post) {
        this.selectedPost.set(post);
        this.showEditPostModal.set(true);
      }
    }
  }

  deletePost(id: number | undefined, event: Event) {
    event.stopPropagation();
    if (!id) return;

    if (confirm('Are you sure you want to delete this post?')) {
      this.deleting.set(id);
      this.apiService.deletePost(id).subscribe({
        next: () => {
          this.notificationService.success('Post deleted successfully');
          this.deleting.set(null);
          this.loadMyPosts();
        },
        error: () => {
          this.notificationService.error('Failed to delete post');
          this.deleting.set(null);
        },
      });
    }
  }

  createNewPost() {
    this.showNewPostModal.set(true);
  }

  closeNewPostModal() {
    this.showNewPostModal.set(false);
  }

  onPostCreated() {
    this.closeNewPostModal();
    this.loadMyPosts();
  }

  closeEditPostModal() {
    this.showEditPostModal.set(false);
    this.selectedPost.set(null);
  }

  onPostUpdated() {
    this.closeEditPostModal();
    this.loadMyPosts();
  }
}
