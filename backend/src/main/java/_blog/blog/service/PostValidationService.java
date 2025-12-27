package _blog.blog.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import _blog.blog.entity.Post;
import _blog.blog.entity.User;
import _blog.blog.enums.Role;
import _blog.blog.repository.PostRepository;
import lombok.RequiredArgsConstructor;

/**
 * Service for validating post access and visibility.
 * Ensures that hidden posts can only be accessed by their authors or admins.
 */
@Service
@RequiredArgsConstructor
public class PostValidationService {

    private final PostRepository postRepository;

    /**
     * Validates that a post is not hidden, or if hidden, that the current user is an admin.
     * Only admins can access hidden posts.
     *
     * @param postId      The ID of the post to validate
     * @param currentUser The current user (can be null for unauthenticated users)
     * @throws ResponseStatusException if the post is hidden and user is not an admin
     */
    public void validatePostIsNotHidden(Long postId, User currentUser) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Post not found"
                ));

        validatePostActionAccess(post, currentUser);
    }

    /**
     * Legacy method - validates with userId only (deprecated, cannot check admin role).
     * Hidden posts can only be accessed by admins.
     *
     * @param post          The post to validate access for
     * @param currentUserId The ID of the current user (can be null for unauthenticated users)
     * @throws ResponseStatusException if the post is hidden (admin check not possible with userId only)
     * @deprecated Use {@link #validatePostActionAccess(Post, User)} instead for proper admin checking
     */
    @Deprecated
    public void validatePostAccess(Post post, Long currentUserId) {
        if (!post.isHidden()) {
            return;
        }
        throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "This post is hidden and only accessible to administrators"
        );
    }

    /**
     * Checks if a user has access to a post (returns boolean instead of throwing exception).
     * Only admins can access hidden posts.
     *
     * @param post        The post to check
     * @param currentUser The current user
     * @return true if user has access, false otherwise
     */
    public boolean hasPostAccess(Post post, User currentUser) {
        if (!post.isHidden()) {
            return true; // Public posts accessible to all
        }

        // Hidden posts only accessible to admins
        return currentUser != null && currentUser.getRole() == Role.ADMIN;
    }

    /**
     * Validates that a user can perform actions on a post (like, comment, etc.).
     * Only admins can access hidden posts. Regular users (including post authors) cannot.
     *
     * @param post        The post to validate
     * @param currentUser The current user (can be null for unauthenticated users)
     * @throws ResponseStatusException if the post is hidden and user is not an admin
     */
    public void validatePostActionAccess(Post post, User currentUser) {
        if (!post.isHidden()) {
            return; // Public posts allow all actions
        }

        // Post is hidden - only admins can access
        if (currentUser != null && currentUser.getRole() == Role.ADMIN) {
            return; // Admin can access all hidden posts
        }

        // Hidden posts return 404 to avoid leaking information about their existence
        throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Post not found"
        );
    }
}
