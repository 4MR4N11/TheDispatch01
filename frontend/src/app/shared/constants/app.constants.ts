/**
 * Application-wide constants
 */

/**
 * File size limits (in bytes)
 */
export const FILE_SIZE_LIMITS = {
  POST_MEDIA: 10 * 1024 * 1024,  // 10MB
  AVATAR: 5 * 1024 * 1024,        // 5MB
} as const;

/**
 * Polling intervals (in milliseconds)
 */
export const POLLING_INTERVALS = {
  NOTIFICATIONS: 30000,  // 30 seconds
} as const;

/**
 * Pagination settings
 */
export const PAGINATION = {
  DEFAULT_PAGE_SIZE: 10,
  ADMIN_PAGE_SIZE: 20,
  REPORTS_PAGE_SIZE: 10,
} as const;

/**
 * Reading time calculation
 */
export const READING_TIME = {
  WORDS_PER_MINUTE: 225,
} as const;

/**
 * Content limits
 */
export const CONTENT_LIMITS = {
  EXCERPT_LENGTH: 150,
  CONTENT_PREVIEW_LENGTH: 200,
  COMMENT_MAX_LENGTH: 500,
  REPORT_MESSAGE_MAX_LENGTH: 500,
  POST_TITLE_MAX_LENGTH: 200,
} as const;

/**
 * Report categories
 */
export const REPORT_CATEGORIES = [
  'Spam',
  'Harassment',
  'Hate Speech',
  'Violence',
  'Misinformation',
  'Copyright Violation',
  'Other'
] as const;

export type ReportCategory = typeof REPORT_CATEGORIES[number];
