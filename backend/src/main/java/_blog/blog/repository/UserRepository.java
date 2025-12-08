package _blog.blog.repository;

import java.util.Optional;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import _blog.blog.entity.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);
    @Query("""
        SELECT u FROM User u
        LEFT JOIN FETCH u.subscriptions s
        LEFT JOIN FETCH u.Posts p
        WHERE u.username = :username
    """)
    Optional<User> findByUsernameWithSubscriptionsAndPosts(@Param("username") String username);
    @Query("SELECT u FROM User u WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<User> searchUsers(@Param("keyword") String keyword);
}