import { Injectable, inject } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { ConfirmDialogComponent, ConfirmDialogData } from '../components/confirm-dialog/confirm-dialog.component';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class ConfirmationModalService {
  private readonly dialog = inject(MatDialog);

  /**
   * Opens a Material confirmation dialog
   * @param options Dialog configuration
   * @returns Promise that resolves to true if confirmed, false if cancelled
   */
  async open(options: {
    title?: string;
    message: string;
    confirmText?: string;
    cancelText?: string;
  }): Promise<boolean> {
    const dialogRef = this.dialog.open(ConfirmDialogComponent, {
      width: '400px',
      data: options as ConfirmDialogData
    });

    const result = await dialogRef.afterClosed().toPromise();
    return result === true;
  }

  /**
   * Opens a Material confirmation dialog (Observable version)
   * @param options Dialog configuration
   * @returns Observable that emits true if confirmed, false if cancelled
   */
  open$(options: {
    title?: string;
    message: string;
    confirmText?: string;
    cancelText?: string;
  }): Observable<boolean> {
    const dialogRef = this.dialog.open(ConfirmDialogComponent, {
      width: '400px',
      data: options as ConfirmDialogData
    });

    return dialogRef.afterClosed();
  }
}
