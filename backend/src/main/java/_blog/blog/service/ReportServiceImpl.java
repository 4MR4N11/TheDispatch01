package _blog.blog.service;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import _blog.blog.dto.AdminReportActionRequest;
import _blog.blog.dto.AdminReportStatusRequest;
import _blog.blog.dto.PostReportResponse;
import _blog.blog.dto.ReportResponse;
import _blog.blog.entity.Post;
import _blog.blog.entity.PostReport;
import _blog.blog.entity.Report;
import _blog.blog.entity.User;
import _blog.blog.enums.ReportStatus;
import _blog.blog.enums.ReportType;
import _blog.blog.repository.PostReportRepository;
import _blog.blog.repository.PostRepository;
import _blog.blog.repository.ReportRepository;
import _blog.blog.repository.UserRepository;

@Service
@Transactional
public class ReportServiceImpl implements ReportService {
    
    private final ReportRepository reportRepository;
    private final PostReportRepository postReportRepository;
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    // private final UserService userService;
    // private final PostService postService;
    
    public ReportServiceImpl(ReportRepository reportRepository, 
                           PostReportRepository postReportRepository,
                           UserRepository userRepository,
                           PostRepository postRepository) {
        this.reportRepository = reportRepository;
        this.postReportRepository = postReportRepository;
        this.userRepository = userRepository;
        this.postRepository = postRepository;
    }

    @Override
    public Report reportUser(Long reporterId, Long reportedUserId, String reason) {
        User reporter = userRepository.findById(reporterId)
                .orElseThrow(() -> new RuntimeException("Reporter not found"));
        User reportedUser = userRepository.findById(reportedUserId)
                .orElseThrow(() -> new RuntimeException("Reported user not found"));
        
        if (reporterId.equals(reportedUserId)) {
            throw new RuntimeException("You cannot report yourself");
        }
        
        if (reportRepository.existsByReporterAndReportedUser(reporter, reportedUser)) {
            throw new RuntimeException("You have already reported this user");
        }
        
        Report report = Report.builder()
                .reporter(reporter)
                .reportedUser(reportedUser)
                .type(ReportType.USER_REPORT)
                .reason(reason)
                .status(ReportStatus.PENDING)
                .build();
        
        return reportRepository.save(report);
    }

    @Override
    public Report reportPost(Long reporterId, Long reportedPostId, String reason) {
        User reporter = userRepository.findById(reporterId)
                .orElseThrow(() -> new RuntimeException("Reporter not found"));
        Post reportedPost = postRepository.findById(reportedPostId)
                .orElseThrow(() -> new RuntimeException("Reported post not found"));
        
        if (reportedPost.getAuthor().getId().equals(reporterId)) {
            throw new RuntimeException("You cannot report your own post");
        }
        
        if (reportRepository.existsByReporterAndReportedPostId(reporter, reportedPostId)) {
            throw new RuntimeException("You have already reported this post");
        }
        
        Report report = Report.builder()
                .reporter(reporter)
                .reportedPost(reportedPost)
                .type(ReportType.POST_REPORT)
                .reason(reason)
                .status(ReportStatus.PENDING)
                .build();
        
        return reportRepository.save(report);
    }

