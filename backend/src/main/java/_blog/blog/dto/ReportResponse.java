package _blog.blog.dto;

import java.util.Date;

import _blog.blog.enums.ReportStatus;
import _blog.blog.enums.ReportType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReportResponse {
    private Long id;
    private String reporterUsername;
    private ReportType type;
    private String reason;
    private ReportStatus status;
    private String reportedUsername; // for user reports
    private String reportedPostTitle; // for post reports
    private Long reportedPostId; // for post reports
    private String handledByAdminUsername;
    private String adminResponse;
    private Date createdAt;
    private Date updatedAt;
    private Date handledAt;
}