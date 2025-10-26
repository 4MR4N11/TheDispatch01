package _blog.blog.dto;

import _blog.blog.enums.ReportStatus;
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

public class AdminReportStatusRequest {
    @NotNull(message = "Action is required")
    private ReportStatus action;
    
    @Size(max = 1000, message = "Response cannot exceed 1000 characters")
    private String adminResponse;
}
