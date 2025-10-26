package _blog.blog.mapper;

import org.springframework.security.crypto.password.PasswordEncoder;

import _blog.blog.dto.RegisterRequest;
import _blog.blog.entity.User;
import _blog.blog.enums.Role;

public class UserMapper {
    
    public static User toEntity(RegisterRequest request, PasswordEncoder passwordEncoder) {
        return User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName()) 
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .avatar(request.getAvatar())
                .role(Role.USER)
                .build();
    }
}