import { Component, Input, Output, EventEmitter, OnInit, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subscription } from 'rxjs';
import { Printer, MqttLogEntry } from '../../core/models/printer.model';
import { PrinterService } from '../../core/services/printer.service';
import { WebSocketService } from '../../core/services/websocket.service';

@Component({
  selector: 'ph-mqtt-logs-modal',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './mqtt-logs-modal.component.html'
})
export class MqttLogsModalComponent implements OnInit, OnDestroy {

  @Input({ required: true }) printer!: Printer;
  @Output() closed = new EventEmitter<void>();

  private printerService = inject(PrinterService);
  private wsService      = inject(WebSocketService);
  private wsSub?: Subscription;

  logs: MqttLogEntry[] = [];
  pushAllOnly = false;

  get visibleLogs(): MqttLogEntry[] {
    if (!this.pushAllOnly) return this.logs;
    return this.logs.filter(e => {
      try { return JSON.parse(e.payload)?.print?.gcode_state !== undefined; }
      catch { return false; }
    });
  }

  ngOnInit(): void {
    this.printerService.getLogs(this.printer.id).subscribe(history => {
      this.logs = history;
    });

    this.wsSub = this.wsService.watchLogs(this.printer.id).subscribe(entry => {
      this.logs = [entry, ...this.logs];
    });
  }

  ngOnDestroy(): void {
    this.wsSub?.unsubscribe();
    this.wsService.stopWatchingLogs(this.printer.id);
  }

  clear(): void {
    this.logs = [];
  }

  formatJson(raw: string): string {
    try {
      return JSON.stringify(JSON.parse(raw), null, 2);
    } catch {
      return raw;
    }
  }

  formatTime(ts: string): string {
    return new Date(ts).toLocaleTimeString();
  }
}
