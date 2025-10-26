/**
 * Calculate reading time based on content
 * Average reading speed: 200-250 words per minute (using 225)
 */
export function calculateReadingTime(content: string): string {
  const wordsPerMinute = 225;
  const words = content.trim().split(/\s+/).length;
  const minutes = Math.ceil(words / wordsPerMinute);

  if (minutes === 0) return '< 1 min read';
  if (minutes === 1) return '1 min read';
  return `${minutes} min read`;
}