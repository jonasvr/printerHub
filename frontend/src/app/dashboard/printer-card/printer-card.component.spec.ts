import { TestBed } from '@angular/core/testing';
import { PrinterCardComponent } from './printer-card.component';
import { Printer, PrinterState, PrinterStatusUpdate } from '../../core/models/printer.model';

// ── Helpers ───────────────────────────────────────────────────────────────────

function makePrinter(state: PrinterState = 'IDLE'): Printer {
  return {
    id: 'p1',
    name: 'X1C',
    brand: 'BAMBU',
    state,
    active: true,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  };
}

function makeStatus(overrides: Partial<PrinterStatusUpdate> = {}): PrinterStatusUpdate {
  return {
    printerId: 'p1',
    state: 'IDLE',
    progressPercent: 0,
    currentFile: null,
    bedTempActual: 0,
    bedTempTarget: 0,
    nozzleTempActual: 0,
    nozzleTempTarget: 0,
    remainingMinutes: -1,
    timestamp: '2026-01-01T00:00:00Z',
    mqttConnected: true,
    connectionError: null,
    chamberTempActual: 0,
    layerCurrent: 0,
    layerTotal: 0,
    speedPercent: 0,
    hmsAlerts: [],
    amsList: null,
    ...overrides,
  };
}

// ── Suite ─────────────────────────────────────────────────────────────────────

