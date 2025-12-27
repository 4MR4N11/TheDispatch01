package _blog.blog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CommentRequest {
    @NotBlank(message = "Comment content cannot be empty")
    @Size(min = 1, max = 5000, message = "Comment must be between 1 and 5000 characters")
    private String content;
}