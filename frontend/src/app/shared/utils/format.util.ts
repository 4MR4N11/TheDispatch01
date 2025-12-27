/**
 * Shared utility functions for formatting data
 */

import { environment } from '../../../environments/environment';

/**
 * Convert a date to a relative time string (e.g., "2h ago", "3d ago")
 * Uses short format for consistency across the app
 */
export function getTimeAgo(date: string | Date): string {
  const now = new Date();
  const past = new Date(date);
  const diffMs = now.getTime() - past.getTime();
  const diffMins = Math.floor(diffMs / 60000);

  if (diffMins < 1) return 'just now';
  if (diffMins < 60) return `${diffMins}m ago`;

  const diffHours = Math.floor(diffMins / 60);
  if (diffHours < 24) return `${diffHours}h ago`;

  const diffDays = Math.floor(diffHours / 24);
  if (diffDays < 7) return `${diffDays}d ago`;

  return past.toLocaleDateString();
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

  return {
    isValid: errors.length === 0,
    errors
  };
}

export function validateUsername(username: string): boolean {
  const errors: string[] = [];

  if (!username) {
    return false;
  }

  if (username.length < 4 || username.length > 20) {
    return false;
  }
  return true;
}
/**
 * Format a date as a localized string (e.g., "Dec 25, 2024, 3:30 PM")
 */
export function formatDate(date: string | Date): string {
  return new Date(date).toLocaleDateString('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  });
}

/**
 * Get a preview/excerpt from content text
 */
export function getContentPreview(content: string, maxLength: number = 200): string {
  if (!content) return '';
  if (content.length <= maxLength) return content;
  return content.substring(0, maxLength) + '...';
}
