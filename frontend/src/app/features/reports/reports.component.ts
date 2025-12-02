import { Component, inject, signal, OnInit } from '@angular/core';

import { Router } from '@angular/router';
import { ApiService } from '../../core/auth/api.service';
import { NotificationService } from '../../core/services/notification.service';
import { ReportResponse } from '../../shared/models/models';

@Component({
  selector: 'app-reports',
  standalone: true,
  imports: [],
  templateUrl: './reports.component.html',
  styleUrl: './reports.component.css'
})
export class ReportsComponent implements OnInit {
  private readonly apiService = inject(ApiService);
  private readonly notificationService = inject(NotificationService);
  private readonly router = inject(Router);

  protected readonly activeTab = signal<'user' | 'post'>('user');
  protected readonly allReports = signal<ReportResponse[]>([]);
  protected readonly loading = signal(true);

  protected readonly userReports = signal<ReportResponse[]>([]);
  protected readonly postReports = signal<ReportResponse[]>([]);

  ngOnInit() {
    this.loadReports();
  }

  private loadReports() {
    this.loading.set(true);
    this.apiService.getMyReports().subscribe({
      next: (reports) => {
        this.allReports.set(reports);
        
        // Separate user and post reports
        const userReps = reports.filter(r => r.type === 'USER_REPORT');
        const postReps = reports.filter(r => r.type === 'POST_REPORT');

        this.userReports.set(userReps);
        this.postReports.set(postReps);
        this.loading.set(false);
      },
      error: () => {
        this.notificationService.error('Failed to load reports');
        this.loading.set(false);
      }
    });
  }

  setActiveTab(tab: 'user' | 'post') {
    this.activeTab.set(tab);
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

  formatDate(date: string | Date): string {
    return new Date(date).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  }
}