describe('PrinterCardComponent', () => {
  let component: PrinterCardComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PrinterCardComponent],
    }).compileComponents();

    const fixture = TestBed.createComponent(PrinterCardComponent);
    component = fixture.componentInstance;
    component.printer = makePrinter();
  });

  // ── stateLower ──────────────────────────────────────────────────────────────

  it('stateLower returns lowercase state from status', () => {
    component.status = makeStatus({ state: 'PRINTING' });
    expect(component.stateLower).toBe('printing');
  });

  it('stateLower falls back to printer.state when status is null', () => {
    component.printer = makePrinter('OFFLINE');
    component.status = null;
    expect(component.stateLower).toBe('offline');
  });

  // ── state booleans ──────────────────────────────────────────────────────────

  it('isPrinting is true when status state is PRINTING', () => {
    component.status = makeStatus({ state: 'PRINTING' });
    expect(component.isPrinting).toBeTrue();
  });

  it('isPrinting is false when state is IDLE', () => {
    component.status = makeStatus({ state: 'IDLE' });
    expect(component.isPrinting).toBeFalse();
  });

  it('isPaused is true when status state is PAUSED', () => {
    component.status = makeStatus({ state: 'PAUSED' });
    expect(component.isPaused).toBeTrue();
  });

  it('isIdle is true when status state is IDLE', () => {
    component.status = makeStatus({ state: 'IDLE' });
    expect(component.isIdle).toBeTrue();
  });

  // ── progress ────────────────────────────────────────────────────────────────

  it('progressPercent returns value from status', () => {
    component.status = makeStatus({ progressPercent: 47.5 });
    expect(component.progressPercent).toBe(47.5);
  });

  it('progressPercent returns 0 when status is null', () => {
    component.status = null;
    expect(component.progressPercent).toBe(0);
  });

  // ── remainingLabel ──────────────────────────────────────────────────────────

  it('remainingLabel formats minutes under 60', () => {
    component.status = makeStatus({ remainingMinutes: 45 });
    expect(component.remainingLabel).toBe('45m');
  });

  it('remainingLabel formats hours and minutes', () => {
    component.status = makeStatus({ remainingMinutes: 83 });
    expect(component.remainingLabel).toBe('1h 23m');
  });

  it('remainingLabel returns dash when remainingMinutes is -1', () => {
    component.status = makeStatus({ remainingMinutes: -1 });
    expect(component.remainingLabel).toBe('—');
  });

  // ── temperatures ────────────────────────────────────────────────────────────

  it('bedTemp returns formatted actual and target', () => {
    component.status = makeStatus({ bedTempActual: 62, bedTempTarget: 65 });
    expect(component.bedTemp).toBe('62°C / 65°C');
  });

  it('bedTemp returns dash when status is null', () => {
    component.status = null;
    expect(component.bedTemp).toBe('—');
  });

  it('nozzleTemp returns formatted string', () => {
    component.status = makeStatus({ nozzleTempActual: 218, nozzleTempTarget: 220 });
    expect(component.nozzleTemp).toBe('218°C / 220°C');
  });

  it('chamberTemp returns value when non-zero', () => {
    component.status = makeStatus({ chamberTempActual: 31 });
    expect(component.chamberTemp).toBe('31°C');
  });

  it('chamberTemp returns dash when zero', () => {
    component.status = makeStatus({ chamberTempActual: 0 });
    expect(component.chamberTemp).toBe('—');
  });

  it('chamberTemp returns dash when status is null', () => {
    component.status = null;
    expect(component.chamberTemp).toBe('—');
  });

  // ── layer / speed ───────────────────────────────────────────────────────────

  it('layerLabel returns string when layerTotal > 0', () => {
    component.status = makeStatus({ layerCurrent: 42, layerTotal: 180 });
    expect(component.layerLabel).toBe('42 / 180');
  });

  it('layerLabel returns null when layerTotal is 0', () => {
    component.status = makeStatus({ layerTotal: 0 });
    expect(component.layerLabel).toBeNull();
  });

  it('speedLabel returns percentage when printing', () => {
    component.status = makeStatus({ state: 'PRINTING', speedPercent: 100 });
    expect(component.speedLabel).toBe('100%');
  });

  it('speedLabel returns null when idle', () => {
    component.status = makeStatus({ state: 'IDLE', speedPercent: 100 });
    expect(component.speedLabel).toBeNull();
  });

  it('speedLabel returns null when speedPercent is 0', () => {
    component.status = makeStatus({ state: 'PRINTING', speedPercent: 0 });
    expect(component.speedLabel).toBeNull();
  });

  // ── HMS alerts ──────────────────────────────────────────────────────────────

  it('hasHmsAlerts is true when alerts are present', () => {
    component.status = makeStatus({ hmsAlerts: [{ attr: 1, code: 2 }] });
    expect(component.hasHmsAlerts).toBeTrue();
  });

  it('hasHmsAlerts is false when hmsAlerts is empty', () => {
    component.status = makeStatus({ hmsAlerts: [] });
    expect(component.hasHmsAlerts).toBeFalse();
  });

  it('hmsAlertLabel uses singular for one alert', () => {
    component.status = makeStatus({ hmsAlerts: [{ attr: 1, code: 2 }] });
    expect(component.hmsAlertLabel).toBe('1 printer alert');
  });

  it('hmsAlertLabel uses plural for multiple alerts', () => {
    component.status = makeStatus({ hmsAlerts: [{ attr: 1, code: 2 }, { attr: 3, code: 4 }] });
    expect(component.hmsAlertLabel).toBe('2 printer alerts');
  });

  // ── hmsSeverity ─────────────────────────────────────────────────────────────

  it('hmsSeverity maps byte value 1 to Fatal', () => {
    expect(component.hmsSeverity(0x100)).toBe('Fatal');
  });

  it('hmsSeverity maps byte value 2 to Serious', () => {
    expect(component.hmsSeverity(0x200)).toBe('Serious');
  });

  it('hmsSeverity maps byte value 3 to Moderate', () => {
    expect(component.hmsSeverity(0x300)).toBe('Moderate');
  });

  it('hmsSeverity maps byte value 4 to Info', () => {
    expect(component.hmsSeverity(0x400)).toBe('Info');
  });

  it('hmsSeverity returns Unknown for unrecognized byte', () => {
    expect(component.hmsSeverity(0x500)).toBe('Unknown');
  });

  // ── hmsSeverityClass ────────────────────────────────────────────────────────

  it('hmsSeverityClass maps Fatal to red', () => {
    expect(component.hmsSeverityClass(0x100)).toBe('text-red-400');
  });

  it('hmsSeverityClass maps Serious to orange', () => {
    expect(component.hmsSeverityClass(0x200)).toBe('text-orange-400');
  });

  it('hmsSeverityClass maps Moderate to yellow', () => {
    expect(component.hmsSeverityClass(0x300)).toBe('text-yellow-400');
  });

  it('hmsSeverityClass maps Info to blue', () => {
    expect(component.hmsSeverityClass(0x400)).toBe('text-blue-400');
  });

  it('hmsSeverityClass maps Unknown to gray', () => {
    expect(component.hmsSeverityClass(0x500)).toBe('text-gray-400');
  });

  // ── hmsHex ──────────────────────────────────────────────────────────────────

  it('hmsHex formats number as padded 8-digit hex', () => {
    expect(component.hmsHex(255)).toBe('0x000000FF');
  });

  // ── output emitters ─────────────────────────────────────────────────────────

  it('pause emits when called', () => {
    let emitted = false;
    component.pause.subscribe(() => (emitted = true));
    component.pause.emit();
    expect(emitted).toBeTrue();
  });

  it('resume emits when called', () => {
    let emitted = false;
    component.resume.subscribe(() => (emitted = true));
    component.resume.emit();
    expect(emitted).toBeTrue();
  });

  it('cancel emits when called', () => {
    let emitted = false;
    component.cancel.subscribe(() => (emitted = true));
    component.cancel.emit();
    expect(emitted).toBeTrue();
  });
});
