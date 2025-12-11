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
import _blog.blog.exception.ResourceNotFoundException;
import _blog.blog.repository.NotificationRepository;
import _blog.blog.repository.PostRepository;
import _blog.blog.repository.ReportRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;

@Service
public class PostServiceImpl implements PostService {

    private final PostRepository postRepository;
    private final SubscriptionService subscriptionService;
    private final CommentService commentService;
    private final LikeService likeService;
    private final NotificationRepository notificationRepository;
    private final ReportRepository reportRepository;

    public PostServiceImpl(LikeService likeService, CommentService commentService, PostRepository postRepository, SubscriptionService subscriptionService, NotificationRepository notificationRepository, ReportRepository reportRepository) {
        this.postRepository = postRepository;
        this.subscriptionService = subscriptionService;
        this.commentService = commentService;
        this.likeService = likeService;
        this.notificationRepository = notificationRepository;
        this.reportRepository = reportRepository;
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
                .orElseThrow(() -> new ResourceNotFoundException("Post", postId));

        post.setTitle(request.getTitle());
        post.setContent(request.getContent());
        post.setMediaType(request.getMedia_type());
        post.setMediaUrl(request.getMedia_url());

        return postRepository.save(post);
    }

    @Override
    public Post getPostById(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post", postId));
    }

    @Override
    public Post getPostByIdWithLikes(Long postId) {
        return postRepository.findByIdWithLikes(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post", postId));
    }

    @Override
    public Post getPostByIdWithCommentsAndLikes(Long postId) {
        return postRepository.findByIdWithCommentsAndLikes(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post", postId));
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
    public Page<Post> getFeedPosts(Long userId, int page, int size) {
        List<User> subscriptions = subscriptionService.getSubscriptions(userId);

        List<Long> authorIds = new ArrayList<>();
        authorIds.add(userId);
        authorIds.addAll(subscriptions.stream().map(User::getId).toList());

        return postRepository.findPostsByAuthorIds(authorIds, PageRequest.of(page, size));
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
                .orElseThrow(() -> new ResourceNotFoundException("Post", postId));

        // Delete all notifications related to this post first
        notificationRepository.deleteByPost(post);

        // Clear report references (keep reports for admin review, just remove post link)
        reportRepository.clearPostReference(postId);

        // Then delete the post (comments and likes are already handled by cascade)
        postRepository.delete(post);
        return true;
    }

    @Override
    public boolean hidePost(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post", postId));
        post.setHidden(true);
        postRepository.save(post);
        return true;
    }

    @Override
    public boolean unhidePost(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post", postId));
        post.setHidden(false);
        postRepository.save(post);
        return true;
    }
}