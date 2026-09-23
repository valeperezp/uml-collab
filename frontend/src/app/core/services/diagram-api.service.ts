import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AiCommandResponse,
  AiTestRequest,
  AiTestResponse,
  AttributeDto,
  ClassDto,
  DataType,
  DiagramDetail,
  DiagramSummary,
  LockDto,
  LockedElementType,
  RelationshipDto,
  RelationshipType,
  UserAiConfig,
  UserAiConfigUpdate,
  Visibility,
} from '../models/models';

@Injectable({ providedIn: 'root' })
export class DiagramApiService {
  private base = environment.apiBaseUrl;

  constructor(private http: HttpClient) {}

  // --- Diagramas ---
  listMine(): Observable<DiagramSummary[]> {
    return this.http.get<DiagramSummary[]>(`${this.base}/diagrams`);
  }

  create(name: string, description: string): Observable<DiagramSummary> {
    return this.http.post<DiagramSummary>(`${this.base}/diagrams`, { name, description });
  }

  getDetail(diagramId: string): Observable<DiagramDetail> {
    return this.http.get<DiagramDetail>(`${this.base}/diagrams/${diagramId}`);
  }

  joinByCode(code: string): Observable<DiagramSummary> {
    return this.http.get<DiagramSummary>(`${this.base}/diagrams/join/${code}`);
  }

  clearDiagram(diagramId: string): Observable<DiagramDetail> {
    return this.http.post<DiagramDetail>(`${this.base}/diagrams/${diagramId}/clear`, {});
  }

  deleteDiagram(diagramId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/diagrams/${diagramId}`);
  }

  leaveDiagram(diagramId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/diagrams/${diagramId}/leave`);
  }

  // --- Clases ---
  createClass(diagramId: string, name: string, x: number, y: number): Observable<ClassDto> {
    return this.http.post<ClassDto>(`${this.base}/diagrams/${diagramId}/classes`, { name, x, y, isAbstract: false });
  }

  updateClass(diagramId: string, classId: string, patch: Partial<{ name: string; x: number; y: number; isAbstract: boolean; stereotype: string }>): Observable<ClassDto> {
    return this.http.put<ClassDto>(`${this.base}/diagrams/${diagramId}/classes/${classId}`, patch);
  }

  deleteClass(diagramId: string, classId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/diagrams/${diagramId}/classes/${classId}`);
  }

  addAttribute(diagramId: string, classId: string, attribute: {
    name: string; dataType: DataType; visibility?: Visibility; isPrimaryKey?: boolean; nullable?: boolean; unique?: boolean;
  }): Observable<AttributeDto> {
    return this.http.post<AttributeDto>(`${this.base}/diagrams/${diagramId}/classes/${classId}/attributes`, attribute);
  }

  updateAttribute(diagramId: string, classId: string, attributeId: string, attribute: {
    name: string; dataType: DataType; visibility?: Visibility; isPrimaryKey?: boolean; nullable?: boolean; unique?: boolean;
  }): Observable<AttributeDto> {
    return this.http.put<AttributeDto>(`${this.base}/diagrams/${diagramId}/classes/${classId}/attributes/${attributeId}`, attribute);
  }

  removeAttribute(diagramId: string, classId: string, attributeId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/diagrams/${diagramId}/classes/${classId}/attributes/${attributeId}`);
  }

  // --- Relaciones ---
  createRelationship(diagramId: string, req: {
    sourceClassId: string; targetClassId: string; type: RelationshipType;
    sourceMultiplicity?: string; targetMultiplicity?: string; sourceRoleName?: string; targetRoleName?: string;
    label?: string;
  }): Observable<RelationshipDto> {
    return this.http.post<RelationshipDto>(`${this.base}/diagrams/${diagramId}/relationships`, req);
  }

  deleteRelationship(diagramId: string, relationshipId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/diagrams/${diagramId}/relationships/${relationshipId}`);
  }

  // --- Locks ---
  acquireLock(diagramId: string, type: LockedElementType, elementId: string): Observable<LockDto> {
    return this.http.post<LockDto>(`${this.base}/diagrams/${diagramId}/locks/${type}/${elementId}/acquire`, {});
  }

  heartbeatLock(diagramId: string, type: LockedElementType, elementId: string): Observable<void> {
    return this.http.post<void>(`${this.base}/diagrams/${diagramId}/locks/${type}/${elementId}/heartbeat`, {});
  }

  releaseLock(diagramId: string, type: LockedElementType, elementId: string): Observable<void> {
    return this.http.post<void>(`${this.base}/diagrams/${diagramId}/locks/${type}/${elementId}/release`, {});
  }

  activeLocks(diagramId: string): Observable<LockDto[]> {
    return this.http.get<LockDto[]>(`${this.base}/diagrams/${diagramId}/locks`);
  }

  // --- IA ---
  sendAiCommand(diagramId: string, command: string): Observable<AiCommandResponse> {
    return this.http.post<AiCommandResponse>(`${this.base}/diagrams/${diagramId}/ai/command`, { command });
  }

  sendAiImage(diagramId: string, file: File): Observable<AiCommandResponse> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<AiCommandResponse>(`${this.base}/diagrams/${diagramId}/ai/image`, form);
  }

  // --- Configuración de Credenciales de IA del Usuario ---
  getAiConfig(): Observable<UserAiConfig> {
    return this.http.get<UserAiConfig>(`${this.base}/user/ai-config`);
  }

  updateAiConfig(req: UserAiConfigUpdate): Observable<UserAiConfig> {
    return this.http.put<UserAiConfig>(`${this.base}/user/ai-config`, req);
  }

  resetAiConfig(): Observable<UserAiConfig> {
    return this.http.delete<UserAiConfig>(`${this.base}/user/ai-config`);
  }

  testAiConfig(req: AiTestRequest): Observable<AiTestResponse> {
    return this.http.post<AiTestResponse>(`${this.base}/user/ai-config/test`, req);
  }

  // --- XMI / Enterprise Architect ---
  exportXmi(diagramId: string): Observable<Blob> {
    return this.http.get(`${this.base}/diagrams/${diagramId}/xmi/export`, { responseType: 'blob' });
  }

  importXmi(diagramId: string, file: File): Observable<AiCommandResponse> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<AiCommandResponse>(`${this.base}/diagrams/${diagramId}/xmi/import`, form);
  }

  // --- Generador de backend ---
  generateBackend(diagramId: string): Observable<Blob> {
    return this.http.get(`${this.base}/diagrams/${diagramId}/codegen/backend`, { responseType: 'blob' });
  }
}
