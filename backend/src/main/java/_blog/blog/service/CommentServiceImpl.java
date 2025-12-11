package _blog.blog.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import _blog.blog.dto.CommentResponse;
import _blog.blog.entity.Comment;
import _blog.blog.exception.ResourceNotFoundException;
import _blog.blog.repository.CommentRepository;

@Service
public class CommentServiceImpl implements CommentService {

    private final CommentRepository commentRepository;
    private final NotificationService notificationService;

    public CommentServiceImpl(CommentRepository commentRepository, NotificationService notificationService) {
        this.commentRepository = commentRepository;
        this.notificationService = notificationService;
    }

    @Override
    public Comment saveComment(Comment comment) {
        Comment savedComment = commentRepository.save(comment);

        // Create notification for post author
        notificationService.notifyPostComment(comment.getAuthor(), comment.getPost(), savedComment);

        return savedComment;
    }

    @Override
    public List<Comment> fetchComments() {
        return commentRepository.findAll();
    }

    @Override
    public Comment updateComment(Comment comment, Long commentId) {
        Comment existingComment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment", commentId));

        existingComment.setContent(comment.getContent());
        return commentRepository.save(existingComment);
    }

    @Override
    public void deleteComment(Long commentId) {
        if (!commentRepository.existsById(commentId)) {
            throw new ResourceNotFoundException("Comment", commentId);
        }
        commentRepository.deleteById(commentId);
    }

    public Comment getCommentById(Long commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment", commentId));
    }

    public List<Comment> getCommentsByPostId(Long postId) {
        return commentRepository.findByPostId(postId);
    }

    public List<Comment> getCommentsByAuthorId(Long authorId) {
        return commentRepository.findByAuthorId(authorId);
    }

    public long getCommentCountByPostId(Long postId) {
        return commentRepository.countByPostId(postId);
    }

    @Override
    public List<CommentResponse> getCommentsRespByPost(Long postId) {
        List<Comment> comments = getCommentsByPostId(postId);
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
}