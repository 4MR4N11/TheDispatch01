package _blog.blog.service;

import java.util.List;

import _blog.blog.dto.LoginRequest;
import _blog.blog.dto.RegisterRequest;
import _blog.blog.dto.UpdateProfileRequest;
import _blog.blog.dto.UserResponse;
import _blog.blog.entity.User;

public interface  UserService {
    User register(RegisterRequest request);
    User authenticate(LoginRequest input);
    UserResponse getCurrentUser(String username);
    List<User> getUsers();
    User getUserByUsername(String username);
    User getUser(Long id);
    User updateUser(User user, Long userId);
    UserResponse updateProfile(Long userId, UpdateProfileRequest request);
    boolean deleteUser(Long userId);
    boolean banUser(Long userId);
    boolean unbanUser(Long userId);
}
