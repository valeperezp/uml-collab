import { CommonModule } from '@angular/common';
import { Component, ElementRef, HostListener, OnDestroy, OnInit, ViewChild, computed, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs';

import { DiagramApiService } from '../../core/services/diagram-api.service';
import { WebSocketService } from '../../core/services/websocket.service';
import { AuthService } from '../../core/services/auth.service';
import { downloadBlob } from '../../core/services/download.util';
import {
  ClassDto,
  DataType,
  DiagramDetail,
  DiagramEvent,
  LockDto,
  PresenceInfo,
  RelationshipDto,
  RelationshipType,
} from '../../core/models/models';

import { ClassBoxComponent } from '../../components/class-box/class-box.component';
import { PresenceBarComponent } from '../../components/presence-bar/presence-bar.component';
import { AiPanelComponent, AiLogEntry } from '../../components/ai-panel/ai-panel.component';
import { ToolbarComponent } from '../../components/toolbar/toolbar.component';

interface PendingRelationship {
  sourceId: string;
  sourceName: string;
  targetId: string;
  targetName: string;
}

const RELATIONSHIP_TYPES: RelationshipType[] = ['ASSOCIATION', 'AGGREGATION', 'COMPOSITION', 'GENERALIZATION', 'REALIZATION'];
const MULTIPLICITIES = ['1', '0..1', '*', '1..*', '0..*'];

@Component({
  selector: 'app-diagram',
  standalone: true,
  imports: [CommonModule, FormsModule, ClassBoxComponent, PresenceBarComponent, AiPanelComponent, ToolbarComponent],
  templateUrl: './diagram.component.html',
  styleUrl: './diagram.component.scss',
})
export class DiagramComponent implements OnInit, OnDestroy {
  diagram = signal<DiagramDetail | null>(null);
  locks = signal<Map<string, LockDto>>(new Map());
  presence = signal<PresenceInfo[]>([]);
  connected = signal(false);

  aiLog = signal<AiLogEntry[]>([]);
  aiBusy = signal(false);
  generatingBackend = signal(false);

  connectingFromClassId = signal<string | null>(null);
  pendingRelationship = signal<PendingRelationship | null>(null);
  relType: RelationshipType = 'ASSOCIATION';
  sourceMult = '1';
  targetMult = '*';

  readonly relationshipTypes = RELATIONSHIP_TYPES;
  readonly multiplicities = MULTIPLICITIES;

  classes = computed(() => this.diagram()?.classes ?? []);
  relationships = computed(() => this.diagram()?.relationships ?? []);

  @ViewChild('canvas') canvasRef?: ElementRef<HTMLDivElement>;

  private diagramId = '';
  private subs: Subscription[] = [];
  private draggingClassId: string | null = null;
  private dragOffsetX = 0;
  private dragOffsetY = 0;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private api: DiagramApiService,
    private ws: WebSocketService,
    public auth: AuthService,
  ) {}

  ngOnInit(): void {
    this.diagramId = this.route.snapshot.paramMap.get('id') ?? '';
    if (!this.diagramId) {
      this.router.navigateByUrl('/diagrams');
      return;
    }
    this.load();
    this.ws.connectToDiagram(this.diagramId);
    this.subs.push(this.ws.connected$.subscribe((c) => this.connected.set(c)));
    this.subs.push(this.ws.events$.subscribe((ev) => this.applyEvent(ev)));
    this.subs.push(this.ws.presence$.subscribe((p) => this.presence.set(p)));
  }

  ngOnDestroy(): void {
    this.subs.forEach((s) => s.unsubscribe());
    this.ws.disconnect();
  }

  private load(): void {
    this.api.getDetail(this.diagramId).subscribe((d) => this.diagram.set(d));
    this.api.activeLocks(this.diagramId).subscribe((locks) => {
      const map = new Map<string, LockDto>();
      locks.forEach((l) => map.set(l.elementId, l));
      this.locks.set(map);
    });
  }

  goBack(): void {
    this.router.navigateByUrl('/diagrams');
  }

  // ---------- Eventos en tiempo real ----------

  private applyEvent(event: DiagramEvent): void {
    switch (event.type) {
      case 'CLASS_CREATED':
      case 'CLASS_UPDATED':
        this.upsertClass(event.payload as ClassDto);
        break;
      case 'CLASS_DELETED':
        this.removeClass(event.payload as unknown as string);
        break;
      case 'RELATIONSHIP_CREATED':
      case 'RELATIONSHIP_UPDATED':
        this.upsertRelationship(event.payload as RelationshipDto);
        break;
      case 'RELATIONSHIP_DELETED':
        this.removeRelationship(event.payload as unknown as string);
        break;
      case 'LOCK_ACQUIRED':
        this.setLock(event.payload as LockDto);
        break;
      case 'LOCK_RELEASED':
        this.clearLock(event.payload as LockDto);
        break;
      case 'AI_OPERATIONS_APPLIED':
      case 'DIAGRAM_REPLACED':
        this.diagram.set(event.payload as DiagramDetail);
        break;
    }
  }

  private upsertClass(dto: ClassDto): void {
    const d = this.diagram();
    if (!d) return;
    const idx = d.classes.findIndex((c) => c.id === dto.id);
    const classes = [...d.classes];
    if (idx >= 0) classes[idx] = dto; else classes.push(dto);
    this.diagram.set({ ...d, classes });
  }

  private removeClass(classId: string): void {
    const d = this.diagram();
    if (!d) return;
    this.diagram.set({
      ...d,
      classes: d.classes.filter((c) => c.id !== classId),
      relationships: d.relationships.filter((r) => r.sourceClassId !== classId && r.targetClassId !== classId),
    });
  }

  private upsertRelationship(dto: RelationshipDto): void {
    const d = this.diagram();
    if (!d) return;
    const idx = d.relationships.findIndex((r) => r.id === dto.id);
    const relationships = [...d.relationships];
    if (idx >= 0) relationships[idx] = dto; else relationships.push(dto);
    this.diagram.set({ ...d, relationships });
  }

  private removeRelationship(relationshipId: string): void {
    const d = this.diagram();
    if (!d) return;
    this.diagram.set({ ...d, relationships: d.relationships.filter((r) => r.id !== relationshipId) });
  }

  private setLock(lock: LockDto): void {
    const map = new Map(this.locks());
    map.set(lock.elementId, lock);
    this.locks.set(map);
  }

  private clearLock(lock: LockDto): void {
    const map = new Map(this.locks());
    map.delete(lock.elementId);
    this.locks.set(map);
  }


  // ---------- Clases ----------

  addClass(): void {
    const count = this.classes().length;
    const x = 80 + (count % 4) * 260;
    const y = 80 + Math.floor(count / 4) * 220;
    this.api.createClass(this.diagramId, 'NuevaClase', x, y).subscribe();
  }

  onRename(classDto: ClassDto, newName: string): void {
    this.api.updateClass(this.diagramId, classDto.id, { name: newName }).subscribe();
  }

  onDeleteClass(classDto: ClassDto): void {
    if (!confirm(`¿Eliminar la clase "${classDto.name}"? Esto también borra sus relaciones.`)) return;
    this.api.deleteClass(this.diagramId, classDto.id).subscribe();
  }

  onAddAttribute(classDto: ClassDto, attr: { name: string; dataType: DataType; isPrimaryKey: boolean }): void {
    this.api.addAttribute(this.diagramId, classDto.id, attr).subscribe();
  }

  onRemoveAttribute(classDto: ClassDto, attributeId: string): void {
    this.api.removeAttribute(this.diagramId, classDto.id, attributeId).subscribe();
  }

  // ---------- Drag & drop ----------

  onDragStart(event: MouseEvent, classDto: ClassDto): void {
    if (this.lockOwnerNameFor(classDto.id)) return;
    this.draggingClassId = classDto.id;
    const canvasRect = this.canvasRef?.nativeElement.getBoundingClientRect();
    const canvasX = event.clientX - (canvasRect?.left ?? 0) + (this.canvasRef?.nativeElement.scrollLeft ?? 0);
    const canvasY = event.clientY - (canvasRect?.top ?? 0) + (this.canvasRef?.nativeElement.scrollTop ?? 0);
    this.dragOffsetX = canvasX - classDto.x;
    this.dragOffsetY = canvasY - classDto.y;
    this.api.acquireLock(this.diagramId, 'CLASS', classDto.id).subscribe({ error: () => (this.draggingClassId = null) });
  }

  @HostListener('document:mousemove', ['$event'])
  onDocumentMouseMove(event: MouseEvent): void {
    if (!this.draggingClassId) return;
    const current = this.classes().find((c) => c.id === this.draggingClassId);
    if (!current) {
      // La clase que estabamos arrastrando fue borrada por otro colaborador mientras tanto.
      this.draggingClassId = null;
      return;
    }
    const canvasRect = this.canvasRef?.nativeElement.getBoundingClientRect();
    const canvasX = event.clientX - (canvasRect?.left ?? 0) + (this.canvasRef?.nativeElement.scrollLeft ?? 0);
    const canvasY = event.clientY - (canvasRect?.top ?? 0) + (this.canvasRef?.nativeElement.scrollTop ?? 0);
    const x = Math.max(0, canvasX - this.dragOffsetX);
    const y = Math.max(0, canvasY - this.dragOffsetY);
    this.upsertClass({ ...current, x, y });
  }

  @HostListener('document:mouseup')
  onDocumentMouseUp(): void {
    if (!this.draggingClassId) return;
    const classId = this.draggingClassId;
    this.draggingClassId = null;
    const dto = this.classes().find((c) => c.id === classId);
    if (dto) {
      this.api.updateClass(this.diagramId, classId, { x: dto.x, y: dto.y }).subscribe({
        complete: () => this.api.releaseLock(this.diagramId, 'CLASS', classId).subscribe(),
      });
    }
  }

  lockOwnerNameFor(elementId: string): string | null {
    const lock = this.locks().get(elementId);
    if (!lock || lock.userId === this.auth.current()?.userId) return null;
    return lock.userDisplayName ?? 'otro usuario';
  }

  // ---------- Relaciones ----------

  onStartConnect(classDto: ClassDto): void {
    this.connectingFromClassId.set(this.connectingFromClassId() === classDto.id ? null : classDto.id);
  }

  onBodyClick(classDto: ClassDto): void {
    const sourceId = this.connectingFromClassId();
    if (!sourceId || sourceId === classDto.id) return;
    const source = this.classes().find((c) => c.id === sourceId);
    if (!source) return;
    this.pendingRelationship.set({ sourceId, sourceName: source.name, targetId: classDto.id, targetName: classDto.name });
    this.connectingFromClassId.set(null);
    this.relType = 'ASSOCIATION';
    this.sourceMult = '1';
    this.targetMult = '*';
  }

  confirmRelationship(): void {
    const pending = this.pendingRelationship();
    if (!pending) return;
    this.api
      .createRelationship(this.diagramId, {
        sourceClassId: pending.sourceId,
        targetClassId: pending.targetId,
        type: this.relType,
        sourceMultiplicity: this.sourceMult,
        targetMultiplicity: this.targetMult,
      })
      .subscribe();
    this.pendingRelationship.set(null);
  }

  cancelRelationship(): void {
    this.pendingRelationship.set(null);
  }

  onDeleteRelationship(relationship: RelationshipDto): void {
    if (!confirm(`¿Eliminar la relación entre "${relationship.sourceClassName}" y "${relationship.targetClassName}"?`)) return;
    this.api.deleteRelationship(this.diagramId, relationship.id).subscribe();
  }

  relationshipPath(r: RelationshipDto): { x1: number; y1: number; x2: number; y2: number; midX: number; midY: number } {
    const source = this.classes().find((c) => c.id === r.sourceClassId);
    const target = this.classes().find((c) => c.id === r.targetClassId);
    const x1 = (source?.x ?? 0) + 110;
    const y1 = (source?.y ?? 0) + 24;
    const x2 = (target?.x ?? 0) + 110;
    const y2 = (target?.y ?? 0) + 24;
    return { x1, y1, x2, y2, midX: (x1 + x2) / 2, midY: (y1 + y2) / 2 };
  }

  relationshipLabel(r: RelationshipDto): string {
    if (r.type === 'GENERALIZATION') return 'hereda';
    const prefix = r.type === 'COMPOSITION' ? '◆' : r.type === 'AGGREGATION' ? '◇' : '';
    return `${r.sourceMultiplicity} ${prefix} ${r.targetMultiplicity}`;
  }

  // ---------- IA ----------

  onAiCommand(text: string): void {
    this.aiLog.set([...this.aiLog(), { role: 'user', text }]);
    this.aiBusy.set(true);
    this.api.sendAiCommand(this.diagramId, text).subscribe({
      next: (res) => {
        this.aiLog.set([...this.aiLog(), { role: 'assistant', text: res.assistantMessage }]);
        this.aiBusy.set(false);
      },
      error: (err) => {
        this.aiLog.set([...this.aiLog(), { role: 'error', text: err?.error?.message ?? 'Error del asistente' }]);
        this.aiBusy.set(false);
      },
    });
  }

  onAiImage(file: File): void {
    this.aiLog.set([...this.aiLog(), { role: 'user', text: `📷 ${file.name}` }]);
    this.aiBusy.set(true);
    this.api.sendAiImage(this.diagramId, file).subscribe({
      next: (res) => {
        this.aiLog.set([...this.aiLog(), { role: 'assistant', text: res.assistantMessage }]);
        this.aiBusy.set(false);
      },
      error: (err) => {
        this.aiLog.set([...this.aiLog(), { role: 'error', text: err?.error?.message ?? 'No se pudo leer la imagen' }]);
        this.aiBusy.set(false);
      },
    });
  }

  // ---------- XMI / codegen ----------

  exportXmi(): void {
    const name = this.diagram()?.name ?? 'diagrama';
    this.api.exportXmi(this.diagramId).subscribe((blob) => downloadBlob(blob, `${name}.xmi`));
  }

  importXmi(file: File): void {
    this.aiLog.set([...this.aiLog(), { role: 'user', text: `Importando ${file.name}…` }]);
    this.api.importXmi(this.diagramId, file).subscribe({
      next: (res) => this.aiLog.set([...this.aiLog(), { role: 'assistant', text: res.assistantMessage }]),
      error: (err) => this.aiLog.set([...this.aiLog(), { role: 'error', text: err?.error?.message ?? 'No se pudo importar el XMI' }]),
    });
  }

  generateBackend(): void {
    this.generatingBackend.set(true);
    const name = this.diagram()?.name ?? 'diagrama';
    this.api.generateBackend(this.diagramId).subscribe({
      next: (blob) => {
        downloadBlob(blob, `${name}-backend.zip`);
        this.generatingBackend.set(false);
      },
      error: () => this.generatingBackend.set(false),
    });
  }
}
