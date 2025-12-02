package _blog.blog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import _blog.blog.validation.NoHtml;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {
    @Size(min=4, max=30, message = "Username must be between 4 and 30 characters")
    private String username;

    @Email(message = "Invalid email format")
    private String email;

    @NoHtml(message = "First name cannot contain HTML")
    @JsonProperty("firstname")
    private String firstName;

    @NoHtml(message = "Last name cannot contain HTML")
    @JsonProperty("lastname")
    private String lastName;

    private String avatar;

    @Size(min=8, max=50, message = "Password must be between 8 and 50 characters")
    private String newPassword;

    private String currentPassword;
}
