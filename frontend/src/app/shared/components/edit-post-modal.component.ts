import { Component, ElementRef, ViewChild, EventEmitter, inject, Input, Output, signal, OnInit, OnDestroy, AfterViewInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import EditorJS from '@editorjs/editorjs';
import Header from '@editorjs/header';
import List from '@editorjs/list';
import ImageTool from '@editorjs/image';
import { ApiService } from '../../core/auth/api.service';
import { NotificationService } from '../../core/services/notification.service';
import { PostResponse } from '../../shared/models/models';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-edit-post-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './edit-post-modal.component.html',
  styleUrl: './edit-post-modal.component.css',
})
export class EditPostModalComponent implements OnInit, AfterViewInit, OnDestroy {
  @Input() post!: PostResponse;
  @Output() close = new EventEmitter<void>();
  @Output() postUpdated = new EventEmitter<void>();
  @ViewChild('editorHolder', { static: false }) editorHolder!: ElementRef;

  private editor!: EditorJS;
  private readonly apiService = inject(ApiService);
  private readonly notificationService = inject(NotificationService);

  protected readonly title = signal('');
  protected readonly content = signal<any>(null);
  protected readonly mediaUrl = signal('');
  protected readonly mediaType = signal('');
  protected readonly selectedFile = signal<File | null>(null);
  protected readonly filePreviewUrl = signal<string>('');
  protected readonly uploading = signal(false);
  protected readonly updating = signal(false);
  protected readonly editorReady = signal(false);

  ngOnInit() {
    if (this.post) {
      this.title.set(this.post.title);
      this.mediaUrl.set(this.post.media_url || '');
      this.mediaType.set(this.post.media_type || '');

      // Parse content from JSON string to Editor.js data
      let contentData;
      try {
        contentData = typeof this.post.content === 'string'
          ? JSON.parse(this.post.content)
          : this.post.content;
      } catch (e) {
        console.error('Error parsing content:', e);
        contentData = { blocks: [] };
      }
      this.content.set(contentData);
    }
  }

  ngAfterViewInit() {
    // Initialize editor after view is ready
    setTimeout(() => this.initializeEditor(), 100);
  }

  ngOnDestroy(): void {
    this.editor?.destroy();
  }

  private initializeEditor() {
    if (!this.editorHolder) return;

    this.editor = new EditorJS({
      holder: this.editorHolder.nativeElement,
      placeholder: 'Write your post content here...',
      data: this.content() || { blocks: [] },
      tools: {
        header: Header,
        list: List,
        image: {
          class: ImageTool,
          config: {
            uploader: {
              uploadByFile: (file: File) => {
                return this.uploadEditorImage(file);
              }
            }
          }
        }
      },
      onChange: async () => {
        const output = await this.editor.save();
        this.content.set(output);
      },
      onReady: () => {
        this.editorReady.set(true);
      }
    });
  }

  private uploadEditorImage(file: File): Promise<any> {
    return new Promise((resolve, reject) => {
      const formData = new FormData();
      formData.append('image', file);

      // Use cookies for authentication (withCredentials)
      fetch(`${environment.apiUrl}/uploads/image`, {
        method: 'POST',
        credentials: 'include',  // ✅ Send HttpOnly cookies
        body: formData
      })
      .then(response => response.json())
      .then(data => {
        if (data.success === 1) {
          resolve(data);
        } else {
          reject(data.error || 'Upload failed');
          this.notificationService.error(data.error || 'Failed to upload image');
        }
      })
      .catch(error => {
        reject(error);
        this.notificationService.error('Failed to upload image');
      });
    });
  }

