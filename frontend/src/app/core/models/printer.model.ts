/** Mirrors the Java PrinterBrand enum */
export type PrinterBrand = 'BAMBU' | 'PRUSA' | 'CREALITY' | 'GENERIC_HTTP';

/** Mirrors the Java PrinterState enum */
export type PrinterState = 'IDLE' | 'PRINTING' | 'PAUSED' | 'ERROR' | 'OFFLINE';

/** Mirrors the Java Printer entity */
export interface Printer {
  id: string;
  name: string;
  brand: PrinterBrand;
  model?: string;
  serialNumber?: string;
  ipAddress?: string;
  state: PrinterState;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

/** Mirrors the Java PrinterStatusUpdate record — pushed over WebSocket */
export interface PrinterStatusUpdate {
  printerId: string;
  state: PrinterState;
  progressPercent: number;
  currentFile: string | null;
  bedTempActual: number;
  bedTempTarget: number;
  nozzleTempActual: number;
  nozzleTempTarget: number;
  remainingMinutes: number;
  timestamp: string;
  mqttConnected: boolean;
}
