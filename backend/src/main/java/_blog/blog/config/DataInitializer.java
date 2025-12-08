package _blog.blog.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import _blog.blog.entity.Comment;
import _blog.blog.entity.Post;
import _blog.blog.entity.User;
import _blog.blog.enums.Role;
import _blog.blog.repository.CommentRepository;
import _blog.blog.repository.PostRepository;
import _blog.blog.repository.UserRepository;

@Configuration
public class DataInitializer {

    private static final Random random = new Random();

    @Bean
    CommandLineRunner initData(UserRepository userRepository,
                               PostRepository postRepository,
                               CommentRepository commentRepository,
                               PasswordEncoder passwordEncoder) {
        return args -> {
            // --- 1️⃣ Create Admin User ---
            String adminUsername = "admin";
            if (userRepository.findByUsername(adminUsername).isEmpty()) {
                User admin = User.builder()
                        .firstName("Admin")
                        .lastName("User")
                        .username(adminUsername)
                        .email("admin@example.com")
                        .password(passwordEncoder.encode("Admin123@"))
                        .role(Role.ADMIN)
                        .build();
                userRepository.save(admin);
                System.out.println("✅ Admin user created");
            }

            // --- 2️⃣ Create 10 mock users ---
            List<User> users = new ArrayList<>();
            IntStream.rangeClosed(1, 10).forEach(i -> {
                String username = "user" + i;
                if (userRepository.findByUsername(username).isEmpty()) {
                    User user = User.builder()
                            .firstName("User" + i)
                            .lastName("Test")
                            .username(username)
                            .email("user" + i + "@example.com")
                            .password(passwordEncoder.encode("Password123!"))
                            .role(Role.USER)
                            .build();
                    userRepository.save(user);
                    users.add(user);
                } else {
                    users.add(userRepository.findByUsername(username).get());
                }
            });
            System.out.println("✅ 10 mock users created");

            // --- 3️⃣ Create 20 mock posts ---
            List<Post> posts = new ArrayList<>();
            IntStream.rangeClosed(1, 20).forEach(i -> {
                User author = users.get(random.nextInt(users.size()));
                Post post = Post.builder()
                        .title("Sample Post " + i)
                        .content("This is the content of sample post number " + i + ".")
                        .author(author)
                        .hidden(false)
                        .build();
                postRepository.save(post);
                posts.add(post);
            });
            System.out.println("✅ 20 mock posts created");

            // --- 4️⃣ Create comments for each post ---
            posts.forEach(post -> {
                int commentsCount = random.nextInt(3) + 1; // 1-3 comments per post
                for (int j = 0; j < commentsCount; j++) {
                    User author = users.get(random.nextInt(users.size()));
                    Comment comment = Comment.builder()
                            .author(author)
                            .post(post)
                            .content("This is a comment by " + author.getUsername() + " on post " + post.getId())
                            .build();
                    commentRepository.save(comment);
                }
            });
            System.out.println("✅ Comments added to posts");
        };
    }
}
