import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Printer, PrinterStatusUpdate } from '../core/models/printer.model';
import { PrinterService } from '../core/services/printer.service';
import { WebSocketService } from '../core/services/websocket.service';
import { PrinterCardComponent } from './printer-card/printer-card.component';
import { AddPrinterModalComponent } from './add-printer-modal/add-printer-modal.component';
import { MqttLogsModalComponent } from './mqtt-logs-modal/mqtt-logs-modal.component';

/**
 * Main dashboard — fetches the printer list once, then subscribes to
 * live status updates over WebSocket for each printer.
 */
@Component({
  selector: 'ph-dashboard',
  standalone: true,
  imports: [CommonModule, PrinterCardComponent, AddPrinterModalComponent, MqttLogsModalComponent],
  templateUrl: './dashboard.component.html'
})
export class DashboardComponent implements OnInit {

  private printerService = inject(PrinterService);
  private wsService = inject(WebSocketService);

  printers: Printer[] = [];
  statusMap = new Map<string, PrinterStatusUpdate>();
  showModal = false;
  editingPrinter?: Printer;
  loggingPrinter?: Printer;

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

  onPrinterAdded(printer: Printer): void {
    this.printers = [...this.printers, printer];
    this.wsService.watchPrinter(printer.id).subscribe(update => {
      this.statusMap.set(update.printerId, update);
    });
    this.showModal = false;
  }

  onPrinterUpdated(printer: Printer): void {
    this.printers = this.printers.map(p => p.id === printer.id ? printer : p);
    this.editingPrinter = undefined;
  }

  openEdit(printer: Printer): void {
    this.editingPrinter = printer;
  }

  closeModal(): void {
    this.showModal = false;
    this.editingPrinter = undefined;
  }

  openLogs(printer: Printer): void {
    this.loggingPrinter = printer;
  }

  closeLogs(): void {
    this.loggingPrinter = undefined;
  }
}
