package _blog.blog.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import _blog.blog.dto.AdminReportStatusRequest;
import _blog.blog.dto.ReportResponse;
import _blog.blog.entity.Post;
import _blog.blog.entity.Report;
import _blog.blog.entity.User;
import _blog.blog.enums.ReportStatus;
import _blog.blog.enums.ReportType;
import _blog.blog.exception.BadRequestException;
import _blog.blog.exception.ResourceNotFoundException;
import _blog.blog.repository.PostRepository;
import _blog.blog.repository.ReportRepository;
import _blog.blog.repository.UserRepository;

@Service
@Transactional
public class ReportServiceImpl implements ReportService {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final PostRepository postRepository;

    public ReportServiceImpl(ReportRepository reportRepository,
                           UserRepository userRepository,
                           PostRepository postRepository) {
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
        this.postRepository = postRepository;
    }

    @Override
    public Report reportUser(Long reporterId, Long reportedUserId, String reason) {
        User reporter = userRepository.findById(reporterId)
                .orElseThrow(() -> new ResourceNotFoundException("User", reporterId));
        User reportedUser = userRepository.findById(reportedUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", reportedUserId));

        if (reporterId.equals(reportedUserId)) {
            throw new BadRequestException("You cannot report yourself");
        }

        if (reportRepository.existsByReporterAndReportedUser(reporter, reportedUser)) {
            throw new BadRequestException("You have already reported this user");
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
                .orElseThrow(() -> new ResourceNotFoundException("User", reporterId));
        Post reportedPost = postRepository.findById(reportedPostId)
                .orElseThrow(() -> new ResourceNotFoundException("Post", reportedPostId));

        if (reportedPost.getAuthor().getId().equals(reporterId)) {
            throw new BadRequestException("You cannot report your own post");
        }

        if (reportRepository.existsByReporterAndReportedPostId(reporter, reportedPostId)) {
            throw new BadRequestException("You have already reported this post");
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
    @Transactional(readOnly = true)
    public List<ReportResponse> getUserReports(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

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
    @Transactional
    public Report handleStatusReport(Long reportId, Long adminId, AdminReportStatusRequest request) {
        int updated = reportRepository.updateStatus(
                reportId,
                request.getAction(),
                adminId,
                request.getAdminResponse()
        );

        if (updated == 0) {
            throw new ResourceNotFoundException("Report", reportId);
        }

        return reportRepository.findById(reportId).orElseThrow();
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
    public boolean hasUserAlreadyReportedUser(Long reporterId, Long reportedUserId) {
        User reporter = userRepository.findById(reporterId)
                .orElseThrow(() -> new ResourceNotFoundException("User", reporterId));
        User reportedUser = userRepository.findById(reportedUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", reportedUserId));

        return reportRepository.existsByReporterAndReportedUser(reporter, reportedUser);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasUserAlreadyReportedPost(Long reporterId, Long postId) {
        User reporter = userRepository.findById(reporterId)
                .orElseThrow(() -> new ResourceNotFoundException("User", reporterId));

        return reportRepository.existsByReporterAndReportedPostId(reporter, postId);
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
}