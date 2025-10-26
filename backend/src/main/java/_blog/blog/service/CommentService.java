package _blog.blog.service;

import java.util.List;

import _blog.blog.dto.CommentResponse;
import _blog.blog.entity.Comment;

public interface CommentService {
    Comment saveComment(Comment comment);
    List<Comment> fetchComments();
    Comment updateComment(Comment comment, Long commentId);
    void deleteComment(Long commentId);
    Comment getCommentById(Long commentId);
    List<Comment> getCommentsByPostId(Long postId);
    List<Comment> getCommentsByAuthorId(Long authorId);
    long getCommentCountByPostId(Long postId);
    List<CommentResponse> getCommentsRespByPost(Long postId);
}