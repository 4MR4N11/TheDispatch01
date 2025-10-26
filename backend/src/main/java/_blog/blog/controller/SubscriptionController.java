package _blog.blog.controller;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import _blog.blog.dto.UserResponse;
import _blog.blog.entity.User;
import _blog.blog.service.PostService;
import _blog.blog.service.SubscriptionService;
import _blog.blog.service.UserService;

@RestController
@RequestMapping("/subscriptions")
public class SubscriptionController {
    private final SubscriptionService subscriptionService;
    private final UserService userService;
    private final PostService postService;

    public SubscriptionController(PostService postService, SubscriptionService subscriptionService, UserService userService) {
        this.subscriptionService = subscriptionService;
        this.userService = userService;
        this.postService = postService;
    }

    @PostMapping("/subscribe/{targetId}")
    public ResponseEntity<String> subscribe(
            @PathVariable Long targetId,
            Authentication auth
    ) {
        User user = userService.getUserByUsername(auth.getName()); // or fetch via your UserDetails impl
        subscriptionService.subscribe(user.getId(), targetId);
        return ResponseEntity.ok("Subscribed successfully");
    }

    @PostMapping("/unsubscribe/{targetId}")
    public ResponseEntity<String> unsubscribe(
            @PathVariable Long targetId,
            Authentication auth
    ) {
        User user = userService.getUserByUsername(auth.getName());
        subscriptionService.unsubscribe(user.getId(), targetId);
        return ResponseEntity.ok("Unsubscribed successfully");
    }

    @GetMapping("/my-subscriptions")
    public ResponseEntity<List<UserResponse>> getSubscriptions(
            Authentication auth
    ) {
        User user = userService.getUserByUsername(auth.getName());
        List<UserResponse> subs = new ArrayList<>();
        for (User u : subscriptionService.getSubscriptions(user.getId())) {
            subs.add(new UserResponse(
            u.getId(),
            u.getFirstName(),
            u.getLastName(),
            u.getUsername(),
            u.getEmail(),
            u.getAvatar(),
            u.getRole().toString(),
            u.isBanned(),
            u.getSubscriptions().stream()
                .map(sub -> sub.getSubscribedTo().getUsername())
                .toList(),
            postService.getPostsRespByUserId(u.getId())));
        }
        return ResponseEntity.ok(subs);
    }

    @GetMapping("/my-followers")
    public ResponseEntity<List<UserResponse>> getFollowers(
            Authentication auth
    ) {
        User user = userService.getUserByUsername(auth.getName());
        List<UserResponse> subs = new ArrayList<>();
        for (User u : subscriptionService.getFollowers(user.getId())) {
            subs.add(new UserResponse(
            u.getId(),
            u.getFirstName(),
            u.getLastName(),
            u.getUsername(),
            u.getEmail(),
            u.getAvatar(),
            u.getRole().toString(),
            u.isBanned(),
            u.getSubscriptions().stream()
                .map(sub -> sub.getSubscribedTo().getUsername())
                .toList(),
            postService.getPostsRespByUserId(u.getId())));
        }
        return ResponseEntity.ok(subs);
    }

    @GetMapping("/followers/{username}")
    public ResponseEntity<List<UserResponse>> getFollowersByUsername(
            @PathVariable String username
    ) {
        User user = userService.getUserByUsername(username);
        List<UserResponse> subs = new ArrayList<>();
        for (User u : subscriptionService.getFollowers(user.getId())) {
            subs.add(new UserResponse(
            u.getId(),
            u.getFirstName(),
            u.getLastName(),
            u.getUsername(),
            u.getEmail(),
            u.getAvatar(),
            u.getRole().toString(),
            u.isBanned(),
            u.getSubscriptions().stream()
                .map(sub -> sub.getSubscribedTo().getUsername())
                .toList(),
            postService.getPostsRespByUserId(u.getId())));
        }
        return ResponseEntity.ok(subs);
    }

    @GetMapping("/subscriptions/{username}")
    public ResponseEntity<List<UserResponse>> getSubscriptionsByUsername(
            @PathVariable String username
    ) {
        User user = userService.getUserByUsername(username);
        List<UserResponse> subs = new ArrayList<>();
        for (User u : subscriptionService.getSubscriptions(user.getId())) {
            subs.add(new UserResponse(
            u.getId(),
            u.getFirstName(),
            u.getLastName(),
            u.getUsername(),
            u.getEmail(),
            u.getAvatar(),
            u.getRole().toString(),
            u.isBanned(),
            u.getSubscriptions().stream()
                .map(sub -> sub.getSubscribedTo().getUsername())
                .toList(),
            postService.getPostsRespByUserId(u.getId())));
        }
        return ResponseEntity.ok(subs);
    }
}
