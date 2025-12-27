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
import _blog.blog.exception.ForbiddenException;
import _blog.blog.exception.ResourceNotFoundException;
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
    public ResponseEntity<List<PostResponse>> getPostsByUsername(@PathVariable String username, Authentication auth) {
        User profileUser = userService.getUserByUsername(username);

        // Check if current user is admin
        boolean isAdmin = false;
        if (auth != null) {
            User currentUser = userService.getUserByUsername(auth.getName());
            isAdmin = currentUser.getRole() == Role.ADMIN;
        }

        // Get posts - admins see all, others see only visible posts
        List<Post> posts;
        if (isAdmin) {
            posts = postService.getPostsByIdWithCommentsAndLikes(profileUser.getId());
        } else {
            posts = postService.getVisiblePostsByIdWithCommentsAndLikes(profileUser.getId());
        }

        List<PostResponse> respPosts = postResponseMapper.toResponseList(posts);
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
            throw new ResourceNotFoundException("Post not found");
        }

        if (!post.getAuthor().getId().equals(user.getId()) && user.getRole() != Role.ADMIN) {
            throw new ForbiddenException("You are not authorized to edit this post");
        }

        postService.updatePost(id, request);
        return ResponseEntity.ok("Post has been updated.");
    }

    @GetMapping("/all")
    public ResponseEntity<List<PostResponse>> getAllPosts(Authentication auth) {
        List<Post> posts = postService.getAllPostsWithCommentsAndLikes();

        // Check if current user is admin
        boolean isAdmin = false;
        if (auth != null) {
            User currentUser = userService.getUserByUsername(auth.getName());
            isAdmin = currentUser.getRole() == Role.ADMIN;
        }

        // Filter out hidden posts for non-admins
        final boolean adminAccess = isAdmin;
        List<Post> filteredPosts = posts.stream()
                .filter(p -> !p.isHidden() || adminAccess)
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
        boolean isAdmin = user.getRole() == Role.ADMIN;
        Page<Post> postsPage = postService.getFeedPosts(user.getId(), page, size, isAdmin);

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
            throw new ForbiddenException("You are not authorized to delete this post");
        }
        if (post.isHidden() && user.getRole() != Role.ADMIN) {
            throw new ResourceNotFoundException("Post not found");
        }

        if (!postService.deletePost(id)) {
            throw new ResourceNotFoundException("Post", id);
        }

        return ResponseEntity.ok(Map.of("message", "Post deleted successfully!"));
    }

    @PutMapping("/hide/{id}")
    public ResponseEntity<Map<String, String>> hidePost(@PathVariable Long id, Authentication auth) {
        User user = userService.getUserByUsername(auth.getName());
        Post post = postService.getPostById(id);

        if (!post.getAuthor().getId().equals(user.getId()) && user.getRole() != Role.ADMIN) {
            throw new ForbiddenException("You are not authorized to hide this post");
        }

        if (!postService.hidePost(id)) {
            throw new ResourceNotFoundException("Post", id);
        }

        return ResponseEntity.ok(Map.of("message", "Post hidden successfully!"));
    }

    @PutMapping("/unhide/{id}")
    public ResponseEntity<Map<String, String>> unhidePost(@PathVariable Long id, Authentication auth) {
        User user = userService.getUserByUsername(auth.getName());

        if (user.getRole() != Role.ADMIN) {
            throw new ForbiddenException("You are not authorized to unhide this post");
        }

        if (!postService.unhidePost(id)) {
            throw new ResourceNotFoundException("Post", id);
        }

        return ResponseEntity.ok(Map.of("message", "Post unhidden successfully!"));
    }
}