  private uploadEditorVideo(file: File): Promise<any> {
    return new Promise((resolve, reject) => {
      // Validate file type
      const validTypes = ['video/mp4', 'video/webm', 'video/ogg'];
      if (!validTypes.includes(file.type)) {
        reject('Invalid file type. Please upload MP4, WebM, or OGG');
        this.notificationService.error('Invalid file type. Please upload MP4, WebM, or OGG');
        return;
      }

      // Validate file size (max 100MB)
      const maxSize = 100 * 1024 * 1024;
      if (file.size > maxSize) {
        reject('File size must be less than 100MB');
        this.notificationService.error('File size must be less than 100MB');
        return;
      }

      const formData = new FormData();
      formData.append('file', file);

      // Use cookies for authentication (withCredentials)
      fetch(`${environment.apiUrl}/uploads/video`, {
        method: 'POST',
        credentials: 'include',  // ✅ Send HttpOnly cookies
        body: formData
      })
      .then(response => response.json())
      .then(data => {
        if (data.success === 1 && data.file && data.file.url) {
          const videoUrl = data.file.url;
          const fullUrl = videoUrl.startsWith('http') ? videoUrl : `${environment.apiUrl}${videoUrl}`;
          resolve({
            success: 1,
            file: {
              url: fullUrl
            }
          });
        } else {
          reject(data.error || 'Upload failed');
          this.notificationService.error(data.error || 'Failed to upload video');
        }
      })
      .catch(error => {
        reject(error);
        this.notificationService.error('Failed to upload video');
      });
    });
  }

  onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      const file = input.files[0];

      // Validate file size (max 10MB)
      if (file.size > 10 * 1024 * 1024) {
        this.notificationService.error('File size must be less than 10MB');
        return;
      }

      // Revoke old preview URL if it exists
      const oldPreview = this.filePreviewUrl();
      if (oldPreview) {
        URL.revokeObjectURL(oldPreview);
      }

      // Detect media type from file
      const mediaType = file.type.startsWith('image/') ? 'image' :
                       file.type.startsWith('video/') ? 'video' : '';
      this.mediaType.set(mediaType);

      // Create preview URL for the new file
      const previewUrl = URL.createObjectURL(file);
      this.filePreviewUrl.set(previewUrl);
      this.selectedFile.set(file);
      this.mediaUrl.set(''); // Clear URL if file is selected
    }
  }

  clearFile() {
    // Revoke preview URL
    const previewUrl = this.filePreviewUrl();
    if (previewUrl) {
      URL.revokeObjectURL(previewUrl);
    }
    this.filePreviewUrl.set('');
    this.selectedFile.set(null);
  }

  removeMedia() {
    // Revoke preview URL if it exists
    const previewUrl = this.filePreviewUrl();
    if (previewUrl) {
      URL.revokeObjectURL(previewUrl);
    }
    this.mediaUrl.set('');
    this.mediaType.set('');
    this.filePreviewUrl.set('');
    this.selectedFile.set(null);
  }

  async updatePost() {
    const title = this.title();
    const content = this.content();

    if (!title.trim() || !content || !content.blocks || content.blocks.length === 0) {
      this.notificationService.warning('Title and content are required');
      return;
    }

    // Save current editor state
    if (this.editor) {
      try {
        const savedContent = await this.editor.save();
        this.content.set(savedContent);
      } catch (e) {
        console.error('Error saving editor content:', e);
      }
    }

    this.updating.set(true);

    // If file is selected, upload it first
    if (this.selectedFile()) {
      this.uploadFileAndUpdatePost();
    } else {
      // Otherwise update post with URL or no media
      let mediaType = this.mediaType();
      const mediaUrl = this.mediaUrl();

      if (mediaUrl && !mediaType) {
        // Try to detect from URL if type is not set
        const urlLower = mediaUrl.toLowerCase();
        if (urlLower.match(/\.(jpg|jpeg|png|gif|webp|svg)$/)) {
          mediaType = 'image';
        } else if (urlLower.match(/\.(mp4|webm|ogg|mov)$/)) {
          mediaType = 'video';
        } else {
          // Default to image if we can't detect
          mediaType = 'image';
        }
      } else if (!mediaUrl) {
        // No media, clear type
        mediaType = '';
      }

      this.updatePostWithData({
        title,
        content: JSON.stringify(this.content()),
        media_type: mediaType,
        media_url: mediaUrl
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

        this.updatePostWithData({
          title: this.title(),
          content: JSON.stringify(this.content()),
          media_type: this.mediaType(),
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
    if (!this.post?.id) return;

    this.apiService.updatePost(this.post.id, postData).subscribe({
      next: () => {
        // Clean up preview URL
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

  closeModal() {
    // Clean up preview URL
    const previewUrl = this.filePreviewUrl();
    if (previewUrl) {
      URL.revokeObjectURL(previewUrl);
    }
    this.close.emit();
  }

  stopPropagation(event: Event) {
    event.stopPropagation();
  }
}
