export type DataType = 'STRING' | 'TEXT' | 'INTEGER' | 'LONG' | 'DOUBLE' | 'DECIMAL' | 'BOOLEAN' | 'DATE' | 'DATETIME' | 'UUID';
export type Visibility = 'PUBLIC' | 'PRIVATE' | 'PROTECTED' | 'PACKAGE';
export type RelationshipType = 'ASSOCIATION' | 'AGGREGATION' | 'COMPOSITION' | 'GENERALIZATION' | 'REALIZATION';
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
