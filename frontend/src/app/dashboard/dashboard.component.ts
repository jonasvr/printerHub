import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Printer, PrinterStatusUpdate, AmsStatus, HmsAlert } from '../core/models/printer.model';
import { PrinterService } from '../core/services/printer.service';
import { WebSocketService } from '../core/services/websocket.service';
import { SettingsService } from '../core/services/settings.service';
import { CardLayoutService } from '../core/services/card-layout.service';
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
  private cardLayoutService = inject(CardLayoutService);

  printers: Printer[] = [];
  statusMap = new Map<string, PrinterStatusUpdate>();
  showModal = false;
  editingPrinter?: Printer;
  loggingPrinter?: Printer;
  pushallInterval = 60;
  wsConnected = false;
  layout = this.cardLayoutService.layout;
  hmsModalPrinterId: string | null = null;

  readonly intervalOptions = [
    { label: '30 s',  value: 30  },
    { label: '1 min', value: 60  },
    { label: '2 min', value: 120 },
    { label: '5 min', value: 300 },
  ];

  ngOnInit(): void {
    this.cardLayoutService.layout$.subscribe(n => { this.layout = n; });
    this.wsService.connected$.subscribe(c => { this.wsConnected = c; });

    this.settingsService.get().subscribe(s => {
      this.pushallInterval = s.pushallIntervalSeconds;
    });

    this.printerService.getAll().subscribe(printers => {
      this.printers = printers;
      printers.forEach(p => {
        this.wsService.watchPrinter(p.id).subscribe(update => {
          this.statusMap.set(update.printerId, update);
        });
      });
    });
  }

  setLayout(n: number): void { this.cardLayoutService.set(n); }

  onPause(printerId: string): void  { this.printerService.pause(printerId).subscribe(); }
  onResume(printerId: string): void { this.printerService.resume(printerId).subscribe(); }
  onCancel(printerId: string): void { this.printerService.cancel(printerId).subscribe(); }

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

  openEdit(printer: Printer): void  { this.editingPrinter = printer; }
  closeModal(): void { this.showModal = false; this.editingPrinter = undefined; }
  openLogs(printer: Printer): void  { this.loggingPrinter = printer; }
  closeLogs(): void  { this.loggingPrinter = undefined; }

  onPushAllIntervalChange(seconds: number): void {
    this.pushallInterval = seconds;
    this.settingsService.setPushAllInterval(seconds).subscribe();
  }

  // ── Card layout helpers (used by layouts 2 & 3) ──────────────────────────

  st(id: string): PrinterStatusUpdate | null {
    return this.statusMap.get(id) ?? null;
  }

  stateOf(s: PrinterStatusUpdate | null, p: Printer): string {
    return s?.state ?? p.state;
  }

  isPrinting(s: PrinterStatusUpdate | null, p: Printer): boolean {
    return this.stateOf(s, p) === 'PRINTING';
  }

  isPaused(s: PrinterStatusUpdate | null, p: Printer): boolean {
    return this.stateOf(s, p) === 'PAUSED';
  }

  stateColorText(s: PrinterStatusUpdate | null, p: Printer): string {
    switch (this.stateOf(s, p)) {
      case 'PRINTING': return 'text-green-400';
      case 'PAUSED':   return 'text-amber-400';
      case 'ERROR':    return 'text-red-400';
      default:         return 'text-gray-500';
    }
  }

  stateBg(s: PrinterStatusUpdate | null, p: Printer): string {
    switch (this.stateOf(s, p)) {
      case 'PRINTING': return 'bg-green-500';
      case 'PAUSED':   return 'bg-amber-400';
      case 'ERROR':    return 'bg-red-500';
      case 'IDLE':     return 'bg-slate-500';
      default:         return 'bg-slate-700';
    }
  }

  ringStroke(s: PrinterStatusUpdate | null, p: Printer): string {
    switch (this.stateOf(s, p)) {
      case 'PRINTING': return '#22c55e';
      case 'PAUSED':   return '#fbbf24';
      case 'ERROR':    return '#ef4444';
      default:         return '#475569';
    }
  }

  ringOffset(s: PrinterStatusUpdate | null): number {
    return 251.3 * (1 - (s?.progressPercent ?? 0) / 100);
  }

  remainingLabel(s: PrinterStatusUpdate | null): string {
    return this.fmtMins(s?.remainingMinutes ?? -1);
  }

  elapsedLabel(s: PrinterStatusUpdate | null): string {
    const rem = s?.remainingMinutes ?? -1;
    const pct = s?.progressPercent ?? 0;
    if (rem < 0 || pct <= 0 || pct >= 100) return '—';
    return this.fmtMins(Math.round(rem * pct / (100 - pct)));
  }

  private fmtMins(m: number): string {
    if (m < 0) return '—';
    if (m < 60) return `${m}m`;
    return `${Math.floor(m / 60)}h ${m % 60}m`;
  }

  hasAlerts(s: PrinterStatusUpdate | null): boolean {
    return (s?.hmsAlerts?.length ?? 0) > 0;
  }

  alertCount(s: PrinterStatusUpdate | null): number {
    return s?.hmsAlerts?.length ?? 0;
  }

  alerts(s: PrinterStatusUpdate | null): HmsAlert[] {
    return s?.hmsAlerts ?? [];
  }

  amsUnits(s: PrinterStatusUpdate | null): AmsStatus[] {
    return s?.amsList ?? [];
  }

  hmsSeverity(attr: number): string {
    switch ((attr >>> 8) & 0xFF) {
      case 1: return 'Fatal';
      case 2: return 'Serious';
      case 3: return 'Moderate';
      case 4: return 'Info';
      default: return 'Unknown';
    }
  }

  hmsSeverityClass(attr: number): string {
    switch ((attr >>> 8) & 0xFF) {
      case 1: return 'text-red-400';
      case 2: return 'text-orange-400';
      case 3: return 'text-yellow-400';
      case 4: return 'text-blue-400';
      default: return 'text-gray-400';
    }
  }

  hmsHex(v: number): string {
    return '0x' + v.toString(16).toUpperCase().padStart(8, '0');
  }
}
