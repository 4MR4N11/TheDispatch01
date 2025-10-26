package _blog.blog.service;

import java.util.List;

import _blog.blog.dto.PostRequest;
import _blog.blog.dto.PostResponse;
import _blog.blog.entity.Post;
import _blog.blog.entity.User;

public interface PostService {
    Post createPost(PostRequest request, User author);
    Post updatePost(Long postId, PostRequest request);
    List<Post> getPostsById(Long id);
    Post getPostById(Long postId);
    Post getPostByIdWithLikes(Long postId);
    Post getPostByIdWithCommentsAndLikes(Long postId);
    List<Post> getPostsByIdWithCommentsAndLikes(Long authorId);
    List<Post> getVisiblePostsByIdWithCommentsAndLikes(Long authorId);
    List<Post> getAllPostsWithCommentsAndLikes();
    List<Post> getAllPostsIncludingHidden();
    List<Post> getFeedPosts(Long userId);
    List<PostResponse> getPostsRespByUserId(Long userId);
    boolean deletePost(Long postId);
    boolean hidePost(Long postId);
    boolean unhidePost(Long postId);
}