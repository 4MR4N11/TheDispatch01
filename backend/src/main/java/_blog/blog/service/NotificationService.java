package _blog.blog.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import _blog.blog.dto.NotificationDto;
import _blog.blog.entity.Comment;
import _blog.blog.entity.Post;
import _blog.blog.entity.User;
import _blog.blog.enums.NotificationType;

public interface NotificationService {
    void createNotification(User user, User actor, NotificationType type, String message, Post post, Comment comment);
    Page<NotificationDto> getUserNotifications(Long userId, Pageable pageable);
    List<NotificationDto> getAllUserNotifications(Long userId);
    Long getUnreadCount(Long userId);
    void markAsRead(Long notificationId, Long userId);
    void markAllAsRead(Long userId);
    void notifyNewFollower(User follower, User followed);
    void notifyNewPost(User author, Post post);
    void notifyPostLike(User liker, Post post);
    void notifyPostComment(User commenter, Post post, Comment comment);
    void notifyCommentReply(User replier, Comment parentComment, Comment reply);
    void deleteNotificationsByCommentId(Long commentId);
}
