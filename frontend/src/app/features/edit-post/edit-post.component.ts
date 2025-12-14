import { Component, ElementRef, ViewChild, inject, signal, OnInit, OnDestroy } from '@angular/core';

import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import EditorJS from '@editorjs/editorjs';
import Header from '@editorjs/header';
import List from '@editorjs/list';
import ImageTool from '@editorjs/image';
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
export class EditPostComponent implements OnInit, OnDestroy {
  @ViewChild('editorHolder', { static: false }) editorHolder!: ElementRef;
  private editor!: EditorJS;

  private readonly apiService = inject(ApiService);
  private readonly notificationService = inject(NotificationService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly postId = signal<number>(0);
  protected readonly title = signal('');
  protected readonly content = signal<any>(null);
  protected readonly mediaUrl = signal('');
  protected readonly mediaType = signal('');
  protected readonly selectedFile = signal<File | null>(null);
  protected readonly filePreviewUrl = signal<string>('');
  protected readonly uploading = signal(false);
  protected readonly updating = signal(false);
  protected readonly loading = signal(true);

  // Track uploaded file URLs for cleanup
  private uploadedFileUrls = new Set<string>();
  private previousBlockUrls = new Set<string>();
  private initialBlockUrls = new Set<string>(); // URLs from loaded content (don't delete these)

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.postId.set(+id);
      this.loadPost();
    } else {
      this.router.navigate(['/my-blog']);
    }
  }

  ngOnDestroy(): void {
    this.editor?.destroy();
  }

  private loadPost() {
    this.apiService.getPostById(this.postId()).subscribe({
      next: (post) => {
        this.title.set(post.title);
        this.mediaUrl.set(post.media_url || '');
        this.mediaType.set(post.media_type || '');

        // Parse content from JSON string to Editor.js data
        let contentData;
        console.log('Loaded post content:', post.content);
        try {
          contentData = typeof post.content === 'string'
            ? JSON.parse(post.content)
            : post.content;
        } catch (e) {
          console.error('Error parsing content:', e);
          contentData = { blocks: [] };
        }

        this.content.set(contentData);
        this.loading.set(false);

        // Track initial URLs from loaded content (don't delete these)
        this.initialBlockUrls = this.extractBlockUrls(contentData);
        this.previousBlockUrls = new Set(this.initialBlockUrls);

        // Initialize editor after content is loaded
        setTimeout(() => this.initializeEditor(), 100);
      },
      error: () => {
        this.notificationService.error('Failed to load post');
        this.router.navigate(['/my-blog']);
      }
    });
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
              },
              uploadByUrl: (url: string) => {
                return Promise.resolve({
                  success: 1,
                  file: { url }
                });
              }
            }
          }
        },
        video: {
          class: this.createVideoTool(),
          config: {
            uploader: {
              uploadByFile: (file: File) => {
                return this.uploadEditorVideo(file);
              }
            }
          }
        }
      },
      onChange: async () => {
        const output = await this.editor.save();
        this.content.set(output);

        // Check for removed blocks and delete their files (only newly uploaded ones)
        this.cleanupRemovedBlockFiles(output);
      }
    });
  }

  private createVideoTool() {
    const self = this;

    return class VideoTool {
      private data: any;
      private wrapper: HTMLElement | null = null;

      static get toolbox() {
        return {
          title: 'Video',
          icon: '<svg width="17" height="15" viewBox="0 0 24 24" fill="currentColor"><path d="M17 10.5V7c0-.55-.45-1-1-1H4c-.55 0-1 .45-1 1v10c0 .55.45 1 1 1h12c.55 0 1-.45 1-1v-3.5l4 4v-11l-4 4z"/></svg>'
        };
      }

      constructor({ data }: { data: any }) {
        this.data = data || {};
      }

      render() {
        this.wrapper = document.createElement('div');
        this.wrapper.classList.add('video-tool');

        if (this.data && this.data.file && this.data.file.url) {
          this.showVideo(this.data.file.url);
        } else {
          this.showUploader();
        }

        return this.wrapper;
      }

      private showUploader() {
        const uploadWrapper = document.createElement('div');
        uploadWrapper.style.cssText = 'border: 2px dashed #ccc; padding: 20px; text-align: center; cursor: pointer; border-radius: 8px; background: #f9f9f9;';
        uploadWrapper.innerHTML = `
          <svg width="48" height="48" viewBox="0 0 24 24" fill="#666" style="margin-bottom: 10px;">
            <path d="M17 10.5V7c0-.55-.45-1-1-1H4c-.55 0-1 .45-1 1v10c0 .55.45 1 1 1h12c.55 0 1-.45 1-1v-3.5l4 4v-11l-4 4z"/>
          </svg>
          <p style="margin: 0; color: #666; font-size: 14px;">Click to upload video</p>
          <p style="margin: 5px 0 0; color: #999; font-size: 12px;">MP4, WebM, OGG (max 100MB)</p>
        `;

        const fileInput = document.createElement('input');
        fileInput.type = 'file';
        fileInput.accept = 'video/mp4,video/webm,video/ogg';
        fileInput.style.display = 'none';

        fileInput.addEventListener('change', async (e: Event) => {
          const target = e.target as HTMLInputElement;
          if (target.files && target.files[0]) {
            uploadWrapper.innerHTML = '<p style="color: #666;">Uploading video...</p>';
            try {
              const response = await self.uploadEditorVideo(target.files[0]);
              if (response.success === 1 && response.file.url) {
                this.data = { file: { url: response.file.url } };
                this.showVideo(response.file.url);
              }
            } catch (error) {
              uploadWrapper.innerHTML = '<p style="color: red;">Upload failed. Click to try again.</p>';
            }
          }
        });

        uploadWrapper.addEventListener('click', () => fileInput.click());
        uploadWrapper.appendChild(fileInput);
        this.wrapper?.appendChild(uploadWrapper);
      }

      private showVideo(url: string) {
        if (!this.wrapper) return;
        this.wrapper.innerHTML = '';
        const video = document.createElement('video');
        video.src = url;
        video.controls = true;
        video.style.cssText = 'max-width: 100%; border-radius: 8px;';
        this.wrapper.appendChild(video);
      }

      save() {
        return this.data;
      }

      validate(savedData: any) {
        return savedData.file && savedData.file.url;
      }
    };
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
          // Track uploaded URL for cleanup
          const url = data.file?.url || data.url;
          if (url) {
            this.uploadedFileUrls.add(url);
          }
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
          // Track uploaded URL for cleanup
          this.uploadedFileUrls.add(fullUrl);
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

  // Extract URLs from EditorJS content blocks
  private extractBlockUrls(content: any): Set<string> {
    const urls = new Set<string>();
    if (content && content.blocks) {
      for (const block of content.blocks) {
        if (block.type === 'image' && block.data?.file?.url) {
          urls.add(block.data.file.url);
        } else if (block.type === 'video' && block.data?.file?.url) {
          urls.add(block.data.file.url);
        }
      }
    }
    return urls;
  }

  // Check for removed blocks and delete their files from server
  private cleanupRemovedBlockFiles(currentContent: any) {
    const currentUrls = this.extractBlockUrls(currentContent);

    // Find URLs that were in previous content but not in current
    for (const url of this.previousBlockUrls) {
      if (!currentUrls.has(url)) {
        // Delete if it's a local upload (newly uploaded or from initial content)
        if (this.uploadedFileUrls.has(url) || this.initialBlockUrls.has(url)) {
          this.deleteUploadedFile(url);
          this.uploadedFileUrls.delete(url);
          this.initialBlockUrls.delete(url);
        }
      }
    }

    // Update previous URLs for next comparison
    this.previousBlockUrls = currentUrls;
  }

  // Delete file from server
  private deleteUploadedFile(url: string) {
    fetch(`${environment.apiUrl}/uploads/delete`, {
      method: 'DELETE',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ url })
    })
    .then(response => response.json())
    .catch(error => {
      console.error('Failed to delete file:', error);
    });
  }

  onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      const file = input.files[0];

      if (file.size > 10 * 1024 * 1024) {
        this.notificationService.error('File size must be less than 10MB');
        return;
      }

      const oldPreview = this.filePreviewUrl();
      if (oldPreview) {
        URL.revokeObjectURL(oldPreview);
      }

      const mediaType = file.type.startsWith('image/') ? 'image' :
        file.type.startsWith('video/') ? 'video' : '';
      this.mediaType.set(mediaType);

      const previewUrl = URL.createObjectURL(file);
      this.filePreviewUrl.set(previewUrl);
      this.selectedFile.set(file);
      this.mediaUrl.set('');
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

    if (this.selectedFile()) {
      this.uploadFileAndUpdatePost();
    } else {
      let mediaType = this.mediaType();
      const mediaUrl = this.mediaUrl();

      if (mediaUrl && !mediaType) {
        const urlLower = mediaUrl.toLowerCase();
        if (urlLower.match(/\.(jpg|jpeg|png|gif|webp|svg)$/)) {
          mediaType = 'image';
        } else if (urlLower.match(/\.(mp4|webm|ogg|mov)$/)) {
          mediaType = 'video';
        } else {
          mediaType = 'image';
        }
      } else if (!mediaUrl) {
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

  cancel() {
    const previewUrl = this.filePreviewUrl();
    if (previewUrl) {
      URL.revokeObjectURL(previewUrl);
    }
    this.router.navigate(['/my-blog']);
  }
}
