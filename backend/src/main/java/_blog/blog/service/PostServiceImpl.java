package _blog.blog.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import _blog.blog.dto.PostRequest;
import _blog.blog.dto.PostResponse;
import _blog.blog.entity.Post;
import _blog.blog.entity.User;
import _blog.blog.exception.BadRequestException;
import _blog.blog.exception.ResourceNotFoundException;
import _blog.blog.mapper.PostMapper;
import _blog.blog.mapper.PostResponseMapper;
import _blog.blog.repository.NotificationRepository;
import _blog.blog.repository.PostRepository;
import _blog.blog.repository.ReportRepository;

@Service
public class PostServiceImpl implements PostService {

    private final PostRepository postRepository;
    private final SubscriptionService subscriptionService;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final ReportRepository reportRepository;
    private final PostResponseMapper postResponseMapper;

    public PostServiceImpl(PostRepository postRepository, SubscriptionService subscriptionService, NotificationRepository notificationRepository, NotificationService notificationService, ReportRepository reportRepository, PostResponseMapper postResponseMapper) {
        this.postRepository = postRepository;
        this.subscriptionService = subscriptionService;
        this.notificationRepository = notificationRepository;
        this.notificationService = notificationService;
        this.reportRepository = reportRepository;
        this.postResponseMapper = postResponseMapper;
    }

    @Override
    public List<Post> getPostsById(Long id) {
        return postRepository.findAllByAuthorId(id);
    }

    @Override
    public Post createPost(PostRequest request, User author) {
        if (request.getTitle() == null || request.getTitle().trim().isBlank()) {
            throw new BadRequestException("Post title cannot be empty");
        }
        if (request.getContent() == null || request.getContent().trim().isBlank()) {
            throw new BadRequestException("Post content cannot be empty");
        }
        Post post = PostMapper.toEntity(request, author);
        Post savedPost = postRepository.save(post);

        // Notify followers about the new post
        notificationService.notifyNewPost(author, savedPost);

        return savedPost;
    }

    @Override
    public Post updatePost(Long postId, PostRequest request) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post", postId));

        if (request.getTitle() == null || request.getTitle().trim().isBlank()) {
            throw new BadRequestException("Post title cannot be empty");
        }
        if (request.getContent() == null || request.getContent().trim().isBlank()) {
            throw new BadRequestException("Post content cannot be empty");
        }

        post = PostMapper.toEntity(request, post.getAuthor());
        post.setId(postId);

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
        return getFeedPosts(userId, page, size, false);
    }

    @Override
    public Page<Post> getFeedPosts(Long userId, int page, int size, boolean includeHidden) {
        List<User> subscriptions = subscriptionService.getSubscriptions(userId);

        List<Long> authorIds = new ArrayList<>();
        authorIds.add(userId);
        authorIds.addAll(subscriptions.stream().map(User::getId).toList());

        if (includeHidden) {
            return postRepository.findPostsByAuthorIdsIncludingHidden(authorIds, PageRequest.of(page, size));
        } else {
            return postRepository.findPostsByAuthorIds(authorIds, PageRequest.of(page, size));
        }
    }


    @Override
    public List<PostResponse> getPostsRespByUserId(Long userId) {
        List<Post> posts = getPostsById(userId);
        return postResponseMapper.toResponseList(posts);
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