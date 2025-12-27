import { Component, EventEmitter, inject, Input, Output, signal, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/auth/api.service';
import { NotificationService } from '../../core/services/notification.service';
import { PostResponse } from '../../shared/models/models';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-edit-post-modal',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './edit-post-modal.component.html',
  styleUrl: './edit-post-modal.component.css',
})
export class EditPostModalComponent implements OnInit {
  @Input() post!: PostResponse;
  @Output() close = new EventEmitter<void>();
  @Output() postUpdated = new EventEmitter<void>();

  private readonly apiService = inject(ApiService);
  private readonly notificationService = inject(NotificationService);

  protected readonly title = signal('');
  protected readonly content = signal(''); // Plain text content
  protected readonly mediaUrl = signal('');
  protected readonly mediaType = signal('');
  protected readonly selectedFile = signal<File | null>(null);
  protected readonly filePreviewUrl = signal<string>('');
  protected readonly uploading = signal(false);
  protected readonly updating = signal(false);

  ngOnInit() {
    if (this.post) {
      this.title.set(this.post.title);
      // Content is now plain text, no need to parse JSON
      this.content.set(this.post.content || '');
      this.mediaUrl.set(this.post.media_url || '');
      this.mediaType.set(this.post.media_type || '');
    }
  }

  onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      const file = input.files[0];

      // Validate file type and size
      const isVideo = file.type.startsWith('video/');
      const isImage = file.type.startsWith('image/');
      const maxSize = isVideo ? 50 * 1024 * 1024 : 10 * 1024 * 1024; // 50MB for videos, 10MB for images

      if (!isImage && !isVideo) {
        this.notificationService.error('Only images and videos are allowed');
        return;
      }

      if (file.size > maxSize) {
        const maxSizeMB = isVideo ? '50MB' : '10MB';
        this.notificationService.error(`File size must be less than ${maxSizeMB}`);
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

      // Detect media type from file
      const mediaType = file.type.startsWith('image/') ? 'image' :
                       file.type.startsWith('video/') ? 'video' : '';
      this.mediaType.set(mediaType);
    }
  }

  clearFile() {
    const previewUrl = this.filePreviewUrl();
    if (previewUrl) {
      URL.revokeObjectURL(previewUrl);
    }
    this.filePreviewUrl.set('');
    this.selectedFile.set(null);

    // Restore original media if it existed
    if (this.post && this.post.media_url) {
      this.mediaUrl.set(this.post.media_url);
      this.mediaType.set(this.post.media_type || '');
    }
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
    if (!this.post || !this.post.id) return;

    this.apiService.updatePost(this.post.id, postData).subscribe({
      next: () => {
        const previewUrl = this.filePreviewUrl();
        if (previewUrl) {
          URL.revokeObjectURL(previewUrl);
        }
        this.updating.set(false);
        this.notificationService.success('Post updated successfully');
        this.postUpdated.emit();
        this.closeModal();
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
    return 'image';
  }

  closeModal() {
    const previewUrl = this.filePreviewUrl();
    if (previewUrl) {
      URL.revokeObjectURL(previewUrl);
    }
    this.close.emit();
  }

  stopPropagation(event: Event) {
    event.stopPropagation();
  }

  isVideoFile(): boolean {
    return this.selectedFile()?.type.startsWith('video/') || false;
  }

  isVideoUrl(): boolean {
    const url = this.mediaUrl();
    if (!url) return false;
    return url.toLowerCase().match(/\.(mp4|webm|ogg|mov)($|\?)/) !== null;
  }
}
