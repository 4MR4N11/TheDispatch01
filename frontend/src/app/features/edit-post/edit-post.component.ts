import { Component, inject, signal, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { ApiService } from '../../core/auth/api.service';
import { NotificationService } from '../../core/services/notification.service';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-edit-post',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './edit-post.component.html',
  styleUrl: './edit-post.component.css',
})
export class EditPostComponent implements OnInit {
  private readonly apiService = inject(ApiService);
  private readonly notificationService = inject(NotificationService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly postId = signal<number>(0);
  protected readonly title = signal('');
  protected readonly content = signal(''); // Plain text content
  protected readonly mediaUrl = signal('');
  protected readonly mediaType = signal('');
  protected readonly selectedFile = signal<File | null>(null);
  protected readonly filePreviewUrl = signal<string>('');
  protected readonly uploading = signal(false);
  protected readonly updating = signal(false);
  protected readonly loading = signal(true);

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.postId.set(+id);
      this.loadPost();
    } else {
      this.notificationService.error('Invalid post ID');
      this.router.navigate(['/my-blog']);
    }
  }

  private loadPost() {
    this.loading.set(true);
    this.apiService.getPostById(this.postId()).subscribe({
      next: (post) => {
        this.title.set(post.title);
        // Content is now plain text, no need to parse JSON
        this.content.set(post.content || '');
        this.mediaUrl.set(post.media_url || '');
        this.mediaType.set(post.media_type || '');
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.notificationService.error('Failed to load post');
        this.router.navigate(['/my-blog']);
      }
    });
  }

  onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      const file = input.files[0];

      this.mediaType.set(this.detectMediaType(file.name));
      // Validate file size (max 10MB)
      if (this.mediaType() == 'image' && file.size > 10 * 1024 * 1024) {
        this.notificationService.error('File size must be less than 10MB');
        return;
      } else if (this.mediaType() == 'video' && file.size > 50 * 1024 * 1024) {
        this.notificationService.error('Video size must be less than 50MB');
        return;
      }

      // Revoke old preview URL if it exists
      const oldPreview = this.filePreviewUrl();
      if (oldPreview) {
        URL.revokeObjectURL(oldPreview);
      }

      // Create preview URL for the new file
      const previewUrl = URL.createObjectURL(file);
      this.filePreviewUrl.set(previewUrl);
      this.selectedFile.set(file);
      this.mediaUrl.set(''); // Clear URL if file is selected
    }
  }

  clearFile() {
    const previewUrl = this.filePreviewUrl();
    if (previewUrl) {
      URL.revokeObjectURL(previewUrl);
    }
    this.filePreviewUrl.set('');
    this.selectedFile.set(null);
    this.mediaType.set('');
  }

  removeMedia() {
    const previewUrl = this.filePreviewUrl();
    if (previewUrl) {
      URL.revokeObjectURL(previewUrl);
    }
    this.mediaUrl.set('');
    this.mediaType.set('');
    this.filePreviewUrl.set('');
    this.selectedFile.set(null);
    this.mediaType.set('');
  }

  updatePost() {
    const title = this.title();
    const content = this.content();

    if (!title.trim()) {
      this.notificationService.warning('Title is required');
      return;
    }

    if (!content.trim()) {
      this.notificationService.warning('Content is required');
      return;
    }

    this.updating.set(true);

    if (this.selectedFile()) {
      this.uploadFileAndUpdatePost();
    } else {
      this.updatePostWithData({
        title,
        content, // Plain text content
        media_type: this.detectMediaType(this.mediaUrl()),
        media_url: this.mediaUrl()
      });
    }
  }

  private uploadFileAndUpdatePost() {
    const file = this.selectedFile();
    if (!file) return;

    this.uploading.set(true);
    const formData = new FormData();
    formData.append('file', file);

    this.apiService.uploadMedia(formData).subscribe({
      next: (response: any) => {
        this.uploading.set(false);
        const fileUrl = response.url || response.file_url;
        const fullUrl = fileUrl.startsWith('http') ? fileUrl : `${environment.apiUrl}${fileUrl}`;

        const mediaType = file.type.startsWith('image/') ? 'image' :
                         file.type.startsWith('video/') ? 'video' : '';

        this.updatePostWithData({
          title: this.title(),
          content: this.content(),
          media_type: mediaType,
          media_url: fullUrl
        });
      },
      error: () => {
        this.uploading.set(false);
        this.updating.set(false);
        this.notificationService.error('Failed to upload file');
      }
    });
  }

  private updatePostWithData(postData: any) {
    this.apiService.updatePost(this.postId(), postData).subscribe({
      next: () => {
        const previewUrl = this.filePreviewUrl();
        if (previewUrl) {
          URL.revokeObjectURL(previewUrl);
        }
        this.updating.set(false);
        this.notificationService.success('Post updated successfully');
        this.router.navigate(['/my-blog']);
      },
      error: () => {
        this.updating.set(false);
        this.notificationService.error('Failed to update post');
      }
    });
  }

  private detectMediaType(url: string): string {
    if (!url) return '';
    const urlLower = url.toLowerCase();
    if (urlLower.match(/\.(jpg|jpeg|png|gif|webp|svg)$/)) {
      return 'image';
    } else if (urlLower.match(/\.(mp4|webm|ogg|mov)$/)) {
      return 'video';
    }
    return '';
  }

  cancel() {
    const previewUrl = this.filePreviewUrl();
    if (previewUrl) {
      URL.revokeObjectURL(previewUrl);
    }
    this.router.navigate(['/my-blog']);
  }
}
