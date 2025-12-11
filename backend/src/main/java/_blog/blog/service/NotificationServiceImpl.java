package _blog.blog.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import _blog.blog.dto.NotificationDto;
import _blog.blog.entity.Comment;
import _blog.blog.entity.Notification;
import _blog.blog.entity.Post;
import _blog.blog.entity.User;
import _blog.blog.enums.NotificationType;
import _blog.blog.exception.ResourceNotFoundException;
import _blog.blog.repository.NotificationRepository;
import _blog.blog.repository.UserRepository;

@Service
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationServiceImpl(NotificationRepository notificationRepository, UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public void createNotification(User user, User actor, NotificationType type, String message, Post post, Comment comment) {
        // Don't create notification if user is the actor
        if (user.getId().equals(actor.getId())) {
            return;
        }

        Notification notification = Notification.builder()
                .user(user)
                .actor(actor)
                .type(type)
                .message(message)
                .post(post)
                .comment(comment)
                .read(false)
                .build();

        notificationRepository.save(notification);
    }

    @Override
    public Page<NotificationDto> getUserNotifications(Long userId, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        Page<Notification> notifications = notificationRepository.findByUserOrderByCreatedAtDesc(user, pageable);
        return notifications.map(this::toDto);
    }

    @Override
    public List<NotificationDto> getAllUserNotifications(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        List<Notification> notifications = notificationRepository.findByUserOrderByCreatedAtDesc(user);
        return notifications.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    public Long getUnreadCount(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        return notificationRepository.countUnreadByUser(user);
    }

    @Override
    @Transactional
    public void markAsRead(Long notificationId, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        notificationRepository.markAsReadById(notificationId, user);
    }

    @Override
    @Transactional
    public void markAllAsRead(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        notificationRepository.markAllAsReadByUser(user);
    }

    @Override
    public void notifyNewFollower(User follower, User followed) {
        String message = follower.getUsername() + " started following you";
        createNotification(followed, follower, NotificationType.NEW_FOLLOWER, message, null, null);
    }

    @Override
    public void notifyPostLike(User liker, Post post) {
        String message = liker.getUsername() + " liked your post: " + truncate(post.getTitle(), 50);
        createNotification(post.getAuthor(), liker, NotificationType.POST_LIKE, message, post, null);
    }

    @Override
    public void notifyPostComment(User commenter, Post post, Comment comment) {
        String message = commenter.getUsername() + " commented on your post: " + truncate(post.getTitle(), 50);
        createNotification(post.getAuthor(), commenter, NotificationType.POST_COMMENT, message, post, comment);
    }

    @Override
    public void notifyCommentReply(User replier, Comment parentComment, Comment reply) {
        String message = replier.getUsername() + " replied to your comment";
        createNotification(parentComment.getAuthor(), replier, NotificationType.COMMENT_REPLY, message,
                parentComment.getPost(), reply);
    }

    private NotificationDto toDto(Notification notification) {
        return NotificationDto.builder()
                .id(notification.getId())
                .actorUsername(notification.getActor() != null ? notification.getActor().getUsername() : null)
                .actorAvatar(notification.getActor() != null ? notification.getActor().getAvatar() : null)
                .type(notification.getType())
                .message(notification.getMessage())
                .postId(notification.getPost() != null ? notification.getPost().getId() : null)
                .commentId(notification.getComment() != null ? notification.getComment().getId() : null)
                .read(notification.isRead())
                .createdAt(notification.getCreatedAt())
                .build();
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }
}
