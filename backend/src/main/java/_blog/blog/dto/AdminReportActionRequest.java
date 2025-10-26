package _blog.blog.dto;

import _blog.blog.enums.ReportAction;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AdminReportActionRequest {
    
    @NotNull(message = "Action is required")
    private ReportAction action; // DISMISS, WARN_USER, DELETE_POST, BAN_USER
    
    @Size(max = 1000, message = "Response cannot exceed 1000 characters")
    private String adminResponse;
}