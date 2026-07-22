// === Response Wrappers ===

export interface ApiResponse<T> {
  data?: T;
  error?: ErrorDetail;
}

export interface ApiListResponse<T> {
  data: T[];
  total: number;
}

export interface ErrorDetail {
  code: string;
  message: string;
  details?: string[];
}

// === Auth ===

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  token: string | null;
  prefix: string | null;
  mustChangePassword: boolean;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export interface CreateApiTokenRequest {
  name: string;
}

export interface ApiTokenResponse {
  id: string;
  name: string;
  prefix: string;
  createdAt: string;
}

export interface CreateApiTokenResponse {
  id: string;
  name: string;
  token: string;
  prefix: string;
  createdAt: string;
}

// === Application ===

export interface ApplicationRequest {
  name: string;
  actuatorUrl?: string;
  prometheusLabel: string;
  tags?: string;
}

export interface ApplicationResponse {
  id: string;
  name: string;
  actuatorUrl?: string;
  prometheusLabel: string;
  tags?: string;
  actuatorInfo?: string;
  createdAt: string;
}

export interface ProbeResponse {
  id: string;
  healthy: boolean;
  info: string;
}

// === DataSource ===

export interface DataSourceRequest {
  type: string;
  name: string;
  url: string;
  authType?: string;
  authConfig?: string;
}

export interface DataSourceResponse {
  id: string;
  type: string;
  name: string;
  url: string;
  authType?: string;
  connected: boolean;
  metadata?: string;
  createdAt: string;
  updatedAt: string;
}

// === LLM ===

export interface LlmProviderRequest {
  provider: string;
  modelName: string;
  apiKey?: string;
  baseUrl?: string;
}

export interface LlmProviderResponse {
  id: string;
  provider: string;
  modelName: string;
  baseUrl?: string;
  connected: boolean;
  updatedAt: string;
}

export interface TestConnectionResponse {
  success: boolean;
  message: string;
}

// === Conversation ===

export interface CreateConversationRequest {
  applicationId: string;
  title: string;
  question: string;
}

export interface ConversationResponse {
  id: string;
  applicationId: string;
  applicationName: string;
  title: string;
  createdAt: string;
  updatedAt: string;
  lastMessage?: string;
}

export interface ConversationDetailResponse {
  id: string;
  applicationId: string;
  applicationName: string;
  title: string;
  createdAt: string;
  updatedAt: string;
  lastMessage?: string;
  messages: MessageResponse[];
}

export interface MessageResponse {
  id: string;
  role: string;
  content: string;
  diagnosisState?: string;
  failureReason?: string;
  riskLevel?: string;
  riskImpact?: string;
  riskUrgency?: string;
  tokenCount?: number;
  createdAt: string;
  completedAt?: string;
  evidence: EvidenceResponse[];
  hypotheses: RootCauseHypothesisResponse[];
  suggestions: FixSuggestionResponse[];
}

export interface EvidenceResponse {
  id: string;
  source: string;
  queryText: string;
  summary?: string;
  anomalyStart?: string;
  anomalyEnd?: string;
  collectedAt: string;
}

export interface RootCauseHypothesisResponse {
  id: string;
  rank: number;
  description: string;
  confidence: number;
  supportingEvidenceIds: string;
}

export interface FixSuggestionResponse {
  id: string;
  description: string;
  autoExecutionAllowed: boolean;
}

// === Report ===

export interface ReportResponse {
  content: string;
}

// === System ===

export interface SystemStatusResponse {
  bootstrapped: boolean;
  adminExists: boolean;
  llmConfigured: boolean;
  dataSourcesCount: number;
  applicationsCount: number;
}

// === SSE Events ===

export interface SseStateEvent {
  conversationId: string;
  messageId: string;
  state: string;
}

export interface SseStepEvent {
  conversationId: string;
  messageId: string;
  step: string;
}

export interface SseTokenEvent {
  conversationId: string;
  messageId: string;
  token: string;
}

export interface SseDoneEvent {
  conversationId: string;
  messageId: string;
  state: string;
}

export interface SseErrorEvent {
  conversationId: string;
  messageId: string;
  error: string;
}

export interface SseCloseEvent {
  conversationId: string;
}

export type SseEvent =
  | { type: 'state'; data: SseStateEvent }
  | { type: 'step'; data: SseStepEvent }
  | { type: 'token'; data: SseTokenEvent }
  | { type: 'done'; data: SseDoneEvent }
  | { type: 'error'; data: SseErrorEvent }
  | { type: 'close'; data: SseCloseEvent };
