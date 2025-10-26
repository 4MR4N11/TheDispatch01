package _blog.blog.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import _blog.blog.dto.AdminReportActionRequest;
import _blog.blog.dto.AdminReportStatusRequest;
import _blog.blog.dto.PostReportResponse;
import _blog.blog.dto.ReportRequest;
import _blog.blog.dto.ReportResponse;
import _blog.blog.entity.User;
import _blog.blog.enums.ReportStatus;
import _blog.blog.enums.ReportType;
import _blog.blog.service.ReportService;
import _blog.blog.service.UserService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/reports")
public class ReportController {
    
    private final ReportService reportService;
    private final UserService userService;
    
    public ReportController(ReportService reportService, UserService userService) {
        this.reportService = reportService;
        this.userService = userService;
    }

    // User reporting functionality
    @PostMapping("/user/{userId}")
    public ResponseEntity<String> reportUser(
            @PathVariable Long userId,
            @RequestBody @Valid ReportRequest request,
            Authentication auth
    ) {
        User reporter = userService.getUserByUsername(auth.getName());
        
        try {
            reportService.reportUser(reporter.getId(), userId, request.getReason());
            return ResponseEntity.ok("User reported successfully");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/post/{postId}")
    public ResponseEntity<String> reportPost(
            @PathVariable Long postId,
            @RequestBody @Valid ReportRequest request,
            Authentication auth
    ) {
        User reporter = userService.getUserByUsername(auth.getName());
        
        try {
            reportService.reportPost(reporter.getId(), postId, request.getReason());
            return ResponseEntity.ok("Post reported successfully");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Alternative endpoint for PostReport entity (if you prefer to use the existing entity)
    @PostMapping("/post-report/{postId}")
    public ResponseEntity<String> createPostReport(
            @PathVariable Long postId,
            @RequestBody @Valid ReportRequest request,
            Authentication auth
    ) {
        User reporter = userService.getUserByUsername(auth.getName());
        
        try {
            reportService.createPostReport(reporter.getId(), postId, request.getReason());
            return ResponseEntity.ok("Post reported successfully");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Get user's own reports
    @GetMapping("/my-reports")
    public ResponseEntity<List<ReportResponse>> getMyReports(Authentication auth) {
        User user = userService.getUserByUsername(auth.getName());
        List<ReportResponse> reports = reportService.getUserReports(user.getId());
        return ResponseEntity.ok(reports);
    }

    // Check if user already reported something
    @GetMapping("/check/user/{userId}")
    public ResponseEntity<Boolean> checkUserReport(
            @PathVariable Long userId,
            Authentication auth
    ) {
        User reporter = userService.getUserByUsername(auth.getName());
        boolean hasReported = reportService.hasUserAlreadyReportedUser(reporter.getId(), userId);
        return ResponseEntity.ok(hasReported);
    }

    @GetMapping("/check/post/{postId}")
    public ResponseEntity<Boolean> checkPostReport(
            @PathVariable Long postId,
            Authentication auth
    ) {
        User reporter = userService.getUserByUsername(auth.getName());
        boolean hasReported = reportService.hasUserAlreadyReportedPost(reporter.getId(), postId);
        return ResponseEntity.ok(hasReported);
    }

    // Admin endpoints - All reports management
    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<ReportResponse>> getAllReports(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<ReportResponse> reports = reportService.getAllReports(pageable);
        return ResponseEntity.ok(reports);
    }

    @GetMapping("/admin/status/{status}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<ReportResponse>> getReportsByStatus(
            @PathVariable ReportStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<ReportResponse> reports = reportService.getReportsByStatus(status, pageable);
        return ResponseEntity.ok(reports);
    }

    @GetMapping("/admin/type/{type}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<ReportResponse>> getReportsByType(
            @PathVariable ReportType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<ReportResponse> reports = reportService.getReportsByType(type, pageable);
        return ResponseEntity.ok(reports);
    }

    // Admin endpoints - PostReports management
    @GetMapping("/admin/post-reports")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<PostReportResponse>> getAllPostReports(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<PostReportResponse> reports = reportService.getAllPostReports(pageable);
        return ResponseEntity.ok(reports);
    }

    @GetMapping("/admin/post-reports/status/{status}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<PostReportResponse>> getPostReportsByStatus(
            @PathVariable ReportStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<PostReportResponse> reports = reportService.getPostReportsByStatus(status, pageable);
        return ResponseEntity.ok(reports);
    }

    // Admin actions
    @PutMapping("/admin/handle/{reportId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> handleStatusReport(
            @PathVariable Long reportId,
            @RequestBody @Valid AdminReportStatusRequest request,
            Authentication auth
    ) {
        User admin = userService.getUserByUsername(auth.getName());
        
        try {
            reportService.handleStatusReport(reportId, admin.getId(), request);
            return ResponseEntity.ok("Report handled successfully");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/admin/handle-post-report/{postReportId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> handlePostReport(
            @PathVariable Long postReportId,
            @RequestBody @Valid AdminReportActionRequest request,
            Authentication auth
    ) {
        User admin = userService.getUserByUsername(auth.getName());
        
        try {
            reportService.handlePostReport(postReportId, admin.getId(), request);
            return ResponseEntity.ok("Post report handled successfully");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Statistics endpoints
    @GetMapping("/admin/stats/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Long> getPendingReportsCount() {
        long count = reportService.getPendingReportsCount();
        return ResponseEntity.ok(count);
    }

    @GetMapping("/admin/stats/total")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Long> getTotalReportsCount() {
        long count = reportService.getTotalReportsCount();
        return ResponseEntity.ok(count);
    }

    @GetMapping("/admin/stats/post-reports/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Long> getPendingPostReportsCount() {
        long count = reportService.getPendingPostReportsCount();
        return ResponseEntity.ok(count);
    }

    @GetMapping("/admin/stats/post-reports/total")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Long> getTotalPostReportsCount() {
        long count = reportService.getTotalPostReportsCount();
        return ResponseEntity.ok(count);
    }
}