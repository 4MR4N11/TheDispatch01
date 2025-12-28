import { Component, inject, signal, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { ApiService } from '../../../core/auth/api.service';
import { NotificationService } from '../../../core/services/notification.service';
import { ConfirmationModalService } from '../../../shared/services/confirmation-modal.service';
import { PostResponse } from '../../../shared/models/models';
import { ErrorHandler } from '../../../core/utils/error-handler';
import { formatDate, getContentPreview } from '../../../shared/utils/format.util';

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
  private readonly confirmationService = inject(ConfirmationModalService);
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
      error: (error) => {
        this.notificationService.error(ErrorHandler.getErrorMessage(error, 'Failed to load posts'));
        this.loading.set(false);
      }
    });
  }

  async deletePost(postId: number) {
    const confirmed = await this.confirmationService.open({
      title: 'Delete Post',
      message: 'Are you sure you want to delete this post?',
      confirmText: 'Delete',
      cancelText: 'Cancel'
    });

    if (confirmed) {
      this.apiService.deletePost(postId).subscribe({
        next: () => {
          this.notificationService.success('Post deleted successfully');
          this.loadPosts();
        },
        error: (error) => {
          this.notificationService.error(ErrorHandler.getErrorMessage(error, 'Failed to delete post'));
        }
      });
    }
  }

  async hidePost(postId: number) {
    const confirmed = await this.confirmationService.open({
      title: 'Hide Post',
      message: 'Are you sure you want to hide this post?',
      confirmText: 'Hide',
      cancelText: 'Cancel'
    });

    if (confirmed) {
      this.apiService.hidePost(postId).subscribe({
        next: () => {
          this.notificationService.success('Post hidden successfully');
          this.loadPosts();
        },
        error: (error) => {
          this.notificationService.error(ErrorHandler.getErrorMessage(error, 'Failed to hide post'));
        }
      });
    }
  }

  async unhidePost(postId: number) {
    const confirmed = await this.confirmationService.open({
      title: 'Unhide Post',
      message: 'Are you sure you want to unhide this post?',
      confirmText: 'Unhide',
      cancelText: 'Cancel'
    });

    if (confirmed) {
      this.apiService.unhidePost(postId).subscribe({
        next: () => {
          this.notificationService.success('Post unhidden successfully');
          this.loadPosts();
        },
        error: (error) => {
          this.notificationService.error(ErrorHandler.getErrorMessage(error, 'Failed to unhide post'));
        }
      });
    }
  }

  viewPost(postId: number | undefined) {
    if (postId) {
      this.router.navigate(['/post', postId]);
    }
  }

  formatDate = formatDate;
  getContentPreview = getContentPreview;

  getExcerpt(content: string): string {
    return getContentPreview(content, 200);
  }
}
