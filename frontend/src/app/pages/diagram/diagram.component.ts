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
import { AiSettingsModalComponent } from '../../components/ai-settings-modal/ai-settings-modal.component';

interface PendingRelationship {
  sourceId: string;
  sourceName: string;
  targetId: string;
  targetName: string;
}

const RELATIONSHIP_TYPES: RelationshipType[] = [
  'ASSOCIATION',
  'AGGREGATION',
  'COMPOSITION',
  'GENERALIZATION',
  'REALIZATION',
  'DEPENDENCY',
];
const MULTIPLICITIES = [
  { value: '', label: '(Ninguno)' },
  { value: '1', label: '1' },
  { value: '0..1', label: '0..1' },
  { value: '*', label: '*' },
  { value: '1..*', label: '1..*' },
  { value: '0..*', label: '0..*' },
];

@Component({
  selector: 'app-diagram',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ClassBoxComponent,
    PresenceBarComponent,
    AiPanelComponent,
    ToolbarComponent,
    AiSettingsModalComponent,
  ],
  templateUrl: './diagram.component.html',
  styleUrl: './diagram.component.scss',
})
export class DiagramComponent implements OnInit, OnDestroy {
  diagram = signal<DiagramDetail | null>(null);
  locks = signal<Map<string, LockDto>>(new Map());
  aiSettingsOpen = signal(false);
  presence = signal<PresenceInfo[]>([]);
  connected = signal(false);

  aiLog = signal<AiLogEntry[]>([]);
  aiBusy = signal(false);
  aiPanelOpen = signal<boolean>(typeof window !== 'undefined' ? window.innerWidth >= 1024 : true);
  generatingBackend = signal(false);

  toggleAiPanel(): void {
    this.aiPanelOpen.set(!this.aiPanelOpen());
  }

  openAiPanel(): void {
    this.aiPanelOpen.set(true);
  }

  closeAiPanel(): void {
    this.aiPanelOpen.set(false);
  }

  /**
   * Oculta el chatbot de IA cuando el usuario interactúa activamente con el canvas
   * (al crear clase, editar, arrastrar, etc.) para que la pizarra quede completamente
   * libre y visible en pantallas responsivas.
   */
  autoHideChatbotOnAction(): void {
    if (this.aiPanelOpen() && typeof window !== 'undefined' && window.innerWidth <= 1024) {
      this.aiPanelOpen.set(false);
    }
  }

  connectingFromClassId = signal<string | null>(null);
  pendingRelationship = signal<PendingRelationship | null>(null);
  errorMessage = signal<string | null>(null);
  relType: RelationshipType = 'ASSOCIATION';
  sourceMult = '1';
  targetMult = '*';
  relLabel = '';

  readonly relationshipTypes = RELATIONSHIP_TYPES;
  readonly multiplicities = MULTIPLICITIES;

  classes = computed(() => this.diagram()?.classes ?? []);
  relationships = computed(() => this.diagram()?.relationships ?? []);

  // --- Infinite Canvas Pan & Zoom ---
  panX = signal(40);
  panY = signal(40);
  zoom = signal(1);
  isPanning = signal(false);
  protected readonly Math = Math;

  private panStartX = 0;
  private panStartY = 0;

  canvasBackgroundStyle = computed(() => {
    const z = this.zoom();
    const size = 24 * z;
    const px = ((this.panX() % size) + size) % size;
    const py = ((this.panY() % size) + size) % size;
    return {
      'background-size': `${size}px ${size}px`,
      'background-position': `${px}px ${py}px`,
    };
  });

  @ViewChild('canvas') canvasRef?: ElementRef<HTMLDivElement>;

  private diagramId = '';
  private subs: Subscription[] = [];
  private draggingClassId: string | null = null;
  private dragOffsetX = 0;
  private dragOffsetY = 0;

  // Offset manual (arrastrado por el usuario) de cada etiqueta de cardinalidad y nombre/rol,
  // relativo a su posicion calculada por defecto. Solo en el cliente: no se
  // persiste en el backend, cada usuario puede acomodarlas a su gusto.
  private multLabelOffsets = new Map<string, { sourceDx: number; sourceDy: number; targetDx: number; targetDy: number; labelDx?: number; labelDy?: number }>();
  private multDrag: { relId: string; end: 'source' | 'target' | 'label'; startX: number; startY: number; baseDx: number; baseDy: number } | null = null;
  private multDragMoved = false;

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

