package _blog.blog.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import _blog.blog.dto.PostRequest;
import _blog.blog.dto.PostResponse;
import _blog.blog.entity.Post;
import _blog.blog.entity.User;
import _blog.blog.enums.Role;
import _blog.blog.mapper.PostResponseMapper;
import _blog.blog.service.PostService;
import _blog.blog.service.PostValidationService;
import _blog.blog.service.UserService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/posts")
public class PostController {
    private final UserService userService;
    private final PostService postService;
    private final PostValidationService postValidationService;
    private final PostResponseMapper postResponseMapper;

    public PostController(UserService userService, PostService postService, PostValidationService postValidationService, PostResponseMapper postResponseMapper) {
        this.userService = userService;
        this.postService = postService;
        this.postValidationService = postValidationService;
        this.postResponseMapper = postResponseMapper;
    }

    @GetMapping("/my-posts")
    public ResponseEntity<List<PostResponse>> getMyPosts(Authentication auth) {
        User user = userService.getUserByUsername(auth.getName());
        List<Post> posts = postService.getPostsByIdWithCommentsAndLikes(user.getId());

        // Filter out hidden posts for non-admins
        List<Post> filteredPosts = posts.stream()
                .filter(p -> !p.isHidden() || user.getRole() == Role.ADMIN)
                .toList();

        List<PostResponse> respPosts = postResponseMapper.toResponseList(filteredPosts);
        return ResponseEntity.ok(respPosts);
    }

    @GetMapping("/{username}")
    public ResponseEntity<List<PostResponse>> getPostsByUsername(@PathVariable String username) {
        User user = userService.getUserByUsername(username);
        // Use getVisiblePostsByIdWithCommentsAndLikes to filter out hidden posts for public view
        List<Post> posts = postService.getVisiblePostsByIdWithCommentsAndLikes(user.getId());

        // Filter out hidden posts for non-admins (double-check even though service should handle this)
        List<Post> filteredPosts = posts.stream()
                .filter(p -> !p.isHidden() || user.getRole() == Role.ADMIN)
                .toList();

        List<PostResponse> respPosts = postResponseMapper.toResponseList(filteredPosts);
        return ResponseEntity.ok(respPosts);
    }

    @GetMapping("/post/{postId}")
    public ResponseEntity<PostResponse> getPostById(@PathVariable Long postId, Authentication auth) {
        if (auth != null) {
            User user = userService.getUserByUsername(auth.getName());
            postValidationService.validatePostIsNotHidden(postId, user);
        } else {
            postValidationService.validatePostIsNotHidden(postId, null);
        }

        Post post = postService.getPostByIdWithCommentsAndLikes(postId);
        PostResponse response = postResponseMapper.toResponse(post);

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
        if (post.isHidden() && user.getRole() != Role.ADMIN) {
            return ResponseEntity.status(403).body("Cannot edit a hidden post");
        }

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

        // Filter out hidden posts for non-admins
        List<Post> filteredPosts = posts.stream()
                .filter(p -> !p.isHidden() || p.getAuthor().getRole() == Role.ADMIN)
                .toList();

        List<PostResponse> respPosts = postResponseMapper.toResponseList(filteredPosts);
        return ResponseEntity.ok(respPosts);
    }

    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<PostResponse>> getAllPostsForAdmin() {
        List<Post> posts = postService.getAllPostsIncludingHidden();
        List<PostResponse> respPosts = postResponseMapper.toResponseList(posts);
        return ResponseEntity.ok(respPosts);
    }

    @GetMapping("/feed")
    public ResponseEntity<Map<String, Object>> getFeed(
            Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        User user = userService.getUserByUsername(auth.getName());
        Page<Post> postsPage = postService.getFeedPosts(user.getId(), page, size);

        List<PostResponse> respPosts = postResponseMapper.toResponseList(postsPage.getContent());

        Map<String, Object> response = new HashMap<>();
        response.put("posts", respPosts);
        response.put("currentPage", postsPage.getNumber());
        response.put("totalItems", postsPage.getTotalElements());
        response.put("totalPages", postsPage.getTotalPages());
        response.put("isLast", postsPage.isLast());

        return ResponseEntity.ok(response);
    }


    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deletePost(@PathVariable Long id, Authentication auth) {
        User user = userService.getUserByUsername(auth.getName());
        Post post = postService.getPostById(id);

        if (!post.getAuthor().getId().equals(user.getId()) && user.getRole() != Role.ADMIN) {
            return ResponseEntity.status(403).body(Map.of("message", "You are not authorized to delete this post"));
        }
        if (post.isHidden() && user.getRole() != Role.ADMIN) {
            return ResponseEntity.status(403).body(Map.of("message", "Cannot delete a hidden post"));
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

        if (user.getRole() != Role.ADMIN) {
            return ResponseEntity.status(403).body(Map.of("message", "You are not authorized to unhide this post"));
        }

        if (postService.unhidePost(id)) {
            return ResponseEntity.ok(Map.of("message", "Post unhidden successfully!"));
        } else {
            return ResponseEntity.status(404).body(Map.of("message", "Post not found!"));
        }
    }
}