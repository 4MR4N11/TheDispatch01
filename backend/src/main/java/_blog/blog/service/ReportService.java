// ReportService.java
package _blog.blog.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import _blog.blog.dto.AdminReportStatusRequest;
import _blog.blog.dto.ReportResponse;
import _blog.blog.entity.Report;
import _blog.blog.enums.ReportStatus;
import _blog.blog.enums.ReportType;

public interface ReportService {
    
    // User reporting functionality
    Report reportUser(Long reporterId, Long reportedUserId, String reason);
    Report reportPost(Long reporterId, Long reportedPostId, String reason);

    // Get reports for users
    List<ReportResponse> getUserReports(Long userId);
    
    // Admin functionality
    Page<ReportResponse> getAllReports(Pageable pageable);
    Page<ReportResponse> getReportsByStatus(ReportStatus status, Pageable pageable);
    Page<ReportResponse> getReportsByType(ReportType type, Pageable pageable);

    // Admin actions
    Report handleStatusReport(Long reportId, Long adminId, AdminReportStatusRequest request);
    
    // Statistics
    long getPendingReportsCount();
    long getTotalReportsCount();

    // Validation
    boolean hasUserAlreadyReportedUser(Long reporterId, Long reportedUserId);
    boolean hasUserAlreadyReportedPost(Long reporterId, Long postId);
}