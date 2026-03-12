import { Injectable, inject, OnDestroy, NgZone } from '@angular/core';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { Observable, Subject } from 'rxjs';
import { MqttLogEntry, PrinterStatusUpdate } from '../models/printer.model';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class WebSocketService implements OnDestroy {

  private ngZone = inject(NgZone);

  private client: Client;
  private subscriptions = new Map<string, StompSubscription>();
  private statusSubjects = new Map<string, Subject<PrinterStatusUpdate>>();
  private logSubscriptions = new Map<string, StompSubscription>();
  private logSubjects = new Map<string, Subject<MqttLogEntry>>();

  constructor() {
    this.client = new Client({
      webSocketFactory: () => new SockJS(environment.wsUrl) as WebSocket,
      reconnectDelay: 5000,
      // debug: str => {
      //   if (!environment.production) console.debug('[STOMP]', str);
      // }
    });

    // Single onConnect handler — re-subscribes all tracked printers on every (re)connect
    this.client.onConnect = () => this.resubscribeAll();
    this.client.activate();
  }

  watchPrinter(printerId: string): Observable<PrinterStatusUpdate> {
    if (!this.statusSubjects.has(printerId)) {
      this.statusSubjects.set(printerId, new Subject<PrinterStatusUpdate>());

      // Subscribe immediately if already connected; otherwise resubscribeAll() handles it
      if (this.client.connected) {
        this.doSubscribe(printerId);
      }
    }

    return this.statusSubjects.get(printerId)!.asObservable();
  }

  stopWatching(printerId: string): void {
    this.subscriptions.get(printerId)?.unsubscribe();
    this.subscriptions.delete(printerId);
    this.statusSubjects.get(printerId)?.complete();
    this.statusSubjects.delete(printerId);
  }

  watchLogs(printerId: string): Observable<MqttLogEntry> {
    if (!this.logSubjects.has(printerId)) {
      this.logSubjects.set(printerId, new Subject<MqttLogEntry>());
      if (this.client.connected) {
        this.doSubscribeLogs(printerId);
      }
    }
    return this.logSubjects.get(printerId)!.asObservable();
  }

  stopWatchingLogs(printerId: string): void {
    this.logSubscriptions.get(printerId)?.unsubscribe();
    this.logSubscriptions.delete(printerId);
    this.logSubjects.get(printerId)?.complete();
    this.logSubjects.delete(printerId);
  }

  ngOnDestroy(): void {
    this.client.deactivate();
  }

  private doSubscribe(printerId: string): void {
    const subject = this.statusSubjects.get(printerId);
    if (!subject || this.subscriptions.has(printerId)) return;

    const sub = this.client.subscribe(
      `/topic/printers/${printerId}`,
      (message: IMessage) => {
        const update: PrinterStatusUpdate = JSON.parse(message.body);
        // ngZone.run() tells Angular that something changed — triggers change detection
        this.ngZone.run(() => subject.next(update));
      }
    );
    this.subscriptions.set(printerId, sub);
  }

  private doSubscribeLogs(printerId: string): void {
    const subject = this.logSubjects.get(printerId);
    if (!subject || this.logSubscriptions.has(printerId)) return;

    const sub = this.client.subscribe(
      `/topic/printers/${printerId}/mqtt-logs`,
      (message: IMessage) => {
        const entry: MqttLogEntry = JSON.parse(message.body);
        this.ngZone.run(() => subject.next(entry));
      }
    );
    this.logSubscriptions.set(printerId, sub);
  }

  private resubscribeAll(): void {
    this.subscriptions.clear();
    this.statusSubjects.forEach((_, printerId) => this.doSubscribe(printerId));
    this.logSubscriptions.clear();
    this.logSubjects.forEach((_, printerId) => this.doSubscribeLogs(printerId));
  }
}
