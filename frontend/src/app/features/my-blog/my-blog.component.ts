import edjsHTML from 'editorjs-html';
import { Component, inject, signal, OnInit } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
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
  private readonly sanitizer = inject(DomSanitizer);

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

  getContentPreview(content: string, maxLength: number = 120): string {
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

  viewPost(id: number | undefined) {
    if (id) {
      this.router.navigate(['/post', id]);
    }
  }

  editPost(id: number | undefined, event: Event) {
    event.stopPropagation();
    if (id) {
      this.router.navigate(['/edit-post', id]);
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
    this.router.navigate(['/create-post']);
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