  // ---------- Pan & Zoom Handlers ----------

  onCanvasMouseDown(event: MouseEvent): void {
    if (event.button !== 0 && event.button !== 1) return;
    const target = event.target as HTMLElement;
    if (target.closest('app-class-box') || target.closest('.rel-label') || target.closest('.canvas-controls') || target.closest('app-ai-panel')) {
      return;
    }
    this.autoHideChatbotOnAction();
    this.isPanning.set(true);
    this.panStartX = event.clientX - this.panX();
    this.panStartY = event.clientY - this.panY();
  }

  onCanvasWheel(event: WheelEvent): void {
    event.preventDefault();
    const canvasRect = this.canvasRef?.nativeElement.getBoundingClientRect();
    if (!canvasRect) return;

    const mouseX = event.clientX - canvasRect.left;
    const mouseY = event.clientY - canvasRect.top;

    const currentZoom = this.zoom();
    const zoomFactor = event.deltaY < 0 ? 1.1 : 0.9;
    const newZoom = Math.min(2.5, Math.max(0.2, currentZoom * zoomFactor));

    const worldX = (mouseX - this.panX()) / currentZoom;
    const worldY = (mouseY - this.panY()) / currentZoom;

    this.zoom.set(newZoom);
    this.panX.set(mouseX - worldX * newZoom);
    this.panY.set(mouseY - worldY * newZoom);
  }

  zoomIn(): void {
    this.setZoomAtCenter(this.zoom() * 1.2);
  }

  zoomOut(): void {
    this.setZoomAtCenter(this.zoom() / 1.2);
  }

  resetZoom(): void {
    this.setZoomAtCenter(1);
  }

  private setZoomAtCenter(targetZoom: number): void {
    const newZoom = Math.min(2.5, Math.max(0.2, targetZoom));
    const canvasRect = this.canvasRef?.nativeElement.getBoundingClientRect();
    const cx = canvasRect ? canvasRect.width / 2 : 400;
    const cy = canvasRect ? canvasRect.height / 2 : 300;
    const worldX = (cx - this.panX()) / this.zoom();
    const worldY = (cy - this.panY()) / this.zoom();
    this.zoom.set(newZoom);
    this.panX.set(cx - worldX * newZoom);
    this.panY.set(cy - worldY * newZoom);
  }

