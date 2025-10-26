import { environment } from '../../../environments/environment';

export class ErrorHandler {
  static getErrorMessage(error: any, defaultMessage: string = 'An error occurred. Please try again.'): string {
    // Only log errors in development mode
    if (!environment.production) {
      console.error('Error details:', error);
    }

    // Check for specific error message from backend
    if (error.error?.error) {
      return error.error.error;
    }

    if (error.error?.message) {
      return error.error.message;
    }

    if (error.message && error.message !== 'Http failure response for (unknown url): 0 Unknown Error') {
      return error.message;
    }

    // Handle HTTP status codes with context-aware messages
    switch (error.status) {
      case 0:
        return 'Unable to connect to server. Please check your internet connection.';
      case 400:
        return 'Invalid request. Please check your input and try again.';
      case 401:
        return 'Your session has expired. Please login again to continue.';
      case 403:
        return this.get403Message(error);
      case 404:
        return 'The requested resource was not found.';
      case 409:
        return 'This operation conflicts with existing data.';
      case 422:
        return 'Validation error. Please check your input.';
      case 500:
        return 'Server error. Please try again later.';
      case 502:
        return 'Bad gateway. The server is temporarily unavailable.';
      case 503:
        return 'Service unavailable. Please try again later.';
      default:
        return defaultMessage;
    }
  }

  private static get403Message(error: any): string {
    const url = error.url || '';

    // Context-specific 403 messages based on the URL
    if (url.includes('/upload') || url.includes('/media')) {
      return 'Access denied. Please login to upload files.';
    }

    if (url.includes('/admin')) {
      return 'Access denied. Administrator privileges required.';
    }

    if (url.includes('/users/update') || url.includes('/profile')) {
      return 'Access denied. You can only edit your own profile.';
    }

    if (url.includes('/post') && error.method === 'PUT') {
      return 'Access denied. You can only edit your own posts.';
    }

    if (url.includes('/post') && error.method === 'DELETE') {
      return 'Access denied. You can only delete your own posts.';
    }

    if (url.includes('/comment') && (error.method === 'PUT' || error.method === 'DELETE')) {
      return 'Access denied. You can only modify your own comments.';
    }

    if (url.includes('/ban') || url.includes('/unban') || url.includes('/report')) {
      return 'Access denied. Administrator privileges required for this action.';
    }

    // Default 403 message
    return 'Access denied. You do not have permission to perform this action.';
  }

  static getUploadErrorMessage(error: any): string {
    if (error.error?.error) {
      return error.error.error;
    }

    if (error.status === 403) {
      return 'Upload failed. Please login to upload files.';
    }

    if (error.status === 413) {
      return 'File is too large. Please choose a smaller file (max 5MB).';
    }

    if (error.status === 415) {
      return 'Invalid file type. Only images (JPG, PNG, GIF, WebP) are allowed.';
    }

    return this.getErrorMessage(error, 'Failed to upload file. Please try again.');
  }

  static getAuthErrorMessage(error: any): string {
    if (error.error?.error) {
      return error.error.error;
    }

    if (error.error?.message) {
      return error.error.message;
    }

    if (error.status === 401) {
      return 'Invalid credentials. Please check your username/email and password.';
    }

    if (error.status === 403) {
      return 'Access denied. Please check your credentials and try again.';
    }

    return this.getErrorMessage(error, 'Authentication failed. Please try again.');
  }

  static getRegistrationErrorMessage(error: any): string {
    if (error.error?.error) {
      return error.error.error;
    }

    if (error.error?.message) {
      return error.error.message;
    }

    if (error.status === 409) {
      return 'Username or email already exists. Please choose a different one.';
    }

    if (error.status === 400) {
      return 'Invalid registration data. Please check all fields and try again.';
    }

    if (error.status === 403) {
      return 'Registration is currently disabled. Please try again later.';
    }

    return this.getErrorMessage(error, 'Registration failed. Please try again.');
  }
}
