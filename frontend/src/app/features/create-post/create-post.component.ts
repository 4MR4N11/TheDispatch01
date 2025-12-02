import { Component, ElementRef, ViewChild, OnInit, OnDestroy, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import EditorJS from '@editorjs/editorjs';
import Header from '@editorjs/header';
import List from '@editorjs/list';
import ImageTool from '@editorjs/image';
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
export class CreatePostComponent implements OnInit, OnDestroy {

  @ViewChild('editorHolder', { static: true }) editorHolder!: ElementRef;
  private editor!: EditorJS;

  private readonly apiService = inject(ApiService);
  private readonly notificationService = inject(NotificationService);
  private readonly router = inject(Router);

  protected readonly title = signal('');
  protected readonly content = signal<any>(null); // store EditorJS JSON
  protected readonly mediaUrl = signal('');
  protected readonly selectedFile = signal<File | null>(null);
  protected readonly filePreviewUrl = signal<string>('');
  protected readonly uploading = signal(false);
  protected readonly creating = signal(false);

  ngOnInit(): void {
    this.editor = new EditorJS({
      holder: this.editorHolder.nativeElement,
      placeholder: 'Write your post content here...',
      autofocus: true,
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
      }
    });
  }

  ngOnDestroy(): void {
    this.editor?.destroy();
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
  }

  removeMedia() {
    const previewUrl = this.filePreviewUrl();
    if (previewUrl) {
      URL.revokeObjectURL(previewUrl);
    }
    this.mediaUrl.set('');
    this.filePreviewUrl.set('');
    this.selectedFile.set(null);
  }

  createPost() {
    const title = this.title();
    const content = this.content();

    if (!title.trim() || !content || !content.blocks || content.blocks.length === 0) {
      this.notificationService.warning('Title and content are required');
      return;
    }

    this.creating.set(true);

    if (this.selectedFile()) {
      this.uploadFileAndCreatePost();
    } else {
      this.createPostWithData({
        title,
        content: JSON.stringify(content), // serialize Editor.js data
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
          content: JSON.stringify(this.content()),
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
    return 'image';
  }

  cancel() {
    const previewUrl = this.filePreviewUrl();
    if (previewUrl) {
      URL.revokeObjectURL(previewUrl);
    }
    this.router.navigate(['/my-blog']);
  }
}
