import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AmsStatus, HmsAlert, Printer, PrinterStatusUpdate, PrinterState } from '../../core/models/printer.model';

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

  @Output() pause = new EventEmitter<void>();
  @Output() resume = new EventEmitter<void>();
  @Output() cancel = new EventEmitter<void>();
  @Output() edit = new EventEmitter<void>();
  @Output() logs = new EventEmitter<void>();

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

   get isIdle(): boolean {
    return (this.status?.state ?? this.printer.state) === 'IDLE';
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
    return (this.status?.bedTempTarget ?? -1) >= 0 && (this.status?.nozzleTempTarget ?? -1) >= 0;
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

  get chamberTemp(): string {
    if (!this.status || !this.status.chamberTempActual) return '—';
    return `${this.status.chamberTempActual}°C`;
  }

  get layerLabel(): string | null {
    if (!this.status || this.status.layerTotal === 0) return null;
    return `${this.status.layerCurrent} / ${this.status.layerTotal}`;
  }

  get speedLabel(): string | null {
    if (!this.status || this.status.speedPercent === 0) return null;
    if (!this.isPrinting && !this.isPaused) return null;
    return `${this.status.speedPercent}%`;
  }

  get hasHmsAlerts(): boolean {
    return (this.status?.hmsAlerts?.length ?? 0) > 0;
  }

  get hmsAlertLabel(): string {
    const count = this.status?.hmsAlerts?.length ?? 0;
    return count === 1 ? '1 printer alert' : `${count} printer alerts`;
  }

  get hmsAlerts(): HmsAlert[] {
    return this.status?.hmsAlerts ?? [];
  }

  get amsUnits(): AmsStatus[] {
    return this.status?.amsList ?? [];
  }

  showHmsModal = false;

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

  hmsHex(value: number): string {
    return '0x' + value.toString(16).toUpperCase().padStart(8, '0');
  }
}
