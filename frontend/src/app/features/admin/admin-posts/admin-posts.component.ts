import { Component, inject, signal, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { ApiService } from '../../../core/auth/api.service';
import { NotificationService } from '../../../core/services/notification.service';
import { PostResponse } from '../../../shared/models/models';

@Component({
  selector: 'app-admin-posts',
  standalone: true,
  imports: [],
  templateUrl: './admin-posts.component.html',
  styleUrl: './admin-posts.component.css'
})
export class AdminPostsComponent implements OnInit {
  private readonly apiService = inject(ApiService);
  private readonly notificationService = inject(NotificationService);
  private readonly router = inject(Router);

  protected readonly posts = signal<PostResponse[]>([]);
  protected readonly loading = signal(true);

  ngOnInit() {
    this.loadPosts();
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
    if (confirm('Are you sure you want to hide this post?')) {
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
  }

  unhidePost(postId: number) {
    if (confirm('Are you sure you want to unhide this post?')) {
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
  }

  viewPost(postId: number | undefined) {
    if (postId) {
      this.router.navigate(['/post', postId]);
    }
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

  getContentPreview(content: string, maxLength: number = 200): string {
    if (!content) return '';
    if (content.length <= maxLength) {
      return content;
    }
    return content.substring(0, maxLength) + '...';
  }

  getExcerpt(content: string): string {
    return this.getContentPreview(content, 200);
  }
}
