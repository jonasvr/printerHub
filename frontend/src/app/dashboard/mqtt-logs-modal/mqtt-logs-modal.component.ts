import { Component, Input, Output, EventEmitter, OnInit, OnDestroy, inject, ViewChild, ElementRef, AfterViewChecked } from '@angular/core';
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
export class MqttLogsModalComponent implements OnInit, OnDestroy, AfterViewChecked {

  @Input({ required: true }) printer!: Printer;
  @Output() closed = new EventEmitter<void>();

  @ViewChild('logContainer') logContainer!: ElementRef<HTMLElement>;

  private printerService = inject(PrinterService);
  private wsService      = inject(WebSocketService);
  private wsSub?: Subscription;

  logs: MqttLogEntry[] = [];
  private shouldScrollToBottom = false;

  ngOnInit(): void {
    // Load history first, then subscribe to live updates
    this.printerService.getLogs(this.printer.id).subscribe(history => {
      this.logs = history;
      this.shouldScrollToBottom = true;
    });

    this.wsSub = this.wsService.watchLogs(this.printer.id).subscribe(entry => {
      // Live entries arrive newest-first from the backend,
      // but we prepend here so the list stays newest-at-top in real time
      this.logs = [entry, ...this.logs];
    });
  }

  ngAfterViewChecked(): void {
    if (this.shouldScrollToBottom) {
      this.shouldScrollToBottom = false;
      const el = this.logContainer?.nativeElement;
      if (el) el.scrollTop = el.scrollHeight;
    }
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
