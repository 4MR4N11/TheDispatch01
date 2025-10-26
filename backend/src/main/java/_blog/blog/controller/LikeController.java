package _blog.blog.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import _blog.blog.dto.LikeResponse;
import _blog.blog.entity.User;
import _blog.blog.service.LikeService;
import _blog.blog.service.UserService;

@RestController
@RequestMapping("/likes")
public class LikeController {
    
    private final LikeService likeService;
    private final UserService userService;

    public LikeController(LikeService likeService, UserService userService) {
        this.likeService = likeService;
        this.userService = userService;
    }

    @PostMapping("/post/{postId}")
    public ResponseEntity<String> likePost(
            @PathVariable Long postId,
            Authentication auth
    ) {
        User user = userService.getUserByUsername(auth.getName());
        
        if (likeService.isPostLikedByUser(postId, user.getId())) {
            return ResponseEntity.badRequest().body("Post already liked");
        }
        
        likeService.likePost(postId, user.getId());
        return ResponseEntity.ok("Post liked successfully");
    }

    @DeleteMapping("/post/{postId}")
    public ResponseEntity<String> unlikePost(
            @PathVariable Long postId,
            Authentication auth
    ) {
        User user = userService.getUserByUsername(auth.getName());
        
        if (!likeService.isPostLikedByUser(postId, user.getId())) {
            return ResponseEntity.badRequest().body("Post not liked yet");
        }
        
        likeService.unlikePost(postId, user.getId());
        return ResponseEntity.ok("Post unliked successfully");
    }

    @GetMapping("/post/{postId}")
    public ResponseEntity<LikeResponse> getPostLikes(@PathVariable Long postId) {
        List<String> likedByUsernames = likeService.getUsersWhoLikedPost(postId);
        long likeCount = likeService.getPostLikeCount(postId);
        
        LikeResponse response = new LikeResponse(
            postId,
            likeCount,
            likedByUsernames
        );
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/post/{postId}/check")
    public ResponseEntity<Boolean> checkIfPostLiked(
            @PathVariable Long postId,
            Authentication auth
    ) {
        User user = userService.getUserByUsername(auth.getName());
        boolean isLiked = likeService.isPostLikedByUser(postId, user.getId());
        return ResponseEntity.ok(isLiked);
    }

    @GetMapping("/my-liked-posts")
    public ResponseEntity<List<Long>> getMyLikedPosts(Authentication auth) {
        User user = userService.getUserByUsername(auth.getName());
        List<Long> likedPostIds = likeService.getLikedPostsByUser(user.getId());
        return ResponseEntity.ok(likedPostIds);
    }
}