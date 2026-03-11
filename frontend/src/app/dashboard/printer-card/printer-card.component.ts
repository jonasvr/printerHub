import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Printer, PrinterStatusUpdate, PrinterState } from '../../core/models/printer.model';

/**
 * Individual printer card displayed in the dashboard grid.
 *
 * Receives data via @Input() and communicates user actions back
 * to the parent via @Output() EventEmitters.
 *
 * This is the classic "smart parent / dumb child" pattern:
 * - DashboardComponent (smart) owns state and calls services
 * - PrinterCardComponent (dumb) only renders what it's given
 * This makes PrinterCardComponent trivial to test and reuse.
 */
@Component({
  selector: 'ph-printer-card',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './printer-card.component.html'
})
export class PrinterCardComponent {

  @Input({ required: true }) printer!: Printer;
  @Input() status: PrinterStatusUpdate | null = null;

  @Output() pause  = new EventEmitter<void>();
  @Output() resume = new EventEmitter<void>();
  @Output() cancel = new EventEmitter<void>();

  get stateLower(): string {
    const s = this.status?.state ?? this.printer.state;
    return s.toLowerCase();
  }

  get stateLabel(): string {
    return this.status?.state ?? this.printer.state;
  }

  get isPrinting(): boolean {
    return (this.status?.state ?? this.printer.state) === 'PRINTING';
  }

  get isPaused(): boolean {
    return (this.status?.state ?? this.printer.state) === 'PAUSED';
  }

  get progressPercent(): number {
    return this.status?.progressPercent ?? 0;
  }

  get remainingLabel(): string {
    const mins = this.status?.remainingMinutes ?? -1;
    if (mins < 0) return '—';
    if (mins < 60) return `${mins}m`;
    return `${Math.floor(mins / 60)}h ${mins % 60}m`;
  }

  get bedTemp(): string {
    if (!this.status) return '—';
    return `${this.status.bedTempActual}°C / ${this.status.bedTempTarget}°C`;
  }

  get nozzleTemp(): string {
    if (!this.status) return '—';
    return `${this.status.nozzleTempActual}°C / ${this.status.nozzleTempTarget}°C`;
  }
}
