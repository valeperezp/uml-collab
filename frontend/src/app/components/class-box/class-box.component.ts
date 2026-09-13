import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AttributeDto, ClassDto, DataType } from '../../core/models/models';

export const DATA_TYPES: DataType[] = ['STRING', 'TEXT', 'INTEGER', 'LONG', 'DOUBLE', 'DECIMAL', 'BOOLEAN', 'DATE', 'DATETIME', 'UUID'];

@Component({
  selector: 'app-class-box',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './class-box.component.html',
  styleUrl: './class-box.component.scss',
})
export class ClassBoxComponent {
  @Input({ required: true }) classDto!: ClassDto;
  @Input() lockedByOther: string | null = null;
  @Input() connecting = false;
  @Input() connectSource = false;

  @Output() dragStart = new EventEmitter<MouseEvent>();
  @Output() rename = new EventEmitter<string>();
  @Output() deleteClass = new EventEmitter<void>();
  @Output() addAttribute = new EventEmitter<{ name: string; dataType: DataType; isPrimaryKey: boolean }>();
  @Output() removeAttribute = new EventEmitter<string>();
  @Output() startConnect = new EventEmitter<void>();
  @Output() bodyClick = new EventEmitter<void>();

  readonly dataTypes = DATA_TYPES;

  editingName = signal(false);
  nameDraft = '';
  showAddAttribute = signal(false);
  newAttrName = '';
  newAttrType: DataType = 'STRING';
  newAttrPk = false;

  get disabled(): boolean {
    return this.lockedByOther !== null;
  }

  onHeaderMouseDown(event: MouseEvent): void {
    if (this.editingName()) return;
    this.dragStart.emit(event);
  }

  startEditName(): void {
    if (this.disabled) return;
    this.nameDraft = this.classDto.name;
    this.editingName.set(true);
  }

  confirmName(): void {
    this.editingName.set(false);
    const trimmed = this.nameDraft.trim();
    if (trimmed && trimmed !== this.classDto.name) {
      this.rename.emit(trimmed);
    }
  }

  toggleAddAttribute(): void {
    this.showAddAttribute.set(!this.showAddAttribute());
    this.newAttrName = '';
    this.newAttrType = 'STRING';
    this.newAttrPk = false;
  }

  confirmAddAttribute(): void {
    const name = this.newAttrName.trim();
    if (!name) return;
    this.addAttribute.emit({ name, dataType: this.newAttrType, isPrimaryKey: this.newAttrPk });
    this.toggleAddAttribute();
  }

  visibilitySymbol(attribute: AttributeDto): string {
    switch (attribute.visibility) {
      case 'PUBLIC': return '+';
      case 'PROTECTED': return '#';
      case 'PACKAGE': return '~';
      default: return '-';
    }
  }
}
