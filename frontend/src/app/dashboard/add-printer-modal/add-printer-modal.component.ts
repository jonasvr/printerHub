import { Component, Input, Output, EventEmitter, OnInit, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { PrinterService } from '../../core/services/printer.service';
import { Printer, PrinterBrand } from '../../core/models/printer.model';
import { PRINTER_MODELS, CONNECTION_HINTS, PrinterModelOption, ConnectionHint, BrandHints } from '../../core/data/printer-catalogue';

@Component({
  selector: 'ph-add-printer-modal',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './add-printer-modal.component.html'
})
export class AddPrinterModalComponent implements OnInit {

  /** When provided, the modal opens in edit mode pre-filled with this printer. */
  @Input() printer?: Printer;

  @Output() added   = new EventEmitter<Printer>();
  @Output() updated = new EventEmitter<Printer>();
  @Output() closed  = new EventEmitter<void>();

  private fb             = inject(FormBuilder);
  private printerService = inject(PrinterService);
  private destroyRef     = inject(DestroyRef);

  saving  = false;
  error: string | null = null;

  readonly brands: PrinterBrand[] = ['BAMBU', 'PRUSA', 'CREALITY', 'GENERIC_HTTP'];

  form = this.fb.group({
    name:         ['', Validators.required],
    brand:        ['BAMBU' as PrinterBrand, Validators.required],
    model:        [PRINTER_MODELS['BAMBU'][0].value],
    serialNumber: [''],
    ipAddress:    [''],
    accessCode:   ['']
  });

  get isEdit(): boolean { return !!this.printer; }
  get title(): string   { return this.isEdit ? 'Edit Printer' : 'Add Printer'; }
  get submitLabel(): string { return this.isEdit ? 'Save changes' : 'Add printer'; }

  get needsLan(): boolean {
    return this.form.value.brand === 'BAMBU';
  }

  get currentModels(): PrinterModelOption[] {
    return PRINTER_MODELS[this.form.value.brand as PrinterBrand] ?? [];
  }

  get connectionHint(): ConnectionHint | null {
    const brand = this.form.value.brand as PrinterBrand;
    const model = this.form.value.model as string;
    const entry: BrandHints | undefined = CONNECTION_HINTS[brand];
    if (!entry) return null;
    return entry.models?.[model] ?? entry.default;
  }

  ngOnInit(): void {
    if (this.printer) {
      this.form.patchValue({
        name:         this.printer.name,
        brand:        this.printer.brand,
        model:        this.printer.model ?? '',
        serialNumber: this.printer.serialNumber ?? '',
        ipAddress:    this.printer.ipAddress ?? '',
        accessCode:   ''   // never pre-fill credentials
      });
    }

    this.form.controls.brand.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(brand => {
        const first = PRINTER_MODELS[brand as PrinterBrand]?.[0]?.value ?? '';
        this.form.controls.model.setValue(first, { emitEvent: false });
      });
  }

  submit(): void {
    if (this.form.invalid || this.saving) return;
    this.saving = true;
    this.error  = null;

    if (this.isEdit) {
      this.printerService.update(this.printer!.id, this.form.value as Partial<Printer>).subscribe({
        next: p => { this.saving = false; this.updated.emit(p); },
        error: err => { this.saving = false; this.error = err?.error?.message ?? 'Failed to update printer.'; }
      });
    } else {
      this.printerService.add(this.form.value as Partial<Printer>).subscribe({
        next: p => { this.saving = false; this.added.emit(p); },
        error: err => { this.saving = false; this.error = err?.error?.message ?? 'Failed to register printer.'; }
      });
    }
  }
}
