package _blog.blog.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import _blog.blog.dto.UpdateProfileRequest;
import _blog.blog.dto.UserResponse;
import _blog.blog.entity.User;
import _blog.blog.enums.Role;
import _blog.blog.service.PostService;
import _blog.blog.service.UserService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/users")
public class UserController {
    private final UserService userService;
    private final PostService postService;

    public UserController(UserService userService, PostService postService) {
        this.userService = userService;
        this.postService = postService;
    }

    @GetMapping
    public List<UserResponse> getUsers(Authentication auth) {
        User currentUser = auth != null ? userService.getUserByUsername(auth.getName()) : null;
        boolean isAdmin = currentUser != null && currentUser.getRole() == Role.ADMIN;

        List<User> users = userService.getUsers();
        List<UserResponse> usersResp = new ArrayList<>();
        for (User u : users) {
            // Only expose email to the user themselves or admins
            String email = (isAdmin || (currentUser != null && currentUser.getId().equals(u.getId())))
                ? u.getEmail()
                : null;

            usersResp.add(
                new UserResponse(
                    u.getId(),
                    u.getFirstName(),
                    u.getLastName(),
                    u.getUsername(),
                    email,
                    u.getAvatar(),
                    u.getRole().toString(),
                    u.isBanned(),
                    u.getSubscriptions().stream()
                        .map(sub -> sub.getSubscribedTo().getUsername())
                        .toList(),
                    postService.getPostsRespByUserId(u.getId()))
            );
        }
        return usersResp;
    }

    @GetMapping("/username/{username}")
    public UserResponse getUserByUsername(@PathVariable String username, Authentication auth) {
        User user = userService.getUserByUsername(username);
        User currentUser = auth != null ? userService.getUserByUsername(auth.getName()) : null;
        boolean isAdmin = currentUser != null && currentUser.getRole() == Role.ADMIN;

        // Only expose email to the user themselves or admins
        String email = (isAdmin || (currentUser != null && currentUser.getUsername().equals(username)))
            ? user.getEmail()
            : null;

        UserResponse userResponse = new UserResponse(
            user.getId(),
            user.getFirstName(),
            user.getLastName(),
            user.getUsername(),
            email,
            user.getAvatar(),
            user.getRole().toString(),
            user.isBanned(),
            user.getSubscriptions().stream()
                .map(sub -> sub.getSubscribedTo().getUsername())
                .toList(),
            postService.getPostsRespByUserId(user.getId()));
        return userResponse;
    }

    @GetMapping("/{id}")
    public UserResponse getUser(@PathVariable Long id, Authentication auth) {
        User user = userService.getUser(id);
        User currentUser = auth != null ? userService.getUserByUsername(auth.getName()) : null;
        boolean isAdmin = currentUser != null && currentUser.getRole() == Role.ADMIN;

        // Only expose email to the user themselves or admins
        String email = (isAdmin || (currentUser != null && currentUser.getId().equals(id)))
            ? user.getEmail()
            : null;

        UserResponse userResponse = new UserResponse(
            user.getId(),
            user.getFirstName(),
            user.getLastName(),
            user.getUsername(),
            email,
            user.getAvatar(),
            user.getRole().toString(),
            user.isBanned(),
            user.getSubscriptions().stream()
                .map(sub -> sub.getSubscribedTo().getUsername())
                .toList(),
            postService.getPostsRespByUserId(user.getId()));
        return userResponse;
    }

    @GetMapping("/me")
    public UserResponse getMe(Authentication auth) {
        User user = userService.getUserByUsername(auth.getName());
        UserResponse userResponse = new UserResponse(
            user.getId(),
            user.getFirstName(),
            user.getLastName(),
            user.getUsername(),
            user.getEmail(),
            user.getAvatar(),
            user.getRole().toString(),
            user.isBanned(),
            user.getSubscriptions().stream()
                .map(sub -> sub.getSubscribedTo().getUsername())
                .toList(),
            postService.getPostsRespByUserId(user.getId()));
        return userResponse;
    }

    @PostMapping("/delete/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> deleteUser(@PathVariable Long id) {
        if (userService.deleteUser(id)) {
            return ResponseEntity.ok(Map.of("message", "User deleted successfully!"));
        } else {
            return ResponseEntity.status(404).body(Map.of("message", "User not found!"));
        }
    }

    @PostMapping("/ban/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> banUser(@PathVariable Long id) {
        if (userService.banUser(id)) {
            return ResponseEntity.ok(Map.of("message", "User banned successfully!"));
        } else {
            return ResponseEntity.status(404).body(Map.of("message", "User not found!"));
        }
    }

    @PostMapping("/unban/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> unbanUser(@PathVariable Long id) {
        if (userService.unbanUser(id)) {
            return ResponseEntity.ok(Map.of("message", "User unbanned successfully!"));
        } else {
            return ResponseEntity.status(404).body(Map.of("message", "User not found!"));
        }
    }

    @PutMapping("/update-profile")
    public ResponseEntity<?> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            Authentication auth) {
        try {
            User user = userService.getUserByUsername(auth.getName());
            UserResponse updatedUser = userService.updateProfile(user.getId(), request);
            return ResponseEntity.ok(updatedUser);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
