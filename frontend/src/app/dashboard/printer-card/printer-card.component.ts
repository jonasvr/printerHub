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
  @Output() edit   = new EventEmitter<void>();
  @Output() logs   = new EventEmitter<void>();

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
    return this.formatMinutes(this.status?.remainingMinutes ?? -1);
  }

  get totalLabel(): string {
    const remaining = this.status?.remainingMinutes ?? -1;
    const progress = this.status?.progressPercent ?? 0;
    if (remaining < 0 || progress <= 0 || progress >= 100) return '—';
    const total = Math.round(remaining * 100 / (100 - progress));
    return this.formatMinutes(total);
  }

  get elapsedLabel(): string {
    const remaining = this.status?.remainingMinutes ?? -1;
    const progress = this.status?.progressPercent ?? 0;
    if (remaining < 0 || progress <= 0 || progress >= 100) return '—';
    const elapsed = Math.round(remaining * progress / (100 - progress));
    return this.formatMinutes(elapsed);
  }

  private formatMinutes(mins: number): string {
    if (mins < 0) return '—';
    if (mins < 60) return `${mins}m`;
    return `${Math.floor(mins / 60)}h ${mins % 60}m`;
  }

  get hasProgressData(): boolean {
    return (this.status?.progressPercent ?? -1) >= 0;
  }

  get isWarmingUp(): boolean {
    return (this.status?.state ?? this.printer.state) === 'IDLE'
        && (this.status?.nozzleTempTarget ?? 0) > 0;
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
