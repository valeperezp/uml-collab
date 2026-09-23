export type DataType = 'STRING' | 'TEXT' | 'INTEGER' | 'LONG' | 'DOUBLE' | 'DECIMAL' | 'BOOLEAN' | 'DATE' | 'DATETIME' | 'UUID';
export type Visibility = 'PUBLIC' | 'PRIVATE' | 'PROTECTED' | 'PACKAGE';
export type RelationshipType = 'ASSOCIATION' | 'AGGREGATION' | 'COMPOSITION' | 'GENERALIZATION' | 'REALIZATION' | 'DEPENDENCY';
export type LockedElementType = 'CLASS' | 'RELATIONSHIP';

export interface AuthResponse {
  token: string;
  userId: string;
  username: string;
  displayName: string;
  colorHex: string;
}

export interface DiagramSummary {
  id: string;
  name: string;
  description?: string;
  ownerId: string;
  ownerName?: string;
  isOwner?: boolean;
  joinCode: string;
  createdAt: string;
  updatedAt: string;
  classCount: number;
}

export interface AttributeDto {
  id: string;
  name: string;
  dataType: DataType;
  visibility: Visibility;
  isPrimaryKey: boolean;
  nullable: boolean;
  unique: boolean;
  orderIndex: number;
}

export interface ClassDto {
  id: string;
  name: string;
  stereotype?: string;
  isAbstract: boolean;
  x: number;
  y: number;
  attributes: AttributeDto[];
}

export interface RelationshipDto {
  id: string;
  sourceClassId: string;
  sourceClassName: string;
  targetClassId: string;
  targetClassName: string;
  type: RelationshipType;
  sourceMultiplicity: string;
  targetMultiplicity: string;
  sourceRoleName?: string;
  targetRoleName?: string;
  label?: string;
}

export interface DiagramDetail {
  id: string;
  name: string;
  description?: string;
  ownerId: string;
  joinCode: string;
  updatedAt: string;
  classes: ClassDto[];
  relationships: RelationshipDto[];
}

export interface LockDto {
  elementType: LockedElementType;
  elementId: string;
  userId: string;
  userDisplayName?: string;
  acquiredAt?: string;
  granted: boolean;
}

export interface PresenceInfo {
  userId: string;
  username: string;
  displayName: string;
  colorHex: string;
}

export interface DiagramEvent {
  type: 'CLASS_CREATED' | 'CLASS_UPDATED' | 'CLASS_DELETED' | 'RELATIONSHIP_CREATED' | 'RELATIONSHIP_UPDATED' |
        'RELATIONSHIP_DELETED' | 'LOCK_ACQUIRED' | 'LOCK_RELEASED' | 'AI_OPERATIONS_APPLIED' | 'DIAGRAM_REPLACED';
  actorUserId: string;
  actorDisplayName?: string;
  payload: unknown;
  timestamp: string;
}

export interface AiCommandResponse {
  assistantMessage: string;
  appliedOperations: unknown[];
  diagram: DiagramDetail;
}

export interface UserAiConfig {
  provider: string;
  model: string;
  baseUrl: string;
  hasCustomApiKey: boolean;
  maskedApiKey: string | null;
  customEnabled: boolean;
  configured: boolean;
  effectiveProvider: string;
  effectiveModel: string;
  systemDefaultAvailable: boolean;
  systemProvider?: string;
  systemModel?: string;
  systemBaseUrl?: string;
  systemConfigured?: boolean;
}

export interface UserAiConfigUpdate {
  provider?: string;
  apiKey?: string;
  model?: string;
  baseUrl?: string;
  customEnabled?: boolean;
  clearApiKey?: boolean;
  saveAsSystemDefault?: boolean;
}

export interface AiTestRequest {
  provider?: string;
  apiKey?: string;
  model?: string;
  baseUrl?: string;
  useSavedKey?: boolean;
}

export interface AiTestResponse {
  success: boolean;
  message: string;
  latencyMs?: number;
  provider?: string;
  model?: string;
}