  centerDiagram(): void {
    const cls = this.classes();
    if (cls.length === 0) {
      this.panX.set(40);
      this.panY.set(40);
      this.zoom.set(1);
      return;
    }
    let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
    cls.forEach((c) => {
      minX = Math.min(minX, c.x);
      minY = Math.min(minY, c.y);
      maxX = Math.max(maxX, c.x + DiagramComponent.BOX_HALF_WIDTH * 2);
      maxY = Math.max(maxY, c.y + this.boxHalfHeight(c) * 2);
    });
    const canvasRect = this.canvasRef?.nativeElement.getBoundingClientRect();
    const viewW = canvasRect?.width ?? 800;
    const viewH = canvasRect?.height ?? 600;
    const diagW = Math.max(100, maxX - minX + 80);
    const diagH = Math.max(100, maxY - minY + 80);
    const fitZoom = Math.min(1.2, Math.max(0.4, Math.min((viewW - 100) / diagW, (viewH - 100) / diagH)));
    const centerX = (minX + maxX) / 2;
    const centerY = (minY + maxY) / 2;
    this.zoom.set(fitZoom);
    this.panX.set(viewW / 2 - centerX * fitZoom);
    this.panY.set(viewH / 2 - centerY * fitZoom);
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
    this.multLabelOffsets.delete(relationshipId);
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

  showClearConfirm = signal(false);

  clearDiagram(): void {
    const classCount = this.classes().length;
    const relCount = this.relationships().length;
    if (classCount === 0 && relCount === 0) return;
    this.showClearConfirm.set(true);
  }

  confirmClearDiagram(): void {
    this.showClearConfirm.set(false);
    this.api.clearDiagram(this.diagramId).subscribe({
      next: (updated) => {
        this.diagram.set(updated);
        this.multLabelOffsets.clear();
      },
      error: (err) => {
        this.errorMessage.set(err?.error?.message ?? 'No se pudo limpiar el diagrama');
      },
    });
  }

  addClass(): void {
    this.autoHideChatbotOnAction();
    const count = this.classes().length;
    const canvasRect = this.canvasRef?.nativeElement.getBoundingClientRect();
    let x: number, y: number;
    if (canvasRect && canvasRect.width > 0) {
      const centerWorldX = (canvasRect.width / 2 - this.panX()) / this.zoom();
      const centerWorldY = (canvasRect.height / 2 - this.panY()) / this.zoom();
      x = Math.round(centerWorldX - 110 + ((count % 5) - 2) * 40);
      y = Math.round(centerWorldY - 70 + ((count % 5) - 2) * 30);
    } else {
      x = 80 + (count % 4) * 260;
      y = 80 + Math.floor(count / 4) * 220;
    }
    const name = this.nextClassName();
    this.api.createClass(this.diagramId, name, x, y).subscribe({
      error: (err) => this.errorMessage.set(err?.error?.message ?? 'No se pudo crear la clase'),
    });
  }

  private nextClassName(): string {
    const existing = new Set(this.classes().map((c) => c.name.toLowerCase()));
    let i = this.classes().length + 1;
    let name = `NuevaClase${i}`;
    while (existing.has(name.toLowerCase())) {
      i++;
      name = `NuevaClase${i}`;
    }
    return name;
  }

  onRename(classDto: ClassDto, newName: string): void {
    this.autoHideChatbotOnAction();
    this.api.updateClass(this.diagramId, classDto.id, { name: newName }).subscribe({
      next: (updated) => {
        this.upsertClass(updated);
        this.api.releaseLock(this.diagramId, 'CLASS', classDto.id).subscribe();
      },
      error: (err) => this.errorMessage.set(err?.error?.message ?? 'No se pudo cambiar el nombre de la clase'),
    });
  }

  onDeleteClass(classDto: ClassDto): void {
    this.autoHideChatbotOnAction();
    this.api.deleteClass(this.diagramId, classDto.id).subscribe();
  }

  onAddAttribute(classDto: ClassDto, attr: { name: string; dataType: DataType; isPrimaryKey: boolean }): void {
    this.autoHideChatbotOnAction();
    this.api.addAttribute(this.diagramId, classDto.id, attr).subscribe();
  }

  onRemoveAttribute(classDto: ClassDto, attributeId: string): void {
    this.autoHideChatbotOnAction();
    this.api.removeAttribute(this.diagramId, classDto.id, attributeId).subscribe();
  }

  // ---------- Drag & drop ----------

  onDragStart(event: MouseEvent, classDto: ClassDto): void {
    this.autoHideChatbotOnAction();
    if (this.lockOwnerNameFor(classDto.id)) return;
    this.draggingClassId = classDto.id;
    const canvasRect = this.canvasRef?.nativeElement.getBoundingClientRect();
    const mouseScreenX = event.clientX - (canvasRect?.left ?? 0);
    const mouseScreenY = event.clientY - (canvasRect?.top ?? 0);
    const worldX = (mouseScreenX - this.panX()) / this.zoom();
    const worldY = (mouseScreenY - this.panY()) / this.zoom();
    this.dragOffsetX = worldX - classDto.x;
    this.dragOffsetY = worldY - classDto.y;
    this.api.acquireLock(this.diagramId, 'CLASS', classDto.id).subscribe({ error: () => (this.draggingClassId = null) });
  }

  private updateMultLabelDrag(event: MouseEvent): void {
    if (!this.multDrag) return;
    const z = this.zoom();
    const dx = (event.clientX - this.multDrag.startX) / z;
    const dy = (event.clientY - this.multDrag.startY) / z;
    if (Math.abs(dx) > 3 || Math.abs(dy) > 3) this.multDragMoved = true;
    const current = this.multLabelOffsets.get(this.multDrag.relId) ?? { sourceDx: 0, sourceDy: 0, targetDx: 0, targetDy: 0 };
    const next = { ...current };
    if (this.multDrag.end === 'source') {
      next.sourceDx = this.multDrag.baseDx + dx;
      next.sourceDy = this.multDrag.baseDy + dy;
    } else if (this.multDrag.end === 'target') {
      next.targetDx = this.multDrag.baseDx + dx;
      next.targetDy = this.multDrag.baseDy + dy;
    } else if (this.multDrag.end === 'label') {
      next.labelDx = this.multDrag.baseDx + dx;
      next.labelDy = this.multDrag.baseDy + dy;
    }
    this.multLabelOffsets.set(this.multDrag.relId, next);
  }

  @HostListener('document:mousemove', ['$event'])
  onDocumentMouseMove(event: MouseEvent): void {
    if (this.isPanning()) {
      this.panX.set(event.clientX - this.panStartX);
      this.panY.set(event.clientY - this.panStartY);
      return;
    }
    this.updateMultLabelDrag(event);
    if (!this.draggingClassId) return;
    const current = this.classes().find((c) => c.id === this.draggingClassId);
    if (!current) {
      // La clase que estabamos arrastrando fue borrada por otro colaborador mientras tanto.
      this.draggingClassId = null;
      return;
    }
    const canvasRect = this.canvasRef?.nativeElement.getBoundingClientRect();
    const mouseScreenX = event.clientX - (canvasRect?.left ?? 0);
    const mouseScreenY = event.clientY - (canvasRect?.top ?? 0);
    const worldX = (mouseScreenX - this.panX()) / this.zoom();
    const worldY = (mouseScreenY - this.panY()) / this.zoom();
    const x = Math.round(worldX - this.dragOffsetX);
    const y = Math.round(worldY - this.dragOffsetY);
    this.upsertClass({ ...current, x, y });
  }

  @HostListener('document:mouseup')
  onDocumentMouseUp(): void {
    if (this.isPanning()) {
      this.isPanning.set(false);
    }
    this.multDrag = null;
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
    this.autoHideChatbotOnAction();
    const current = this.connectingFromClassId();
    if (current && current !== classDto.id) {
      // Si ya estábamos conectando desde otra clase y hacemos clic en el botón conectar de esta, conectamos ambas
      const source = this.classes().find((c) => c.id === current);
      if (source) {
        this.pendingRelationship.set({ sourceId: current, sourceName: source.name, targetId: classDto.id, targetName: classDto.name });
        this.connectingFromClassId.set(null);
        this.relType = 'ASSOCIATION';
        this.sourceMult = '1';
        this.targetMult = '*';
        this.relLabel = '';
        return;
      }
    }
    this.connectingFromClassId.set(this.connectingFromClassId() === classDto.id ? null : classDto.id);
  }

  onBodyClick(classDto: ClassDto): void {
    const sourceId = this.connectingFromClassId();
    if (!sourceId) return;
    this.autoHideChatbotOnAction();
    const source = this.classes().find((c) => c.id === sourceId);
    if (!source) return;
    this.pendingRelationship.set({ sourceId, sourceName: source.name, targetId: classDto.id, targetName: classDto.name });
    this.connectingFromClassId.set(null);
    this.relType = 'ASSOCIATION';
    this.sourceMult = '1';
    this.targetMult = '*';
    this.relLabel = '';
  }

  onRelTypeChange(newType: RelationshipType): void {
    this.relType = newType;
    if (newType === 'COMPOSITION' || newType === 'DEPENDENCY' || newType === 'GENERALIZATION' || newType === 'REALIZATION') {
      this.sourceMult = '';
      this.targetMult = '';
    } else if (newType === 'AGGREGATION') {
      this.sourceMult = '';
      this.targetMult = '*';
    } else if (newType === 'ASSOCIATION') {
      this.sourceMult = '1';
      this.targetMult = '*';
    }
  }

  confirmRelationship(): void {
    const pending = this.pendingRelationship();
    if (!pending) return;
    this.api
      .createRelationship(this.diagramId, {
        sourceClassId: pending.sourceId,
        targetClassId: pending.targetId,
        type: this.relType,
        sourceMultiplicity: this.sourceMult || '',
        targetMultiplicity: this.targetMult || '',
        label: this.relLabel.trim() || undefined,
      })
      .subscribe();
    this.pendingRelationship.set(null);
    this.relLabel = '';
  }

  cancelRelationship(): void {
    this.pendingRelationship.set(null);
    this.relLabel = '';
  }

  onDeleteRelationship(relationship: RelationshipDto): void {
    const labelPart = relationship.label ? ` "${relationship.label}"` : '';
    const promptText = relationship.sourceClassId === relationship.targetClassId
      ? `¿Eliminar la relación recursiva${labelPart} en "${relationship.sourceClassName}"?`
      : `¿Eliminar la relación${labelPart} entre "${relationship.sourceClassName}" y "${relationship.targetClassName}"?`;
    if (!confirm(promptText)) return;
    this.api.deleteRelationship(this.diagramId, relationship.id).subscribe({
      next: () => {
        this.removeRelationship(relationship.id);
      },
      error: (err) => {
        this.errorMessage.set(err?.error?.message ?? 'No se pudo eliminar la relación');
      },
    });
  }

  private static readonly BOX_HALF_WIDTH = 115; // 230px / 2

  private boxHalfHeight(c: ClassDto): number {
    const headerHeight = 62;
    const lockHeight = this.lockOwnerNameFor(c.id) ? 21 : 0;
    const attrsHeight = c.attributes && c.attributes.length > 0
      ? 8 + c.attributes.length * 22
      : 27;
    const footerHeight = 29;
    const total = headerHeight + lockHeight + attrsHeight + footerHeight;
    return total / 2;
  }

  /** Punto donde un rayo desde el centro de una caja (hw x 2hh), en direccion (dx,dy), cruza su borde. */
  private edgeIntersection(cx: number, cy: number, hw: number, hh: number, dx: number, dy: number): { x: number; y: number } {
    if (dx === 0 && dy === 0) return { x: cx, y: cy };
    const tX = dx !== 0 ? hw / Math.abs(dx) : Infinity;
    const tY = dy !== 0 ? hh / Math.abs(dy) : Infinity;
    const t = Math.min(tX, tY);
    return { x: cx + dx * t, y: cy + dy * t };
  }

  relationshipPath(r: RelationshipDto): {
    isSelf: boolean;
    d?: string;
    x1: number; y1: number; x2: number; y2: number;
    midX: number; midY: number;
    labelX: number; labelY: number;
    sourceLabelX: number; sourceLabelY: number;
    targetLabelX: number; targetLabelY: number;
  } {
    const source = this.classes().find((c) => c.id === r.sourceClassId);
    const target = this.classes().find((c) => c.id === r.targetClassId);
    if (!source || !target) {
      return { isSelf: false, x1: 0, y1: 0, x2: 0, y2: 0, midX: 0, midY: 0, labelX: 0, labelY: 0, sourceLabelX: 0, sourceLabelY: 0, targetLabelX: 0, targetLabelY: 0 };
    }

    const offset = this.multLabelOffsets.get(r.id);

    // --- Relación recursiva (reflexiva / a sí misma) ---
    if (r.sourceClassId === r.targetClassId) {
      const boxW = DiagramComponent.BOX_HALF_WIDTH * 2;
      const boxH = this.boxHalfHeight(source) * 2;

      // Detectar índice si hay múltiples relaciones recursivas en la misma clase
      const selfRels = this.relationships().filter((rel) => rel.sourceClassId === r.sourceClassId && rel.targetClassId === r.sourceClassId);
      const selfIndex = Math.max(0, selfRels.findIndex((rel) => rel.id === r.id));
      const loopWidth = 46 + selfIndex * 28;

      // Puntos de salida y entrada en el lateral derecho de la clase
      const x1 = source.x + boxW;
      const y1 = source.y + Math.min(34, boxH * 0.32);
      const x2 = source.x + boxW;
      const y2 = source.y + Math.max(boxH - 34, Math.min(boxH * 0.72, y1 + 52));

      const d = `M ${x1} ${y1} L ${x1 + loopWidth} ${y1} L ${x1 + loopWidth} ${y2} L ${x2} ${y2}`;

      return {
        isSelf: true,
        d,
        x1, y1, x2, y2,
        midX: x1 + loopWidth,
        midY: (y1 + y2) / 2,
        labelX: x1 + loopWidth + 24 + (offset?.labelDx ?? 0),
        labelY: (y1 + y2) / 2 + (offset?.labelDy ?? 0),
        sourceLabelX: x1 + 18 + (offset?.sourceDx ?? 0),
        sourceLabelY: y1 - 13 + (offset?.sourceDy ?? 0),
        targetLabelX: x2 + 18 + (offset?.targetDx ?? 0),
        targetLabelY: y2 + 13 + (offset?.targetDy ?? 0),
      };
    }

    // --- Relación entre dos clases distintas ---
    const hw = DiagramComponent.BOX_HALF_WIDTH;
    const sourceCenter = { x: source.x + hw, y: source.y + this.boxHalfHeight(source) };
    const targetCenter = { x: target.x + hw, y: target.y + this.boxHalfHeight(target) };
    const dx = targetCenter.x - sourceCenter.x;
    const dy = targetCenter.y - sourceCenter.y;
    const length = Math.hypot(dx, dy) || 1;
    const ux = dx / length;
    const uy = dy / length;

    // Detectar si hay múltiples relaciones entre este mismo par de clases para trazar líneas paralelas separadas
    const aId = r.sourceClassId;
    const bId = r.targetClassId;
    const group = this.relationships().filter(
      (rel) =>
        (rel.sourceClassId === aId && rel.targetClassId === bId) ||
        (rel.sourceClassId === bId && rel.targetClassId === aId)
    );
    const count = group.length;
    const index = Math.max(0, group.findIndex((rel) => rel.id === r.id));

    let parallelOffset = 0;
    if (count > 1) {
      const step = count === 2 ? 40 : 28;
      const rawOffset = (index - (count - 1) / 2) * step;
      // Normalizar dirección según el orden canónico de IDs para evitar solapamiento si van en sentidos opuestos
      parallelOffset = (aId < bId) ? rawOffset : -rawOffset;
    }

    // Vector perpendicular al segmento origen-destino (-uy, ux)
    const perpX = -uy;
    const perpY = ux;

    const shiftedSourceCenter = {
      x: sourceCenter.x + perpX * parallelOffset,
      y: sourceCenter.y + perpY * parallelOffset,
    };
    const shiftedTargetCenter = {
      x: targetCenter.x + perpX * parallelOffset,
      y: targetCenter.y + perpY * parallelOffset,
    };

    const p1 = this.edgeIntersection(shiftedSourceCenter.x, shiftedSourceCenter.y, hw, this.boxHalfHeight(source), dx, dy);
    const p2 = this.edgeIntersection(shiftedTargetCenter.x, shiftedTargetCenter.y, hw, this.boxHalfHeight(target), -dx, -dy);

    const actualLength = Math.hypot(p2.x - p1.x, p2.y - p1.y) || 1;
    const actualUx = (p2.x - p1.x) / actualLength;
    const actualUy = (p2.y - p1.y) / actualLength;

    // Distancia a lo largo de la linea desde el borde de la clase (para no superponer marcadores/flechas)
    const alongDist = Math.min(22, Math.max(12, actualLength * 0.25));
    const perpDist = 13;

    // Normal para situar la cardinalidad de forma clara a un costado/arriba de la linea
    const srcSide = (actualUy > 0 || (actualUy === 0 && actualUx > 0)) ? -1 : 1;
    const srcNormX = srcSide * (-actualUy);
    const srcNormY = srcSide * actualUx;

    const tgtUx = -actualUx;
    const tgtUy = -actualUy;
    const tgtSide = (tgtUy > 0 || (tgtUy === 0 && tgtUx > 0)) ? -1 : 1;
    const tgtNormX = tgtSide * (-tgtUy);
    const tgtNormY = tgtSide * tgtUx;

    // Posicion para el nombre/rol de la relacion (ej: "vende", "compra")
    const labelX = (p1.x + p2.x) / 2 + srcNormX * 14 + (offset?.labelDx ?? 0);
    const labelY = (p1.y + p2.y) / 2 + srcNormY * 14 + (offset?.labelDy ?? 0);

    return {
      isSelf: false,
      x1: p1.x, y1: p1.y, x2: p2.x, y2: p2.y,
      midX: (p1.x + p2.x) / 2,
      midY: (p1.y + p2.y) / 2,
      labelX,
      labelY,
      sourceLabelX: p1.x + actualUx * alongDist + srcNormX * perpDist + (offset?.sourceDx ?? 0),
      sourceLabelY: p1.y + actualUy * alongDist + srcNormY * perpDist + (offset?.sourceDy ?? 0),
      targetLabelX: p2.x + tgtUx * alongDist + tgtNormX * perpDist + (offset?.targetDx ?? 0),
      targetLabelY: p2.y + tgtUy * alongDist + tgtNormY * perpDist + (offset?.targetDy ?? 0),
    };
  }

  // ---------- Arrastre de etiquetas de cardinalidad y nombres ----------

  onMultLabelMouseDown(event: MouseEvent, relationshipId: string, end: 'source' | 'target'): void {
    this.autoHideChatbotOnAction();
    event.stopPropagation();
    event.preventDefault();
    const current = this.multLabelOffsets.get(relationshipId) ?? { sourceDx: 0, sourceDy: 0, targetDx: 0, targetDy: 0 };
    this.multDragMoved = false;
    this.multDrag = {
      relId: relationshipId,
      end,
      startX: event.clientX,
      startY: event.clientY,
      baseDx: end === 'source' ? current.sourceDx : current.targetDx,
      baseDy: end === 'source' ? current.sourceDy : current.targetDy,
    };
  }

  onLabelMouseDown(event: MouseEvent, relationshipId: string): void {
    this.autoHideChatbotOnAction();
    event.stopPropagation();
    event.preventDefault();
    const current = this.multLabelOffsets.get(relationshipId) ?? { sourceDx: 0, sourceDy: 0, targetDx: 0, targetDy: 0 };
    this.multDragMoved = false;
    this.multDrag = {
      relId: relationshipId,
      end: 'label',
      startX: event.clientX,
      startY: event.clientY,
      baseDx: current.labelDx ?? 0,
      baseDy: current.labelDy ?? 0,
    };
  }

  /** Flecha en la punta destino, segun notacion UML estandar (ver imagen de referencia). */
  relationshipMarkerEnd(r: RelationshipDto): string | null {
    switch (r.type) {
      case 'ASSOCIATION':
      case 'DEPENDENCY':
        return 'url(#rel-arrow-association)';
      case 'GENERALIZATION':
      case 'REALIZATION':
        return 'url(#rel-arrow-triangle)';
      default:
        return null;
    }
  }

  /** Rombo en la punta origen (el lado "todo") para agregacion/composicion. */
  relationshipMarkerStart(r: RelationshipDto): string | null {
    switch (r.type) {
      case 'AGGREGATION':
        return 'url(#rel-diamond-hollow)';
      case 'COMPOSITION':
        return 'url(#rel-diamond-filled)';
      default:
        return null;
    }
  }

  // ---------- IA ----------

  onAiCommand(text: string): void {
    this.aiLog.set([...this.aiLog(), { role: 'user', text }]);
    this.aiBusy.set(true);
    this.api.sendAiCommand(this.diagramId, text).subscribe({
      next: (res) => {
        this.aiLog.set([...this.aiLog(), { role: 'assistant', text: res.assistantMessage }]);
        if (res.diagram) {
          this.diagram.set(res.diagram);
        }
        this.aiBusy.set(false);
      },
      error: (err) => {
        this.aiLog.set([...this.aiLog(), { role: 'error', text: err?.error?.message ?? 'Error del asistente' }]);
        this.aiBusy.set(false);
      },
    });
  }

  onAiImage(file: File): void {
    this.aiLog.set([...this.aiLog(), { role: 'user', text: `[Imagen] ${file.name}` }]);
    this.aiBusy.set(true);
    this.api.sendAiImage(this.diagramId, file).subscribe({
      next: (res) => {
        this.aiLog.set([...this.aiLog(), { role: 'assistant', text: res.assistantMessage }]);
        if (res.diagram) {
          this.diagram.set(res.diagram);
        }
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
      next: (res) => {
        this.aiLog.set([...this.aiLog(), { role: 'assistant', text: res.assistantMessage }]);
        if (res.diagram) {
          this.diagram.set(res.diagram);
        }
      },
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
