/**
 * Shared utility functions for formatting data
 */

import { environment } from '../../../environments/environment';

/**
 * Convert a date to a relative time string (e.g., "2 hours ago")
 */
export function getTimeAgo(date: string | Date): string {
  const now = new Date();
  const past = new Date(date);
  const diffInSeconds = Math.floor((now.getTime() - past.getTime()) / 1000);

  if (diffInSeconds < 60) {
    return 'just now';
  }

  const diffInMinutes = Math.floor(diffInSeconds / 60);
  if (diffInMinutes < 60) {
    return `${diffInMinutes} minute${diffInMinutes > 1 ? 's' : ''} ago`;
  }

  const diffInHours = Math.floor(diffInMinutes / 60);
  if (diffInHours < 24) {
    return `${diffInHours} hour${diffInHours > 1 ? 's' : ''} ago`;
  }

  const diffInDays = Math.floor(diffInHours / 24);
  if (diffInDays < 7) {
    return `${diffInDays} day${diffInDays > 1 ? 's' : ''} ago`;
  }

  const diffInWeeks = Math.floor(diffInDays / 7);
  if (diffInWeeks < 4) {
    return `${diffInWeeks} week${diffInWeeks > 1 ? 's' : ''} ago`;
  }

  const diffInMonths = Math.floor(diffInDays / 30);
  if (diffInMonths < 12) {
    return `${diffInMonths} month${diffInMonths > 1 ? 's' : ''} ago`;
  }

  const diffInYears = Math.floor(diffInDays / 365);
  return `${diffInYears} year${diffInYears > 1 ? 's' : ''} ago`;
}

/**
 * Get full avatar URL from relative path
 */
export function getAvatarUrl(avatar: string | null | undefined): string {
  if (!avatar) {
    return '';
  }

  // If it's already a full URL, return as is
  if (avatar.startsWith('http://') || avatar.startsWith('https://')) {
    return avatar;
  }

  // If it starts with /uploads/, prepend API URL
  if (avatar.startsWith('/uploads/')) {
    return `${environment.apiUrl}${avatar}`;
  }

  // Otherwise, assume it needs /uploads/ prefix
  return `${environment.apiUrl}/uploads/${avatar}`;
}

/**
 * Strip HTML tags from content and create an excerpt
 * Uses DOMParser for safe HTML parsing without XSS risks
 */
export function getExcerpt(content: string, maxLength: number = 150): string {
  if (!content) {
    return '';
  }

  // ✅ SECURITY FIX: Use DOMParser instead of innerHTML to prevent XSS
  const parser = new DOMParser();
  const doc = parser.parseFromString(content, 'text/html');
  const stripped = doc.body.textContent || '';

  // Truncate to maxLength
  if (stripped.length <= maxLength) {
    return stripped;
  }

  return stripped.substring(0, maxLength).trim() + '...';
}

/**
 * Get content preview without HTML tags
 */
export function getContentPreview(content: string, maxLength: number = 200): string {
  return getExcerpt(content, maxLength);
}

/**
 * Get author initials from username
 */
export function getAuthorInitial(author: string): string {
  if (!author || author.length === 0) {
    return '?';
  }
  return author.charAt(0).toUpperCase();
}

/**
 * Validate email format
 */
export function isValidEmail(email: string): boolean {
  if (!email) {
    return false;
  }

  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  return emailRegex.test(email);
}

/**
 * Validate password strength
 */
export interface PasswordValidationResult {
  isValid: boolean;
  errors: string[];
}

export function validatePassword(password: string): PasswordValidationResult {
  const errors: string[] = [];

  if (!password) {
    return { isValid: false, errors: ['Password is required'] };
  }

  if (password.length < 8) {
    errors.push('Password must be at least 8 characters');
  }

  if (!/[A-Z]/.test(password)) {
    errors.push('Password must contain at least one uppercase letter');
  }

  if (!/[a-z]/.test(password)) {
    errors.push('Password must contain at least one lowercase letter');
  }

  if (!/[0-9]/.test(password)) {
    errors.push('Password must contain at least one number');
  }

  if (!/[!@#$%^&*(),.?":{}|<>]/.test(password)) {
    errors.push('Password must contain at least one special character (!@#$%^&*(),.?":{}|<>)');
  }

  return {
    isValid: errors.length === 0,
    errors
  };
}
