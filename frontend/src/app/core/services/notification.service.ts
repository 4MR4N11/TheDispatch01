import { Injectable, signal } from '@angular/core';

export interface Notification {
  id: number;
  message: string;
  type: 'success' | 'error' | 'info' | 'warning';
  duration?: number;
}

@Injectable({
  providedIn: 'root'
})
export class NotificationService {
  private readonly notifications = signal<Notification[]>([]);
  readonly notifications$ = this.notifications.asReadonly();

  private idCounter = 0;

  show(message: string, type: Notification['type'] = 'info', duration: number = 5000) {
    const id = this.idCounter++;
    const notification: Notification = { id, message, type, duration };

    this.notifications.update(n => [...n, notification]);

    if (duration > 0) {
      setTimeout(() => this.dismiss(id), duration);
    }
  }

  success(message: string, duration?: number) {
    this.show(message, 'success', duration);
  }

  error(message: string, duration?: number) {
    this.show(message, 'error', duration);
  }

  warning(message: string, duration?: number) {
    this.show(message, 'warning', duration);
  }

  info(message: string, duration?: number) {
    this.show(message, 'info', duration);
  }

  dismiss(id: number) {
    this.notifications.update(n => n.filter(notification => notification.id !== id));
  }

  clear() {
    this.notifications.set([]);
  }
}