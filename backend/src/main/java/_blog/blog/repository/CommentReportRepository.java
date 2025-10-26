package _blog.blog.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import _blog.blog.entity.CommentReport;

public interface CommentReportRepository extends JpaRepository<CommentReport, Long>{
    
}
