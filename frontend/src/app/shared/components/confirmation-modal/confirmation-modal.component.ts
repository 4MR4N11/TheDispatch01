import { Component, inject } from '@angular/core';

import { ConfirmationModalService } from '../../services/confirmation-modal.service';

@Component({
  selector: 'app-confirmation-modal',
  standalone: true,
  imports: [],
  templateUrl: './confirmation-modal.component.html',
  styleUrl: './confirmation-modal.component.css'
})
export class ConfirmationModalComponent {
  protected readonly modalService = inject(ConfirmationModalService);

  protected confirm() {
    this.modalService.confirm();
  }

  protected cancel() {
    this.modalService.cancel();
  }
}
