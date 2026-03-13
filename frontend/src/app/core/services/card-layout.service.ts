import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

const KEY = 'ph_card_layout';

@Injectable({ providedIn: 'root' })
export class CardLayoutService {

  private _layout$ = new BehaviorSubject<number>(
    Number(localStorage.getItem(KEY) ?? 1)
  );

  readonly layout$ = this._layout$.asObservable();

  get layout(): number { return this._layout$.value; }

  set(n: number): void {
    localStorage.setItem(KEY, String(n));
    this._layout$.next(n);
  }
}
