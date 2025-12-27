package _blog.blog.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import _blog.blog.entity.Post;

public interface PostRepository extends JpaRepository<Post, Long> {
    
    List<Post> findAllByAuthorId(Long authorId);

    @Query("SELECT p FROM Post p LEFT JOIN FETCH p.likedBy WHERE p.id = :postId")
    Optional<Post> findByIdWithLikes(@Param("postId") Long postId);

    @Query("SELECT p.id FROM Post p JOIN p.likedBy u WHERE u.id = :userId AND p.hidden = false")
    List<Long> findPostsLikedByUser(@Param("userId") Long userId);

    @Query("SELECT p FROM Post p LEFT JOIN FETCH p.comments LEFT JOIN FETCH p.likedBy WHERE p.id = :postId")
    Optional<Post> findByIdWithCommentsAndLikes(@Param("postId") Long postId);

    @Query("SELECT p FROM Post p LEFT JOIN FETCH p.comments LEFT JOIN FETCH p.likedBy WHERE p.author.id = :authorId ORDER BY p.createdAt DESC")
    List<Post> findAllByAuthorIdWithCommentsAndLikes(@Param("authorId") Long authorId);

    @Query("SELECT p FROM Post p LEFT JOIN FETCH p.comments LEFT JOIN FETCH p.likedBy WHERE p.author.id = :authorId AND p.hidden = false ORDER BY p.createdAt DESC")
    List<Post> findAllVisibleByAuthorIdWithCommentsAndLikes(@Param("authorId") Long authorId);

    @Query("SELECT p FROM Post p LEFT JOIN FETCH p.comments LEFT JOIN FETCH p.likedBy WHERE p.hidden = false ORDER BY p.createdAt DESC")
    List<Post> findAllWithCommentsAndLikes();

    @Query("SELECT p FROM Post p LEFT JOIN FETCH p.comments LEFT JOIN FETCH p.likedBy WHERE p.hidden = false ORDER BY p.createdAt DESC")
    List<Post> findAllWithCommentsAndLikesOrderByCreatedAtDesc();

    @Query("SELECT p FROM Post p LEFT JOIN FETCH p.comments LEFT JOIN FETCH p.likedBy WHERE p.author.id IN :authorIds AND p.hidden = false ORDER BY p.createdAt DESC")
    List<Post> findPostsByAuthorIdsWithCommentsAndLikes(@Param("authorIds") List<Long> authorIds);

    // Admin queries - include hidden posts
    @Query("SELECT p FROM Post p LEFT JOIN FETCH p.comments LEFT JOIN FETCH p.likedBy ORDER BY p.createdAt DESC")
    List<Post> findAllWithCommentsAndLikesIncludingHidden();


    @Query("""
        SELECT p FROM Post p
        WHERE p.hidden = false AND p.author.id IN :authorIds
        ORDER BY p.createdAt DESC
    """)
    Page<Post> findPostsByAuthorIds(@Param("authorIds") List<Long> authorIds, Pageable pageable);

    // Admin version - includes hidden posts
    @Query("""
        SELECT p FROM Post p
        WHERE p.author.id IN :authorIds
        ORDER BY p.createdAt DESC
    """)
    Page<Post> findPostsByAuthorIdsIncludingHidden(@Param("authorIds") List<Long> authorIds, Pageable pageable);
}

