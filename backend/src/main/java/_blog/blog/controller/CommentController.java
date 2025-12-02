package _blog.blog.controller;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import _blog.blog.dto.CommentRequest;
import _blog.blog.dto.CommentResponse;
import _blog.blog.entity.Comment;
import _blog.blog.entity.Post;
import _blog.blog.entity.User;
import _blog.blog.service.CommentService;
import _blog.blog.service.PostService;
import _blog.blog.service.UserService;

@RestController
@RequestMapping("/comments")
public class CommentController {
    
    private final CommentService commentService;
    private final UserService userService;
    private final PostService postService;

    public CommentController(CommentService commentService, UserService userService, PostService postService) {
        this.commentService = commentService;
        this.userService = userService;
        this.postService = postService;
    }

    // ✅ SECURITY FIX: Added @Valid annotation to enforce input validation
    @PostMapping("/create/{postId}")
    public ResponseEntity<String> createComment(
            @PathVariable Long postId,
            @Valid @RequestBody CommentRequest request,
            Authentication auth
    ) {
        User user = userService.getUserByUsername(auth.getName());
        Post post = postService.getPostById(postId);
        
        Comment comment = Comment.builder()
                .content(request.getContent())
                .author(user)
                .post(post)
                .build();
        
        commentService.saveComment(comment);
        return ResponseEntity.ok("Comment created successfully");
    }

    @GetMapping("/post/{postId}")
    public List<CommentResponse> getCommentsByPost(@PathVariable Long postId) {
        List<Comment> comments = commentService.getCommentsByPostId(postId);
        List<CommentResponse> commentResponses = new ArrayList<>();

        for (Comment comment : comments) {
            commentResponses.add(new CommentResponse(
                comment.getId(),
                comment.getAuthor().getUsername(),
                comment.getAuthor().getAvatar(),
                comment.getContent(),
                comment.getCreatedAt()
            ));
        }

        return commentResponses;
    }

    @GetMapping
    public ResponseEntity<List<CommentResponse>> getAllComments() {
        List<Comment> comments = commentService.fetchComments();
        List<CommentResponse> commentResponses = new ArrayList<>();

        for (Comment comment : comments) {
            commentResponses.add(new CommentResponse(
                comment.getId(),
                comment.getAuthor().getUsername(),
                comment.getAuthor().getAvatar(),
                comment.getContent(),
                comment.getCreatedAt()
            ));
        }

        return ResponseEntity.ok(commentResponses);
    }

    @PutMapping("/{commentId}")
    public ResponseEntity<String> updateComment(
            @PathVariable Long commentId,
            @Valid @RequestBody CommentRequest request,
            Authentication auth
    ) {
        User user = userService.getUserByUsername(auth.getName());
        Comment existingComment = commentService.getCommentById(commentId);
        
        // Check if the user is the author of the comment
        if (!existingComment.getAuthor().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body("You can only edit your own comments");
        }
        
        Comment updatedComment = Comment.builder()
                .id(commentId)
                .content(request.getContent())
                .author(existingComment.getAuthor())
                .post(existingComment.getPost())
                .createdAt(existingComment.getCreatedAt())
                .build();
        
        commentService.updateComment(updatedComment, commentId);
        return ResponseEntity.ok("Comment updated successfully");
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<String> deleteComment(
            @PathVariable Long commentId,
            Authentication auth
    ) {
        User user = userService.getUserByUsername(auth.getName());
        Comment comment = commentService.getCommentById(commentId);
        
        // Check if the user is the author of the comment or has admin role
        if (!comment.getAuthor().getId().equals(user.getId()) && !user.getRole().name().equals("ADMIN")) {
            return ResponseEntity.status(403).body("You can only delete your own comments");
        }
        
        commentService.deleteComment(commentId);
        return ResponseEntity.ok("Comment deleted successfully");
    }
}