package _blog.blog.service;

import java.util.List;

public interface  LikeService {
    void likePost(Long postId, Long userId);
    void unlikePost(Long postId, Long userId);
    boolean isPostLikedByUser(Long postId, Long userId);
    long getPostLikeCount(Long postId);
    List<String> getUsersWhoLikedPost(Long postId);
    List<Long> getLikedPostsByUser(Long userId);
}