    @Override
    public PostReport createPostReport(Long reporterId, Long reportedPostId, String reason) {
        User reporter = userRepository.findById(reporterId)
                .orElseThrow(() -> new RuntimeException("Reporter not found"));
        Post reportedPost = postRepository.findById(reportedPostId)
                .orElseThrow(() -> new RuntimeException("Reported post not found"));
        
        if (reportedPost.getAuthor().getId().equals(reporterId)) {
            throw new RuntimeException("You cannot report your own post");
        }
        
        if (postReportRepository.existsByReporterAndReportedPost(reporter, reportedPost)) {
            throw new RuntimeException("You have already reported this post");
        }
        
        PostReport postReport = PostReport.builder()
                .reporter(reporter)
                .reportedPost(reportedPost)
                .reason(reason)
                .status(ReportStatus.PENDING)
                .build();
        
        return postReportRepository.save(postReport);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReportResponse> getUserReports(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        List<Report> reports = reportRepository.findByReporter(user);
        return reports.stream()
                .map(this::mapToReportResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReportResponse> getAllReports(Pageable pageable) {
        Page<Report> reports = reportRepository.findAllOrderByCreatedAtDesc(pageable);
        return reports.map(this::mapToReportResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReportResponse> getReportsByStatus(ReportStatus status, Pageable pageable) {
        Page<Report> reports = reportRepository.findByStatus(status, pageable);
        return reports.map(this::mapToReportResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReportResponse> getReportsByType(ReportType type, Pageable pageable) {
        Page<Report> reports = reportRepository.findByType(type, pageable);
        return reports.map(this::mapToReportResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PostReportResponse> getAllPostReports(Pageable pageable) {
        Page<PostReport> postReports = postReportRepository.findAllOrderByCreatedAtDesc(pageable);
        return postReports.map(this::mapToPostReportResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PostReportResponse> getPostReportsByStatus(ReportStatus status, Pageable pageable) {
        Page<PostReport> postReports = postReportRepository.findByStatus(status, pageable);
        return postReports.map(this::mapToPostReportResponse);
    }

    @Override
    @Transactional
    public Report handleStatusReport(Long reportId, Long adminId, AdminReportStatusRequest request) {
        int updated = reportRepository.updateStatus(
                reportId,
                request.getAction(),      // your enum value
                adminId,
                request.getAdminResponse()
        );

        if (updated == 0) {
            throw new RuntimeException("Failed to update report");
        }

        // Return fresh entity (optional)
        return reportRepository.findById(reportId).orElseThrow();
    }


    @Override
    public Report handleReport(Long reportId, Long adminId, AdminReportActionRequest request) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new RuntimeException("Report not found"));
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("Admin not found"));
        
        // Execute the requested action
        switch (request.getAction()) {
            case DISMISS:
                report.setStatus(ReportStatus.REJECTED);
                break;
            case DELETE_POST:
                if (report.getReportedPost() != null) {
                    // TODO: Implement post deletion
                    report.setStatus(ReportStatus.APPROVED);
                } else {
                    throw new RuntimeException("Cannot delete post: This is not a post report");
                }
                break;
            case BAN_USER:
                if (report.getReportedUser() != null) {
                    // TODO: Implement user banning
                    report.setStatus(ReportStatus.APPROVED);
                } else if (report.getReportedPost() != null) {
                    // Ban the post author
                    // TODO: Implement user banning
                    report.setStatus(ReportStatus.APPROVED);
                }
                break;
            default:
                break;
        }
        
        report.setHandledByAdmin(admin);
        report.setAdminResponse(request.getAdminResponse());
        report.setHandledAt(new Date());
        
        return reportRepository.save(report);
    }

    @Override
    public PostReport handlePostReport(Long postReportId, Long adminId, AdminReportActionRequest request) {
        PostReport postReport = postReportRepository.findById(postReportId)
                .orElseThrow(() -> new RuntimeException("Post report not found"));
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("Admin not found"));
        
        // Execute the requested action
        switch (request.getAction()) {
            case DISMISS:
                postReport.setStatus(ReportStatus.REJECTED);
                break;
            case WARN_USER:
                postReport.setStatus(ReportStatus.APPROVED);
                // TODO: Implement warning system
                break;
            case DELETE_POST:
                // TODO: Implement post deletion
                postReport.setStatus(ReportStatus.APPROVED);
                break;
            case BAN_USER:
                // TODO: Implement user banning
                postReport.setStatus(ReportStatus.APPROVED);
                break;
        }
        
        postReport.setHandledByAdmin(admin);
        postReport.setAdminResponse(request.getAdminResponse());
        postReport.setHandledAt(new Date());
        
        return postReportRepository.save(postReport);
    }

    @Override
    @Transactional(readOnly = true)
    public long getPendingReportsCount() {
        return reportRepository.countByStatus(ReportStatus.PENDING);
    }

    @Override
    @Transactional(readOnly = true)
    public long getTotalReportsCount() {
        return reportRepository.count();
    }

    @Override
    @Transactional(readOnly = true)
    public long getPendingPostReportsCount() {
        return postReportRepository.countByStatus(ReportStatus.PENDING);
    }

    @Override
    @Transactional(readOnly = true)
    public long getTotalPostReportsCount() {
        return postReportRepository.count();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasUserAlreadyReportedUser(Long reporterId, Long reportedUserId) {
        User reporter = userRepository.findById(reporterId)
                .orElseThrow(() -> new RuntimeException("Reporter not found"));
        User reportedUser = userRepository.findById(reportedUserId)
                .orElseThrow(() -> new RuntimeException("Reported user not found"));
        
        return reportRepository.existsByReporterAndReportedUser(reporter, reportedUser);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasUserAlreadyReportedPost(Long reporterId, Long postId) {
        User reporter = userRepository.findById(reporterId)
                .orElseThrow(() -> new RuntimeException("Reporter not found"));
        
        return reportRepository.existsByReporterAndReportedPostId(reporter, postId) ||
               postReportRepository.existsByReporterAndReportedPost(reporter, 
                   postRepository.findById(postId).orElseThrow(() -> new RuntimeException("Post not found")));
    }

    // Helper methods
    private ReportResponse mapToReportResponse(Report report) {
        return new ReportResponse(
            report.getId(),
            report.getReporter().getUsername(),
            report.getType(),
            report.getReason(),
            report.getStatus(),
            report.getReportedUser() != null ? report.getReportedUser().getUsername() : null,
            report.getReportedPost() != null ? report.getReportedPost().getTitle() : null,
            report.getReportedPost() != null ? report.getReportedPost().getId() : null,
            report.getHandledByAdmin() != null ? report.getHandledByAdmin().getUsername() : null,
            report.getAdminResponse(),
            report.getCreatedAt(),
            report.getUpdatedAt(),
            report.getHandledAt()
        );
    }

    private PostReportResponse mapToPostReportResponse(PostReport postReport) {
        return new PostReportResponse(
            postReport.getId(),
            postReport.getReporter().getUsername(),
            postReport.getReportedPost().getTitle(),
            postReport.getReportedPost().getId(),
            postReport.getReportedPost().getAuthor().getUsername(),
            postReport.getReason(),
            postReport.getStatus(),
            postReport.getHandledByAdmin() != null ? postReport.getHandledByAdmin().getUsername() : null,
            postReport.getAdminResponse(),
            postReport.getCreatedAt(),
            postReport.getUpdatedAt(),
            postReport.getHandledAt()
        );
    }
}