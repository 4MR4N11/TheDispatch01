package _blog.blog.mapper;

import _blog.blog.dto.CommentResponse;
import _blog.blog.dto.PostResponse;
import _blog.blog.entity.Post;
import _blog.blog.service.CommentService;
import _blog.blog.service.LikeService;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Mapper to convert Post entities to PostResponse DTOs
 * Centralizes the mapping logic to avoid duplication
 */
@Component
public class PostResponseMapper {

    private final CommentService commentService;
    private final LikeService likeService;

    public PostResponseMapper(CommentService commentService, LikeService likeService) {
        this.commentService = commentService;
        this.likeService = likeService;
    }

    /**
     * Maps a single Post entity to PostResponse DTO
     * @param post The post entity to map
     * @return The mapped PostResponse DTO
     */
    public PostResponse toResponse(Post post) {
        if (post == null) {
            return null;
        }

        List<CommentResponse> comments = commentService.getCommentsRespByPost(post.getId());
        Long likeCount = likeService.getPostLikeCount(post.getId());
        List<String> likedBy = likeService.getUsersWhoLikedPost(post.getId());

        return new PostResponse(
                post.getId(),
                post.getAuthor().getUsername(),
                post.getTitle(),
                post.getContent(),
                post.getMediaType(),
                post.getMediaUrl(),
                post.isHidden(),
                comments,
                post.getCreatedAt(),
                post.getUpdatedAt(),
                likeCount,
                likedBy
        );
    }

    /**
     * Maps a list of Post entities to PostResponse DTOs
     * @param posts The list of post entities to map
     * @return The list of mapped PostResponse DTOs
     */
    public List<PostResponse> toResponseList(List<Post> posts) {
        if (posts == null) {
            return List.of();
        }

        return posts.stream()
                .map(this::toResponse)
                .toList();
    }
}
