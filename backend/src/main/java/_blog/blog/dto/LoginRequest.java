package _blog.blog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {
    
    @JsonProperty("usernameOrEmail")
    private String usernameOrEmail;
    
    private String password;
    
    @JsonProperty("username")
    public void setUsername(String username) {
        this.usernameOrEmail = username;
    }

    public String getUsername() {
        return this.usernameOrEmail;
    }
    
    @JsonProperty("email") 
    public void setEmail(String email) {
        this.usernameOrEmail = email;
    }
}