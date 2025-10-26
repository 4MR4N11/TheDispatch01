package _blog.blog.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import _blog.blog.dto.CommentResponse;
import jakarta.validation.Valid;
import _blog.blog.dto.PostRequest;
import _blog.blog.dto.PostResponse;
import _blog.blog.entity.Post;
import _blog.blog.entity.User;
import _blog.blog.enums.Role;
import _blog.blog.service.CommentService;
import _blog.blog.service.PostService;
import _blog.blog.service.UserService;

@RestController
@RequestMapping("/posts")
public class PostController {
    private final UserService userService;
    private final PostService postService;
    // private final LikeService likeService;
    private final CommentService commentService;

    public PostController(UserService userService, PostService postService, CommentService commentService) {
        this.userService = userService;
        this.postService = postService;
        // this.likeService = likeService;
        this.commentService = commentService;
    }

    @GetMapping("/my-posts")
    public ResponseEntity<List<PostResponse>> getMyPosts(Authentication auth) {
        User user = userService.getUserByUsername(auth.getName());
        List<Post> posts = postService.getPostsByIdWithCommentsAndLikes(user.getId());
        List<PostResponse> respPosts = new ArrayList<>();
        
        for (Post p : posts) {
            List<CommentResponse> comments = commentService.getCommentsRespByPost(p.getId());
            respPosts.add(new PostResponse(
                p.getId(),
                p.getAuthor().getUsername(),
                p.getAuthor().getAvatar(),
                p.getTitle(),
                p.getContent(),
                p.getMediaType(),
                p.getMediaUrl(),
                p.isHidden(),
                comments,
                p.getCreatedAt(),
                p.getUpdatedAt(),
                p.getLikedBy().size(), // Add like count
                p.getLikedBy().stream()
                    .map(User::getUsername)
                    .toList() // Add list of users who liked
            ));
        }
        return ResponseEntity.ok(respPosts);
    }

    @GetMapping("/{username}")
    public ResponseEntity<List<PostResponse>> getPostsByUsername(@PathVariable String username) {
        User user = userService.getUserByUsername(username);
        // Use getVisiblePostsByIdWithCommentsAndLikes to filter out hidden posts for public view
        List<Post> posts = postService.getVisiblePostsByIdWithCommentsAndLikes(user.getId());
        List<PostResponse> respPosts = new ArrayList<>();

        for (Post p : posts) {
            List<CommentResponse> comments = commentService.getCommentsRespByPost(p.getId());
            respPosts.add(new PostResponse(
                p.getId(),
                p.getAuthor().getUsername(),
                p.getAuthor().getAvatar(),
                p.getTitle(),
                p.getContent(),
                p.getMediaType(),
                p.getMediaUrl(),
                p.isHidden(),
                comments,
                p.getCreatedAt(),
                p.getUpdatedAt(),
                p.getLikedBy().size(), // Add like count
                p.getLikedBy().stream()
                    .map(User::getUsername)
                    .toList() // Add list of users who liked
            ));
        }
        return ResponseEntity.ok(respPosts);
    }

