import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface AppSettings {
  pushallIntervalSeconds: number;
}

@Injectable({ providedIn: 'root' })
export class SettingsService {

  private http = inject(HttpClient);
  private base = `${environment.apiBaseUrl}/settings`;

  get(): Observable<AppSettings> {
    return this.http.get<AppSettings>(this.base);
  }

  setPushAllInterval(seconds: number): Observable<AppSettings> {
    return this.http.patch<AppSettings>(`${this.base}/pushall-interval`, { seconds });
  }
}
