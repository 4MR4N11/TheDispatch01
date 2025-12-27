import { Component, inject, signal, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { ApiService } from '../../../core/auth/api.service';
import { NotificationService } from '../../../core/services/notification.service';
import { ReportStatus } from '../../../shared/models/models';

@Component({
  selector: 'app-admin-reports',
  standalone: true,
  imports: [],
  templateUrl: './admin-reports.component.html',
  styleUrl: './admin-reports.component.css'
})
export class AdminReportsComponent implements OnInit {
  private readonly apiService = inject(ApiService);
  private readonly notificationService = inject(NotificationService);
  private readonly router = inject(Router);

  protected readonly reports = signal<any[]>([]);
  protected readonly loading = signal(true);
  protected readonly currentPage = signal(0);
  protected readonly totalPages = signal(0);

  ngOnInit() {
    this.loadReports();
  }

  private loadReports() {
    this.loading.set(true);
    this.apiService.getAllReports(this.currentPage(), 20).subscribe({
      next: (response) => {
        this.reports.set(response.content || response);
        this.totalPages.set(response.totalPages || 1);
        this.loading.set(false);
      },
      error: () => {
        this.notificationService.error('Failed to load reports');
        this.loading.set(false);
      }
    });
  }

  handleReport(reportId: number, action: ReportStatus, adminResponse: string) {
    this.apiService.handleReport(reportId, { action, adminResponse }).subscribe({
      next: () => {
        this.notificationService.success('Report handled successfully');
        this.loadReports();
      },
      error: () => {
        this.notificationService.error('Failed to handle report');
      }
    });
  }

  approveReport(reportId: number) {
    this.handleReport(reportId, 'APPROVED' as ReportStatus, 'Report approved by admin');
  }

  rejectReport(reportId: number) {
    this.handleReport(reportId, 'REJECTED' as ReportStatus, 'Report rejected by admin');
  }

  formatDate(date: string | Date): string {
    return new Date(date).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  }

  getStatusClass(status: string): string {
    switch(status?.toUpperCase()) {
      case 'PENDING': return 'status-pending';
      case 'APPROVED': return 'status-approved';
      case 'REJECTED': return 'status-rejected';
      default: return 'status-pending';
    }
  }

  viewPost(postId: number | undefined) {
    if (postId) {
      this.router.navigate(['/post', postId]);
    }
  }

  viewProfile(username: string | undefined) {
    if (username) {
      this.router.navigate(['/profile', username]);
    }
  }
}