    @GetMapping("/post/{postId}")
    public ResponseEntity<PostResponse> getPostById(@PathVariable Long postId) {
        Post post = postService.getPostByIdWithCommentsAndLikes(postId);
        List<CommentResponse> comments = commentService.getCommentsRespByPost(post.getId());
        PostResponse response = new PostResponse(
            post.getId(),
            post.getAuthor().getUsername(),
            post.getAuthor().getAvatar(),
            post.getTitle(),
            post.getContent(),
            post.getMediaType(),
            post.getMediaUrl(),
            post.isHidden(),
            comments,
            post.getCreatedAt(),
            post.getUpdatedAt(),
            post.getLikedBy().size(),
            post.getLikedBy().stream()
                .map(User::getUsername)
                .toList()
        );
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/create")
    public ResponseEntity<String> createPost(@Valid @RequestBody PostRequest request, Authentication auth) {
        User user = userService.getUserByUsername(auth.getName());
        postService.createPost(request, user);
        return ResponseEntity.ok("Post has been created.");
    }

    @PutMapping("/{id}")
    public ResponseEntity<String> updatePost(@PathVariable Long id, @Valid @RequestBody PostRequest request, Authentication auth) {
        User user = userService.getUserByUsername(auth.getName());
        Post post = postService.getPostById(id);

        // Check if the user is the author of the post
        if (!post.getAuthor().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body("You are not authorized to edit this post");
        }

        postService.updatePost(id, request);
        return ResponseEntity.ok("Post has been updated.");
    }

    @GetMapping("/all")
    public ResponseEntity<List<PostResponse>> getAllPosts() {
        List<Post> posts = postService.getAllPostsWithCommentsAndLikes();
        List<PostResponse> respPosts = new ArrayList<>();

        for (Post p : posts) {
            List<CommentResponse> comments = commentService.getCommentsRespByPost(p.getId());
            respPosts.add(new PostResponse(
                p.getId(),
                p.getAuthor().getUsername(),
                p.getAuthor().getAvatar(),
                p.getTitle(),
                p.getContent(),
                p.getMediaType(),
                p.getMediaUrl(),
                p.isHidden(),
                comments,
                p.getCreatedAt(),
                p.getUpdatedAt(),
                p.getLikedBy().size(),
                p.getLikedBy().stream()
                    .map(User::getUsername)
                    .toList()
            ));
        }
        return ResponseEntity.ok(respPosts);
    }

    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<PostResponse>> getAllPostsForAdmin() {
        List<Post> posts = postService.getAllPostsIncludingHidden();
        List<PostResponse> respPosts = new ArrayList<>();

        for (Post p : posts) {
            List<CommentResponse> comments = commentService.getCommentsRespByPost(p.getId());
            respPosts.add(new PostResponse(
                p.getId(),
                p.getAuthor().getUsername(),
                p.getAuthor().getAvatar(),
                p.getTitle(),
                p.getContent(),
                p.getMediaType(),
                p.getMediaUrl(),
                p.isHidden(),
                comments,
                p.getCreatedAt(),
                p.getUpdatedAt(),
                p.getLikedBy().size(),
                p.getLikedBy().stream()
                    .map(User::getUsername)
                    .toList()
            ));
        }
        return ResponseEntity.ok(respPosts);
    }

    @GetMapping("/feed")
    public ResponseEntity<List<PostResponse>> getFeed(Authentication auth) {
        User user = userService.getUserByUsername(auth.getName());
        List<Post> posts = postService.getFeedPosts(user.getId());
        List<PostResponse> respPosts = new ArrayList<>();
        
        for (Post p : posts) {
            List<CommentResponse> comments = commentService.getCommentsRespByPost(p.getId());
            respPosts.add(new PostResponse(
                p.getId(),
                p.getAuthor().getUsername(),
                p.getAuthor().getAvatar(),
                p.getTitle(),
                p.getContent(),
                p.getMediaType(),
                p.getMediaUrl(),
                p.isHidden(),
                comments,
                p.getCreatedAt(),
                p.getUpdatedAt(),
                p.getLikedBy().size(),
                p.getLikedBy().stream()
                    .map(User::getUsername)
                    .toList()
            ));
        }
        return ResponseEntity.ok(respPosts);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deletePost(@PathVariable Long id, Authentication auth) {
        User user = userService.getUserByUsername(auth.getName());
        Post post = postService.getPostById(id);

        // Check if the user is the author or an admin
        if (!post.getAuthor().getId().equals(user.getId()) && user.getRole() != Role.ADMIN) {
            return ResponseEntity.status(403).body(Map.of("message", "You are not authorized to delete this post"));
        }

        if (postService.deletePost(id)) {
            return ResponseEntity.ok(Map.of("message", "Post deleted successfully!"));
        } else {
            return ResponseEntity.status(404).body(Map.of("message", "Post not found!"));
        }
    }

    @PutMapping("/hide/{id}")
    public ResponseEntity<Map<String, String>> hidePost(@PathVariable Long id, Authentication auth) {
        User user = userService.getUserByUsername(auth.getName());
        Post post = postService.getPostById(id);

        // Check if the user is the author or an admin
        if (!post.getAuthor().getId().equals(user.getId()) && user.getRole() != Role.ADMIN) {
            return ResponseEntity.status(403).body(Map.of("message", "You are not authorized to hide this post"));
        }

        if (postService.hidePost(id)) {
            return ResponseEntity.ok(Map.of("message", "Post hidden successfully!"));
        } else {
            return ResponseEntity.status(404).body(Map.of("message", "Post not found!"));
        }
    }

    @PutMapping("/unhide/{id}")
    public ResponseEntity<Map<String, String>> unhidePost(@PathVariable Long id, Authentication auth) {
        User user = userService.getUserByUsername(auth.getName());
        Post post = postService.getPostById(id);

        // Check if the user is the author or an admin
        if (!post.getAuthor().getId().equals(user.getId()) && user.getRole() != Role.ADMIN) {
            return ResponseEntity.status(403).body(Map.of("message", "You are not authorized to unhide this post"));
        }

        if (postService.unhidePost(id)) {
            return ResponseEntity.ok(Map.of("message", "Post unhidden successfully!"));
        } else {
            return ResponseEntity.status(404).body(Map.of("message", "Post not found!"));
        }
    }
}