package _blog.blog.dto;

import _blog.blog.validation.NoHtml;
import _blog.blog.validation.SanitizedEditorJs;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PostRequest {
    @NoHtml(message = "Title cannot contain HTML")
    @Size(max = 200, message = "Title must not exceed 200 characters")
    String title;

    @SanitizedEditorJs(message = "Content contains unsafe HTML")
    @NotBlank(message = "Content is required")
    @Size(min = 1, max = 100000, message = "Content must be between 1 and 100000 characters")
    String content;

    @Pattern(regexp = "^(image|video|audio)?$", message = "Media type must be 'image', 'video', 'audio', or empty")
    String media_type;

    @Size(max = 2048, message = "Media URL must not exceed 2048 characters")
    @Pattern(regexp = "^(https?://.*|/uploads/.*)?$", message = "Media URL must be a valid HTTP/HTTPS URL or relative upload path")
    String media_url;
}
