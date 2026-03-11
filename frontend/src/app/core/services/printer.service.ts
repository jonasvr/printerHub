import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Printer } from '../models/printer.model';
import { environment } from '../../../environments/environment';

/**
 * HTTP service for the /api/v1/printers REST resource.
 *
 * Injectable({ providedIn: 'root' }) — Angular creates a single shared instance
 * (singleton) rather than one per component. Good default for services.
 *
 * We use inject() instead of constructor injection — the modern Angular 17 style
 * that works cleanly with standalone components.
 */
@Injectable({ providedIn: 'root' })
export class PrinterService {

  private http = inject(HttpClient);
  private base = `${environment.apiBaseUrl}/printers`;

  getAll(): Observable<Printer[]> {
    return this.http.get<Printer[]>(this.base);
  }

  getOne(id: string): Observable<Printer> {
    return this.http.get<Printer>(`${this.base}/${id}`);
  }

  add(printer: Partial<Printer>): Observable<Printer> {
    return this.http.post<Printer>(this.base, printer);
  }

  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }

  pause(id: string): Observable<void> {
    return this.http.post<void>(`${this.base}/${id}/pause`, {});
  }

  resume(id: string): Observable<void> {
    return this.http.post<void>(`${this.base}/${id}/resume`, {});
  }

  cancel(id: string): Observable<void> {
    return this.http.post<void>(`${this.base}/${id}/cancel`, {});
  }
}
