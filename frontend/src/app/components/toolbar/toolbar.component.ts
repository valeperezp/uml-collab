import { CommonModule } from '@angular/common';
import { Component, ElementRef, EventEmitter, Input, Output, ViewChild } from '@angular/core';

@Component({
  selector: 'app-toolbar',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './toolbar.component.html',
  styleUrl: './toolbar.component.scss',
})
export class ToolbarComponent {
  @Input() diagramName = '';
  @Input() joinCode = '';
  @Input() generating = false;

  @Output() newClass = new EventEmitter<void>();
  @Output() exportXmi = new EventEmitter<void>();
  @Output() importXmi = new EventEmitter<File>();
  @Output() generateBackend = new EventEmitter<void>();
  @Output() back = new EventEmitter<void>();

  @ViewChild('xmiInput') xmiInput?: ElementRef<HTMLInputElement>;

  triggerImport(): void {
    this.xmiInput?.nativeElement.click();
  }

  onXmiFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (file) this.importXmi.emit(file);
    input.value = '';
  }

  copyJoinCode(): void {
    navigator.clipboard?.writeText(this.joinCode).catch(() => undefined);
  }
}
