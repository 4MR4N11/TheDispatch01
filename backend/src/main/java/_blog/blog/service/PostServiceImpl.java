package _blog.blog.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import _blog.blog.dto.PostRequest;
import _blog.blog.dto.PostResponse;
import _blog.blog.entity.Post;
import _blog.blog.entity.User;
import _blog.blog.mapper.PostMapper;
import _blog.blog.repository.NotificationRepository;
import _blog.blog.repository.PostRepository;

@Service
public class PostServiceImpl implements PostService {

    private final PostRepository postRepository;
    private final SubscriptionService subscriptionService;
    private final CommentService commentService;
    private final LikeService likeService;
    private final NotificationRepository notificationRepository;

    public PostServiceImpl(LikeService likeService, CommentService commentService, PostRepository postRepository, SubscriptionService subscriptionService, NotificationRepository notificationRepository) {
        this.postRepository = postRepository;
        this.subscriptionService = subscriptionService;
        this.commentService = commentService;
        this.likeService = likeService;
        this.notificationRepository = notificationRepository;
    }

    @Override
    public List<Post> getPostsById(Long id) {
        return postRepository.findAllByAuthorId(id);
    }

    @Override
    public Post createPost(PostRequest request, User author) {
        Post post = PostMapper.toEntity(request, author);
        return postRepository.save(post);
    }

    @Override
    public Post updatePost(Long postId, PostRequest request) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        post.setTitle(request.getTitle());
        post.setContent(request.getContent());
        post.setMediaType(request.getMedia_type());
        post.setMediaUrl(request.getMedia_url());

        return postRepository.save(post);
    }

    @Override
    public Post getPostById(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));
    }

    @Override
    public Post getPostByIdWithLikes(Long postId) {
        return postRepository.findByIdWithLikes(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));
    }

    @Override
    public Post getPostByIdWithCommentsAndLikes(Long postId) {
        return postRepository.findByIdWithCommentsAndLikes(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));
    }

    @Override
    public List<Post> getPostsByIdWithCommentsAndLikes(Long authorId) {
        return postRepository.findAllByAuthorIdWithCommentsAndLikes(authorId);
    }

    @Override
    public List<Post> getVisiblePostsByIdWithCommentsAndLikes(Long authorId) {
        return postRepository.findAllVisibleByAuthorIdWithCommentsAndLikes(authorId);
    }

    @Override
    public List<Post> getAllPostsWithCommentsAndLikes() {
        return postRepository.findAllWithCommentsAndLikes();
    }

    @Override
    public List<Post> getAllPostsIncludingHidden() {
        return postRepository.findAllWithCommentsAndLikesIncludingHidden();
    }

    @Override
    public List<Post> getFeedPosts(Long userId) {
        List<User> subscriptions = subscriptionService.getSubscriptions(userId);

        // Include the user's own ID along with followed users
        List<Long> authorIds = new ArrayList<>();
        authorIds.add(userId); // Add user's own posts

        // Add followed users' posts
        authorIds.addAll(subscriptions.stream()
                .map(User::getId)
                .toList());

        return postRepository.findPostsByAuthorIdsWithCommentsAndLikes(authorIds);
    }

    @Override
    public List<PostResponse> getPostsRespByUserId(Long userId) {
        List<Post> Posts = getPostsById(userId);
        List<PostResponse> PostResponses = new ArrayList<>();
        for (Post p : Posts) {
            PostResponses.add(new PostResponse(
                p.getId(),
                p.getAuthor().getUsername(),
                p.getAuthor().getAvatar(),
                p.getTitle(),
                p.getContent(),
                p.getMediaType(),
                p.getMediaUrl(),
                p.isHidden(),
                commentService.getCommentsRespByPost(p.getId()),
                p.getCreatedAt(),
                p.getUpdatedAt(),
                likeService.getPostLikeCount(p.getId()),
                likeService.getUsersWhoLikedPost(p.getId())
            ));
        }

        return PostResponses;
    }

    @Override
    @Transactional
    public boolean deletePost(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        // Delete all notifications related to this post first
        notificationRepository.deleteByPost(post);

        // Then delete the post (comments and likes are already handled by cascade)
        postRepository.delete(post);
        return true;
    }

    @Override
    public boolean hidePost(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));
        post.setHidden(true);
        postRepository.save(post);
        return true;
    }

    @Override
    public boolean unhidePost(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));
        post.setHidden(false);
        postRepository.save(post);
        return true;
    }
}