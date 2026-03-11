import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Observable, switchMap, startWith, of } from 'rxjs';
import { Printer, PrinterStatusUpdate } from '../core/models/printer.model';
import { PrinterService } from '../core/services/printer.service';
import { WebSocketService } from '../core/services/websocket.service';
import { PrinterCardComponent } from './printer-card/printer-card.component';

/**
 * Main dashboard — fetches the printer list once, then subscribes to
 * live status updates over WebSocket for each printer.
 */
@Component({
  selector: 'ph-dashboard',
  standalone: true,
  imports: [CommonModule, PrinterCardComponent],
  templateUrl: './dashboard.component.html'
})
export class DashboardComponent implements OnInit {

  private printerService = inject(PrinterService);
  private wsService = inject(WebSocketService);

  printers: Printer[] = [];
  // Map of printerId → latest status update (from WebSocket)
  statusMap = new Map<string, PrinterStatusUpdate>();

  ngOnInit(): void {
    this.printerService.getAll().subscribe(printers => {
      this.printers = printers;
      // Subscribe to live updates for each printer
      printers.forEach(p => {
        this.wsService.watchPrinter(p.id).subscribe(update => {
          this.statusMap.set(update.printerId, update);
        });
      });
    });
  }

  onPause(printerId: string): void {
    this.printerService.pause(printerId).subscribe();
  }

  onResume(printerId: string): void {
    this.printerService.resume(printerId).subscribe();
  }

  onCancel(printerId: string): void {
    this.printerService.cancel(printerId).subscribe();
  }
}
