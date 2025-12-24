import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiService } from '../../core/auth/api.service';
import { NotificationService } from '../../core/services/notification.service';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-create-post',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './create-post.component.html',
  styleUrl: './create-post.component.css',
})
export class CreatePostComponent {
  private readonly apiService = inject(ApiService);
  private readonly notificationService = inject(NotificationService);
  private readonly router = inject(Router);

  protected readonly title = signal('');
  protected readonly content = signal(''); // Plain text content
  protected readonly mediaUrl = signal('');
  protected readonly selectedFile = signal<File | null>(null);
  protected readonly filePreviewUrl = signal<string>('');
  protected readonly uploading = signal(false);
  protected readonly creating = signal(false);
  protected readonly mediaType = signal('');

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
    this.filePreviewUrl.set('');
    this.selectedFile.set(null);
    this.mediaType.set('');
  }

  createPost() {
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

    this.creating.set(true);

    if (this.selectedFile()) {
      this.uploadFileAndCreatePost();
    } else {
      this.createPostWithData({
        title,
        content, // Plain text content
        media_type: this.detectMediaType(this.mediaUrl()),
        media_url: this.mediaUrl()
      });
    }
  }

  private uploadFileAndCreatePost() {
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

        this.createPostWithData({
          title: this.title(),
          content: this.content(),
          media_type: mediaType,
          media_url: fullUrl
        });
      },
      error: () => {
        this.uploading.set(false);
        this.creating.set(false);
        this.notificationService.error('Failed to upload file');
      }
    });
  }

  private createPostWithData(postData: any) {
    this.apiService.createPost(postData).subscribe({
      next: () => {
        const previewUrl = this.filePreviewUrl();
        if (previewUrl) {
          URL.revokeObjectURL(previewUrl);
        }
        this.creating.set(false);
        this.notificationService.success('Post created successfully');
        this.router.navigate(['/my-blog']);
      },
      error: () => {
        this.creating.set(false);
        this.notificationService.error('Failed to create post');
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
