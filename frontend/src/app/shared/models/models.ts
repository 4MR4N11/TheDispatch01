// src/app/shared/models.ts
export interface UserResponse {
  id: number,
  firstname: string;
  lastname: string;
  username: string;
  email: string;
  avatar: string;
  role: string;
  banned: boolean;
  subscriptions: string[];
  posts: PostResponse[]; // Simplified, or PostResponse[] if circular ok
}

export interface PostResponse {
  id: number,
  author: string;
  authorAvatar: string;
  title: string;
  content: string;
  media_type: string;
  media_url: string;
  hidden: boolean;
  comments: CommentResponse[];
  created_at: string | Date;
  updated_at: string | Date;
  likeCount: number;
  likedByUsernames: string[];
}

export interface CommentResponse {
  id: number;
  authorUsername: string;
  authorAvatar: string;
  content: string;
  createdAt: string | Date;
}

export interface LikeResponse {
  postId: number;
  likeCount: number;
  likedByUsernames: string[];
}

export interface ReportRequest {
  reason: string;
}

export interface ReportResponse {
  id: number;
  reporterUsername: string;
  type: string;
  reason: string;
  status: string;
  reportedUsername?: string;
  reportedPostTitle?: string;
  reportedPostId?: number;
  handledByAdminUsername?: string;
  adminResponse?: string;
  createdAt: string | Date;
  updatedAt: string | Date;
  handledAt?: string | Date;
}

export interface PostReportResponse {
  id: number;
  reporterUsername: string;
  reportedPostTitle: string;
  reportedPostId: number;
  reportedPostAuthor: string;
  reason: string;
  status: string;
  handledByAdminUsername?: string;
  adminResponse?: string;
  createdAt: string | Date;
  updatedAt: string | Date;
  handledAt?: string | Date;
}

export type ReportStatus = "APPROVED" | "REJECTED";

export interface AdminReportActionRequest {
  action: ReportStatus;
  adminResponse?: string;
}

export interface PostRequest {
  title: string;
  content: string;
  media_type?: string;
  media_url?: string;
}

export interface CommentRequest {
  content: string;
}

export interface AuthResponse {
  token: string;
  usernameOrEmail: string;
  role: string;
}

export interface LoginRequest {
  usernameOrEmail: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
  firstname: string;
  lastname: string;
  avatar?: string;
}

export interface NotificationResponse {
  id: number;
  actorUsername: string;
  actorAvatar: string;
  type: 'NEW_FOLLOWER' | 'POST_LIKE' | 'POST_COMMENT' | 'COMMENT_REPLY' | 'POST_MENTIONED';
  message: string;
  postId?: number;
  commentId?: number;
  read: boolean;
  createdAt: string | Date;
}