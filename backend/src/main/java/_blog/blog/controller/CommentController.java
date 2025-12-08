package _blog.blog.controller;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import _blog.blog.dto.CommentRequest;
import _blog.blog.dto.CommentResponse;
import _blog.blog.entity.Comment;
import _blog.blog.entity.Post;
import _blog.blog.entity.User;
import _blog.blog.service.CommentService;
import _blog.blog.service.PostService;
import _blog.blog.service.UserService;

import java.util.Date;

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

    // ✅ Create Comment
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

    // ✅ Get Comments By Post (with updatedAt)
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
                    comment.getCreatedAt(),
                    comment.getUpdatedAt()
            ));
        }

        return commentResponses;
    }

    // ✅ Get All Comments (with updatedAt)
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
                    comment.getCreatedAt(),
                    comment.getUpdatedAt()
            ));
        }

        return ResponseEntity.ok(commentResponses);
    }

    // ✅ Update Comment (keeps createdAt, updates updatedAt)
    @PutMapping("/{commentId}")
    public ResponseEntity<String> updateComment(
            @PathVariable Long commentId,
            @Valid @RequestBody CommentRequest request,
            Authentication auth
    ) {
        User user = userService.getUserByUsername(auth.getName());
        Comment existingComment = commentService.getCommentById(commentId);

        // Author check
        if (!existingComment.getAuthor().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body("You can only edit your own comments");
        }

        // Update fields
        existingComment.setContent(request.getContent());
        existingComment.setUpdatedAt(fromLocalDateTime(LocalDateTime.now()));

        commentService.updateComment(existingComment, commentId);
        return ResponseEntity.ok("Comment updated successfully");
    }

    Date fromLocalDateTime(LocalDateTime ldt) {
        return Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
    }

    // ✅ Delete Comment
    @DeleteMapping("/{commentId}")
    public ResponseEntity<String> deleteComment(
            @PathVariable Long commentId,
            Authentication auth
    ) {
        User user = userService.getUserByUsername(auth.getName());
        Comment comment = commentService.getCommentById(commentId);

        if (!comment.getAuthor().getId().equals(user.getId())
                && !user.getRole().name().equals("ADMIN")) {
            return ResponseEntity.status(403).body("You can only delete your own comments");
        }

        commentService.deleteComment(commentId);
        return ResponseEntity.ok("Comment deleted successfully");
    }
}
