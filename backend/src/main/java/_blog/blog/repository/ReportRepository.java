package _blog.blog.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import _blog.blog.entity.Report;
import _blog.blog.entity.User;
import _blog.blog.enums.ReportStatus;
import _blog.blog.enums.ReportType;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {
    
    // Find reports by status
    List<Report> findByStatus(ReportStatus status);
    Page<Report> findByStatus(ReportStatus status, Pageable pageable);
    
    // Find reports by type
    List<Report> findByType(ReportType type);
    Page<Report> findByType(ReportType type, Pageable pageable);
    
    // Find reports by reporter
    List<Report> findByReporter(User reporter);
    
    // Find reports by reported user
    List<Report> findByReportedUser(User reportedUser);
    
    // Check if user already reported another user
    boolean existsByReporterAndReportedUser(User reporter, User reportedUser);
    
    // Check if user already reported a post
    @Query("SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END FROM Report r WHERE r.reporter = :reporter AND r.reportedPost.id = :postId")
    boolean existsByReporterAndReportedPostId(@Param("reporter") User reporter, @Param("postId") Long postId);
    
    // Count reports by status
    long countByStatus(ReportStatus status);
    
    // Count reports by type
    long countByType(ReportType type);
    
    // Find pending reports ordered by creation date
    @Query("SELECT r FROM Report r WHERE r.status = 'PENDING' ORDER BY r.createdAt ASC")
    List<Report> findPendingReportsOrderByCreatedAt();
    
    // Find all reports with pagination and sorting
    @Query("SELECT r FROM Report r ORDER BY r.createdAt DESC")
    Page<Report> findAllOrderByCreatedAtDesc(Pageable pageable);
    
    // Find reports by multiple criteria
    @Query("SELECT r FROM Report r WHERE " +
           "(:status IS NULL OR r.status = :status) AND " +
           "(:type IS NULL OR r.type = :type) " +
           "ORDER BY r.createdAt DESC")
    Page<Report> findReportsByCriteria(@Param("status") ReportStatus status, 
                                      @Param("type") ReportType type, 
                                      Pageable pageable);

    @Modifying
    @Query("UPDATE Report r SET r.status = :status, r.handledByAdmin.id = :adminId, r.adminResponse = :adminResponse, r.handledAt = CURRENT_TIMESTAMP WHERE r.id = :reportId")
    int updateStatus(
       @Param("reportId") Long reportId,
       @Param("status") ReportStatus status,
       @Param("adminId") Long adminId,
       @Param("adminResponse") String adminResponse);

    @Modifying
    @Query("DELETE FROM Report r WHERE r.reporter = :user OR r.reportedUser = :user")
    void deleteByUser(@Param("user") User user);
}