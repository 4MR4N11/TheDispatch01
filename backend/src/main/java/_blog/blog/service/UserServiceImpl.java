package _blog.blog.service;

import java.util.List;
import java.util.Optional;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import _blog.blog.dto.LoginRequest;
import _blog.blog.dto.RegisterRequest;
import _blog.blog.dto.UpdateProfileRequest;
import _blog.blog.dto.UserResponse;
import _blog.blog.entity.Post;
import _blog.blog.entity.User;
import _blog.blog.mapper.UserMapper;
import _blog.blog.repository.CommentRepository;
import _blog.blog.repository.NotificationRepository;
import _blog.blog.repository.PostRepository;
import _blog.blog.repository.ReportRepository;
import _blog.blog.repository.SubscriptionRepository;
import _blog.blog.repository.UserRepository;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final PostService postService;
    private final ReportRepository reportRepository;
    private final NotificationRepository notificationRepository;
    private final CommentRepository commentRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PostRepository postRepository;

    public UserServiceImpl(
        UserRepository userRepository,
        AuthenticationManager authenticationManager,
        PasswordEncoder passwordEncoder,
        PostService postService,
        ReportRepository reportRepository,
        NotificationRepository notificationRepository,
        CommentRepository commentRepository,
        SubscriptionRepository subscriptionRepository,
        PostRepository postRepository
    ) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.postService = postService;
        this.reportRepository = reportRepository;
        this.notificationRepository = notificationRepository;
        this.commentRepository = commentRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.postRepository = postRepository;
    }

    @Override
    public User register(RegisterRequest request) {
        User user = UserMapper.toEntity(request, passwordEncoder);
        if (user.getAvatar() == null) {
            user.setAvatar("");
        }
        return userRepository.save(user);
    }

    @Override
    public User authenticate(LoginRequest request) {
        // Find user by email or username
        User user = findUserByEmailOrUsername(request.getUsernameOrEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Check if user is banned
        if (user.isBanned()) {
            throw new RuntimeException("Your account has been banned. Please contact support.");
        }

        // Authenticate using the user's email as the principal
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                user.getEmail(), // Use email as consistent identifier for Spring Security
                request.getPassword()
            )
        );

        return user;
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String username) {
        User user = userRepository.findByUsernameWithSubscriptionsAndPosts(username)
            .orElseThrow(() -> new RuntimeException("User not found"));

        var subscriptions = user.getSubscriptions().stream()
            .map(sub -> sub.getSubscribedTo().getUsername())
            .toList();

        return new UserResponse(
            user.getId(),
            user.getFirstName(),
            user.getLastName(),
            user.getUsername(),
            user.getEmail(),
            user.getAvatar(),
            user.getRole().name(),
            user.isBanned(),
            subscriptions,
            postService.getPostsRespByUserId(user.getId())
        );
    }

    @Override
    public List<User> getUsers() {
        return (List<User>) userRepository.findAll();
    }

    @Override
    public User getUser(Long id) {
        return userRepository.findById(id).orElseThrow(()  -> new RuntimeException("User not found"));
    }

    @Override
    public User getUserByUsername(String username) {
        return userRepository.findByUsername(username).orElseThrow(() -> new RuntimeException("User not found"));
    }
    @Override
    public User updateUser(User user, Long userId) {
        return userRepository.save(user);
    }

    @Override
    @Transactional
    public boolean deleteUser(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));

        // 1. Delete all reports (where user is reporter or reported user)
        reportRepository.deleteByUser(user);

        // 2. Delete all notifications (where user is recipient or actor)
        notificationRepository.deleteByUser(user);

        // 3. Delete all comments by this user
        commentRepository.deleteByAuthor(user);

        // 4. Delete all subscriptions (both as follower and following)
        subscriptionRepository.deleteByUser(user);

        // 5. Remove user from all liked posts
        for (Post post : user.getLikedPosts()) {
            post.getLikedBy().remove(user);
        }

        // 6. Delete all posts by this user (this will cascade delete comments and likes on those posts)
        List<Post> userPosts = postRepository.findAllByAuthorId(userId);
        for (Post post : userPosts) {
            // Delete notifications for this post first
            notificationRepository.deleteByPost(post);
            postRepository.delete(post);
        }

        // 7. Finally, delete the user
        userRepository.delete(user);
        return true;
    }

    @Override
    public boolean banUser(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));
        user.setBanned(true);
        userRepository.save(user);
        return true;
    }

    @Override
    public boolean unbanUser(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));
        user.setBanned(false);
        userRepository.save(user);
        return true;
    }


    public User getUserByEmail(String email){
        return userRepository.findByEmail(email).orElseThrow();
    }

    private Optional<User> findUserByEmailOrUsername(String identifier) {
        // First try to find by email
        Optional<User> userByEmail = userRepository.findByEmail(identifier);
        if (userByEmail.isPresent()) {
            return userByEmail;
        }

        // If not found by email, try by username
        return userRepository.findByUsername(identifier);
    }

    @Override
    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));

        // Validate current password if changing password
        if (request.getNewPassword() != null && !request.getNewPassword().isEmpty()) {
            if (request.getCurrentPassword() == null || request.getCurrentPassword().isEmpty()) {
                throw new RuntimeException("Current password is required to change password");
            }
            if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
                throw new RuntimeException("Current password is incorrect");
            }
            user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        }

        // Check if username is unique (if changed)
        if (request.getUsername() != null && !request.getUsername().equals(user.getUsername())) {
            if (userRepository.findByUsername(request.getUsername()).isPresent()) {
                throw new RuntimeException("Username is already taken");
            }
            user.setUsername(request.getUsername());
        }

        // Check if email is unique (if changed)
        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            if (userRepository.findByEmail(request.getEmail()).isPresent()) {
                throw new RuntimeException("Email is already taken");
            }
            user.setEmail(request.getEmail());
        }

        // Update other fields
        if (request.getFirstName() != null && !request.getFirstName().isEmpty()) {
            user.setFirstName(request.getFirstName());
        }

        if (request.getLastName() != null && !request.getLastName().isEmpty()) {
            user.setLastName(request.getLastName());
        }

        if (request.getAvatar() != null) {
            user.setAvatar(request.getAvatar());
        }

        User updatedUser = userRepository.save(user);

        return new UserResponse(
            updatedUser.getId(),
            updatedUser.getFirstName(),
            updatedUser.getLastName(),
            updatedUser.getUsername(),
            updatedUser.getEmail(),
            updatedUser.getAvatar(),
            updatedUser.getRole().name(),
            updatedUser.isBanned(),
            updatedUser.getSubscriptions().stream()
                .map(sub -> sub.getSubscribedTo().getUsername())
                .toList(),
            postService.getPostsRespByUserId(updatedUser.getId())
        );
    }
}
