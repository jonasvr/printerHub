import { PrinterBrand } from '../models/printer.model';

export interface PrinterModelOption {
  value: string;
  label: string;
}

export interface ConnectionHint {
  steps: string[];
  note?: string;
}

export interface BrandHints {
  /** Shown when no model-specific entry exists. */
  default: ConnectionHint;
  /** Per-model overrides keyed by PrinterModelOption.value. */
  models?: Partial<Record<string, ConnectionHint>>;
}

export const PRINTER_MODELS: Record<PrinterBrand, PrinterModelOption[]> = {
  BAMBU: [
    { value: 'X1C',    label: 'X1 Carbon (X1C)' },
    { value: 'X1E',    label: 'X1E' },
    { value: 'P1S',    label: 'P1S' },
    { value: 'P1P',    label: 'P1P' },
    { value: 'A1',     label: 'A1' },
    { value: 'A1MINI', label: 'A1 mini' },
  ],
  PRUSA: [
    { value: 'MK4',   label: 'MK4' },
    { value: 'MK39S', label: 'MK3.9S' },
    { value: 'XL',    label: 'XL' },
    { value: 'MINIP', label: 'MINI+' },
  ],
  CREALITY: [
    { value: 'K1',       label: 'K1' },
    { value: 'K1MAX',    label: 'K1 Max' },
    { value: 'K1C',      label: 'K1C' },
    { value: 'ENDER3V3', label: 'Ender-3 V3 KE' },
  ],
  GENERIC_HTTP: [
    { value: 'CUSTOM', label: 'Other / Custom' },
  ],
};

export const CONNECTION_HINTS: Record<PrinterBrand, BrandHints> = {
  BAMBU: {
    default: {
      steps: [
        'Touchscreen: Settings → Wi-Fi → note the IP address.',
        'Touchscreen: Settings → Network → enable "LAN Only Liveview".',
        'Touchscreen: Settings → Network → note the Access Code (8 digits).',
        'Serial number: on the back label, or Settings → Device.',
      ],
    },
    models: {
      X1C: {
        steps: [
          'Not tested yet',
          'Tap the Settings gear (top bar) → Network → enable "LAN Only Liveview".',
          'Your Access Code is displayed on the same Network screen (8 digits).',
          'IP address: Settings → Wi-Fi → shown next to your network name.',
          'Serial number: on the label on the back of the printer.',
        ],
      },
      X1E: {
        steps: [
          'Not tested yet',
          'Tap the Settings gear (top bar) → Network → enable "LAN Only Liveview".',
          'Your Access Code is displayed on the same Network screen (8 digits).',
          'IP address: Settings → Wi-Fi → shown next to your network name.',
          'Serial number: on the label on the back of the printer.',
        ],
      },
      P1S: {
        steps: [
          'Display → Tap the Settings gear.',
          'WLAN → IP',
          'WLAN → Access Code (8 digits).',
          'Device → printer (Serial number)',
        ],
      },
      P1P: {
        steps: [
          'Display → Tap the Settings gear.',
          'WLAN → IP"',
          'WLAN → Access Code (8 digits).',
          'Device → printer (Serial number)',
        ],
      },
      A1: {
        steps: [
          'Not tested yet',
          'Tap the hamburger menu (≡) → Settings → Wi-Fi → note the IP address.',
          'Tap (≡) → Settings → Network → enable "LAN Only Mode".',
          'Your Access Code is displayed on the Network screen (8 digits).',
          'Serial number: on the label on the back of the printer.',
        ],
      },
      A1MINI: {
        steps: [
          'Not tested yet',
          'Tap the hamburger menu (≡) → Settings → Wi-Fi → note the IP address.',
          'Tap (≡) → Settings → Network → enable "LAN Only Mode".',
          'Your Access Code is displayed on the Network screen (8 digits).',
          'Serial number: on the label on the back of the printer.',
        ],
      },
    },
  },
  PRUSA: {
    default: {
      steps: [
        'Not tested yet',
        'Ensure PrusaLink is installed (MK4 ships with it; MK3.9S requires the Raspberry Pi Zero 2W upgrade).',
        'On the printer LCD: Settings → Network → note the IP address.',
        'Open the PrusaLink web UI and go to Settings → API Key.',
      ],
      note: 'The Prusa HTTP adapter is coming soon. You can register the printer now and connect it once the adapter ships.',
    },
    models: {
      MK4: {
        steps: [
         'Not tested yet',
           'PrusaLink is pre-installed on the MK4.',
          'On the LCD: Settings → Network → note the IP address.',
          'Open http://<ip> in a browser → Settings → API Key.',
        ],
        note: 'The Prusa HTTP adapter is coming soon.',
      },
      MK39S: {
        steps: [
          'Not tested yet',
          'Install the Raspberry Pi Zero 2W into the Einsy slot and flash it with PrusaLink.',
          'On the LCD: Settings → Network → note the IP address.',
          'Open http://<ip> in a browser → Settings → API Key.',
        ],
        note: 'The Prusa HTTP adapter is coming soon.',
      },
    },
  },
  CREALITY: {
    default: {
      steps: [
        'Not tested yet',
        'Ensure your printer runs Creality OS with the HTTP API enabled.',
        'Find the IP address on the printer screen under Settings → Wi-Fi.',
      ],
      note: 'The Creality HTTP adapter is coming soon. You can register the printer now.',
    },
  },
  GENERIC_HTTP: {
    default: {
      steps: [
        'Not tested yet',
        'Enter the base URL of your printer\'s HTTP API (e.g. http://192.168.1.x).',
        'Consult your printer\'s documentation for the required API key or auth header.',
      ],
    },
  },
};
