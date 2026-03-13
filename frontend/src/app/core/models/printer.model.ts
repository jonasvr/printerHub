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

/** Mirrors the Java MqttLogEntry record */
export interface MqttLogEntry {
  printerId: string;
  timestamp: string;
  payload: string;   // raw JSON string
}

/** Mirrors the Java HmsAlert record */
export interface HmsAlert {
  attr: number;
  code: number;
}

/** Mirrors the Java AmsTray record */
export interface AmsTray {
  id: string;
  filamentType: string;
  subBrand: string | null;
  colorHex: string;      // 6-char RGB e.g. "56B7E6" — ready for CSS
  isActive: boolean;
}

/** Mirrors the Java AmsStatus record */
export interface AmsStatus {
  unitId: string;
  trays: AmsTray[];
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
  connectionError?: string | null;
  chamberTempActual: number;
  layerCurrent: number;
  layerTotal: number;
  speedPercent: number;
  hmsAlerts: HmsAlert[];
  amsList: AmsStatus[] | null;
}
