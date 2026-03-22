import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { PrinterService } from './printer.service';
import { environment } from '../../../environments/environment';
import { Printer } from '../models/printer.model';

describe('PrinterService', () => {
  let service: PrinterService;
  let http: HttpTestingController;
  const base = `${environment.apiBaseUrl}/printers`;

  const mockPrinter: Printer = {
    id: 'abc-123',
    name: 'X1C',
    brand: 'BAMBU',
    state: 'IDLE',
    active: true,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
    });
    service = TestBed.inject(PrinterService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('getAll sends GET to base URL and returns array', () => {
    let result: Printer[] | undefined;
    service.getAll().subscribe(r => (result = r));

    const req = http.expectOne(base);
    expect(req.request.method).toBe('GET');
    req.flush([mockPrinter]);

    expect(result).toEqual([mockPrinter]);
  });

  it('getOne sends GET to /printers/{id}', () => {
    service.getOne('abc-123').subscribe();

    const req = http.expectOne(`${base}/abc-123`);
    expect(req.request.method).toBe('GET');
    req.flush(mockPrinter);
  });

  it('add sends POST with body', () => {
    const body = { name: 'X1C', brand: 'BAMBU' } as Partial<Printer>;
    service.add(body).subscribe();

    const req = http.expectOne(base);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush(mockPrinter);
  });

  it('update sends PATCH to /printers/{id}', () => {
    const body = { name: 'Renamed' } as Partial<Printer>;
    service.update('abc-123', body).subscribe();

    const req = http.expectOne(`${base}/abc-123`);
    expect(req.request.method).toBe('PATCH');
    req.flush(mockPrinter);
  });

  it('remove sends DELETE to /printers/{id}', () => {
    service.remove('abc-123').subscribe();

    const req = http.expectOne(`${base}/abc-123`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('pause sends POST to /printers/{id}/pause', () => {
    service.pause('abc-123').subscribe();

    const req = http.expectOne(`${base}/abc-123/pause`);
    expect(req.request.method).toBe('POST');
    req.flush(null);
  });

  it('resume sends POST to /printers/{id}/resume', () => {
    service.resume('abc-123').subscribe();

    const req = http.expectOne(`${base}/abc-123/resume`);
    expect(req.request.method).toBe('POST');
    req.flush(null);
  });

  it('cancel sends POST to /printers/{id}/cancel', () => {
    service.cancel('abc-123').subscribe();

    const req = http.expectOne(`${base}/abc-123/cancel`);
    expect(req.request.method).toBe('POST');
    req.flush(null);
  });

  it('getLogs sends GET to /printers/{id}/mqtt-logs', () => {
    service.getLogs('abc-123').subscribe();

    const req = http.expectOne(`${base}/abc-123/mqtt-logs`);
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });
});
