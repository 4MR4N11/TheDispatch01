package _blog.blog.service;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import _blog.blog.entity.Post;
import _blog.blog.entity.User;
import _blog.blog.exception.BadRequestException;
import _blog.blog.exception.ResourceNotFoundException;
import _blog.blog.repository.PostRepository;
import _blog.blog.repository.UserRepository;

@Service
public class LikeServiceImpl implements LikeService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public LikeServiceImpl(PostRepository postRepository, UserRepository userRepository, NotificationService notificationService) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional
    public void likePost(Long postId, Long userId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post", postId));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (post.getLikedBy().contains(user)) {
            throw new BadRequestException("Post already liked by user");
        }

        post.getLikedBy().add(user);
        postRepository.save(post);

        // Create notification for post author
        notificationService.notifyPostLike(user, post);
    }

    @Override
    @Transactional
    public void unlikePost(Long postId, Long userId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post", postId));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (!post.getLikedBy().contains(user)) {
            throw new BadRequestException("Post not liked by user");
        }

        post.getLikedBy().remove(user);
        postRepository.save(post);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isPostLikedByUser(Long postId, Long userId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post", postId));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        return post.getLikedBy().contains(user);
    }

    @Override
    @Transactional(readOnly = true)
    public long getPostLikeCount(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post", postId));

        return post.getLikedBy().size();
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getUsersWhoLikedPost(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post", postId));

        return post.getLikedBy().stream()
                .map(User::getUsername)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> getLikedPostsByUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        return postRepository.findPostsLikedByUser(user.getId());
    }
}