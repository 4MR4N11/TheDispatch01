package _blog.blog.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import _blog.blog.entity.Notification;
import _blog.blog.entity.Post;
import _blog.blog.entity.User;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    List<Notification> findByUserOrderByCreatedAtDesc(User user);

    @Query("SELECT COUNT(n) FROM Notification n WHERE n.user = :user AND n.read = false")
    Long countUnreadByUser(@Param("user") User user);

    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.user = :user AND n.read = false")
    void markAllAsReadByUser(@Param("user") User user);

    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.id = :id AND n.user = :user")
    void markAsReadById(@Param("id") Long id, @Param("user") User user);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.post = :post")
    void deleteByPost(@Param("post") Post post);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.user = :user OR n.actor = :user")
    void deleteByUser(@Param("user") User user);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.comment.id = :commentId")
    void deleteByCommentId(@Param("commentId") Long commentId);
}
