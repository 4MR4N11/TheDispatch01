package _blog.blog.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import _blog.blog.entity.Post;
import _blog.blog.entity.PostReport;
import _blog.blog.entity.User;
import _blog.blog.enums.ReportStatus;

@Repository
public interface PostReportRepository extends JpaRepository<PostReport, Long> {
    
    // Find reports by status
    List<PostReport> findByStatus(ReportStatus status);
    Page<PostReport> findByStatus(ReportStatus status, Pageable pageable);
    
    // Find reports by reporter
    List<PostReport> findByReporter(User reporter);
    
    // Find reports by reported post
    List<PostReport> findByReportedPost(Post reportedPost);
    
    // Check if user already reported this post
    boolean existsByReporterAndReportedPost(User reporter, Post reportedPost);
    
    // Count reports by status
    long countByStatus(ReportStatus status);
    
    // Find pending reports
    @Query("SELECT pr FROM PostReport pr WHERE pr.status = 'PENDING' ORDER BY pr.createdAt ASC")
    List<PostReport> findPendingReportsOrderByCreatedAt();
    
    // Find all reports with pagination
    @Query("SELECT pr FROM PostReport pr ORDER BY pr.createdAt DESC")
    Page<PostReport> findAllOrderByCreatedAtDesc(Pageable pageable);
    
    // Find reports by post author (to see reports against their posts)
    @Query("SELECT pr FROM PostReport pr WHERE pr.reportedPost.author = :author ORDER BY pr.createdAt DESC")
    List<PostReport> findByPostAuthor(@Param("author") User author);
}