import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Printer, PrinterStatusUpdate } from '../core/models/printer.model';
import { PrinterService } from '../core/services/printer.service';
import { WebSocketService } from '../core/services/websocket.service';
import { SettingsService } from '../core/services/settings.service';
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

  private printerService  = inject(PrinterService);
  private wsService       = inject(WebSocketService);
  private settingsService = inject(SettingsService);

  printers: Printer[] = [];
  statusMap = new Map<string, PrinterStatusUpdate>();
  showModal = false;
  editingPrinter?: Printer;
  loggingPrinter?: Printer;
  pushallInterval = 60;
  wsConnected = false;

  readonly intervalOptions = [
    { label: '30 s',  value: 30  },
    { label: '1 min', value: 60  },
    { label: '2 min', value: 120 },
    { label: '5 min', value: 300 },
  ];

  ngOnInit(): void {
    this.wsService.connected$.subscribe(c => { this.wsConnected = c; });

    this.settingsService.get().subscribe(s => {
      this.pushallInterval = s.pushallIntervalSeconds;
    });

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

  onPushAllIntervalChange(seconds: number): void {
    this.pushallInterval = seconds;
    this.settingsService.setPushAllInterval(seconds).subscribe();
  }
}
