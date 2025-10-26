// src/app/core/api/api.service.ts  // Assuming path
import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PostResponse, PostRequest, CommentResponse, CommentRequest, LikeResponse, UserResponse, ReportRequest, ReportResponse, AdminReportActionRequest, NotificationResponse } from '../../shared/models/models';

@Injectable({
  providedIn: 'root'
})
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.apiUrl;

  // Posts
  getAllPosts(): Observable<PostResponse[]> {
    return this.http.get<PostResponse[]>(`${this.baseUrl}/posts/all`);
  }

  getAllPostsForAdmin(): Observable<PostResponse[]> {
    return this.http.get<PostResponse[]>(`${this.baseUrl}/posts/admin/all`);
  }

  getFeed(): Observable<PostResponse[]> {
    return this.http.get<PostResponse[]>(`${this.baseUrl}/posts/feed`);
  }

  getMyPosts(): Observable<PostResponse[]> {
    return this.http.get<PostResponse[]>(`${this.baseUrl}/posts/my-posts`);
  }

  getPostsByUsername(username: string): Observable<PostResponse[]> {
    return this.http.get<PostResponse[]>(`${this.baseUrl}/posts/${username}`);
  }

  getPostById(id: number): Observable<PostResponse> {
    return this.http.get<PostResponse>(`${this.baseUrl}/posts/post/${id}`);
  }

  createPost(request: PostRequest): Observable<string> {
    return this.http.post(`${this.baseUrl}/posts/create`, request, { responseType: 'text' });
  }

  updatePost(id: number, request: PostRequest): Observable<string> {
    return this.http.put(`${this.baseUrl}/posts/${id}`, request, { responseType: 'text' });
  }

  deletePost(id: number) {
    return this.http.delete<{ message: string }>(`${this.baseUrl}/posts/${id}`);
  }

  hidePost(id: number) {
    return this.http.put<{ message: string }>(`${this.baseUrl}/posts/hide/${id}`, {});
  }

  unhidePost(id: number) {
    return this.http.put<{ message: string }>(`${this.baseUrl}/posts/unhide/${id}`, {});
  }

  // Comments
  createComment(postId: number, request: CommentRequest): Observable<string> {
    return this.http.post(`${this.baseUrl}/comments/create/${postId}`, request, { responseType: 'text' });
  }

  getCommentsByPost(postId: number): Observable<CommentResponse[]> {
    return this.http.get<CommentResponse[]>(`${this.baseUrl}/comments/post/${postId}`);
  }

  getAllComments(): Observable<CommentResponse[]> {
    return this.http.get<CommentResponse[]>(`${this.baseUrl}/comments`);
  }

  updateComment(commentId: number, request: CommentRequest): Observable<string> {
    return this.http.put(`${this.baseUrl}/comments/${commentId}`, request, { responseType: 'text' });
  }

  deleteComment(commentId: number): Observable<string> {
    return this.http.delete(`${this.baseUrl}/comments/${commentId}`, { responseType: 'text' });
  }

  // Likes
  likePost(postId: number): Observable<string> {
    return this.http.post(`${this.baseUrl}/likes/post/${postId}`, {}, { responseType: 'text' });
  }

  unlikePost(postId: number): Observable<string> {
    return this.http.delete(`${this.baseUrl}/likes/post/${postId}`, { responseType: 'text' });
  }

  getPostLikes(postId: number): Observable<LikeResponse> {
    return this.http.get<LikeResponse>(`${this.baseUrl}/likes/post/${postId}`);
  }

  checkIfPostLiked(postId: number): Observable<boolean> {
    return this.http.get<boolean>(`${this.baseUrl}/likes/post/${postId}/check`);
  }

  getMyLikedPosts(): Observable<number[]> {
    return this.http.get<number[]>(`${this.baseUrl}/likes/my-liked-posts`);
  }

  // Subscriptions
  subscribe(targetId: number): Observable<string> {
    return this.http.post(`${this.baseUrl}/subscriptions/subscribe/${targetId}`, {}, { responseType: 'text' });
  }

  unsubscribe(targetId: number): Observable<string> {
    return this.http.post(`${this.baseUrl}/subscriptions/unsubscribe/${targetId}`, {}, { responseType: 'text' });
  }

  getSubscriptions(): Observable<UserResponse[]> {
    return this.http.get<UserResponse[]>(`${this.baseUrl}/subscriptions/my-subscriptions`);
  }

  getFollowers(): Observable<UserResponse[]> {
    return this.http.get<UserResponse[]>(`${this.baseUrl}/subscriptions/my-followers`);
  }

  getFollowersByUsername(username: string): Observable<UserResponse[]> {
    return this.http.get<UserResponse[]>(`${this.baseUrl}/subscriptions/followers/${username}`);
  }

  getSubscriptionsByUsername(username: string): Observable<UserResponse[]> {
    return this.http.get<UserResponse[]>(`${this.baseUrl}/subscriptions/subscriptions/${username}`);
  }

  // Users
  getUsers(): Observable<UserResponse[]> {
    return this.http.get<UserResponse[]>(`${this.baseUrl}/users`);
  }

  getUser(id: number): Observable<UserResponse> {
    return this.http.get<UserResponse>(`${this.baseUrl}/users/${id}`);
  }

  getUserByUsername(username: string): Observable<UserResponse> {
    return this.http.get<UserResponse>(`${this.baseUrl}/users/username/${username}`);
  }

  getCurrentUser(): Observable<UserResponse> {
    return this.http.get<UserResponse>(`${this.baseUrl}/users/me`);
  }

  deleteUser(id: number) {
    return this.http.post<{ message: string }>(`${this.baseUrl}/users/delete/${id}`, {});
  }

  banUser(id: number) {
    return this.http.post<{ message: string }>(`${this.baseUrl}/users/ban/${id}`, {});
  }

  unbanUser(id: number) {
    return this.http.post<{ message: string }>(`${this.baseUrl}/users/unban/${id}`, {});
  }

  updateProfile(request: any): Observable<UserResponse> {
    return this.http.put<UserResponse>(`${this.baseUrl}/users/update-profile`, request);
  }

  // Reports
  reportUser(userId: number, request: ReportRequest): Observable<string> {
    return this.http.post(`${this.baseUrl}/reports/user/${userId}`, request, { responseType: 'text' });
  }

  reportPost(postId: number, request: ReportRequest): Observable<string> {
    return this.http.post(`${this.baseUrl}/reports/post/${postId}`, request, { responseType: 'text' });
  }

  getMyReports(): Observable<ReportResponse[]> {
    return this.http.get<ReportResponse[]>(`${this.baseUrl}/reports/my-reports`);
  }

  checkUserReport(userId: number): Observable<boolean> {
    return this.http.get<boolean>(`${this.baseUrl}/reports/check/user/${userId}`);
  }

  checkPostReport(postId: number): Observable<boolean> {
    return this.http.get<boolean>(`${this.baseUrl}/reports/check/post/${postId}`);
  }

  // Admin report endpoints (add more as needed)
  getAllReports(page: number = 0, size: number = 10): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/reports/admin/all?page=${page}&size=${size}`);
  }

  handleReport(reportId: number, request: AdminReportActionRequest): Observable<string> {
    return this.http.put(`${this.baseUrl}/reports/admin/handle/${reportId}`, request, { responseType: 'text' });
  }

  // Media upload
  uploadMedia(formData: FormData): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/media/upload`, formData);
  }

  uploadAvatar(formData: FormData): Observable<{ url: string; filename: string }> {
    return this.http.post<{ url: string; filename: string }>(`${this.baseUrl}/media/upload-avatar`, formData);
  }

  // Notifications
  getNotifications(page: number = 0, size: number = 20): Observable<NotificationResponse[]> {
    return this.http.get<NotificationResponse[]>(`${this.baseUrl}/notifications?page=${page}&size=${size}`);
  }

  getAllNotifications(): Observable<NotificationResponse[]> {
    return this.http.get<NotificationResponse[]>(`${this.baseUrl}/notifications/all`);
  }

  getUnreadNotificationsCount(): Observable<number> {
    return this.http.get<number>(`${this.baseUrl}/notifications/unread-count`);
  }

  markNotificationAsRead(id: number): Observable<void> {
    return this.http.put<void>(`${this.baseUrl}/notifications/${id}/read`, {});
  }

  markAllNotificationsAsRead(): Observable<void> {
    return this.http.put<void>(`${this.baseUrl}/notifications/mark-all-read`, {});
  }
}