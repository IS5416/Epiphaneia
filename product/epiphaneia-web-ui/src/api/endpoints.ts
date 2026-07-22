import { request, getList } from './client';
import type {
  LoginRequest,
  LoginResponse,
  ChangePasswordRequest,
  CreateApiTokenRequest,
  ApiTokenResponse,
  CreateApiTokenResponse,
  ApplicationRequest,
  ApplicationResponse,
  ProbeResponse,
  DataSourceRequest,
  DataSourceResponse,
  TestConnectionResponse,
  LlmProviderRequest,
  LlmProviderResponse,
  CreateConversationRequest,
  ConversationResponse,
  ConversationDetailResponse,
  ReportResponse,
  SystemStatusResponse,
} from '../types';

// === Auth ===

export const login = (body: LoginRequest) =>
  request<LoginResponse>('/auth/login', {
    method: 'POST',
    body: JSON.stringify(body),
  });

export const changePassword = (body: ChangePasswordRequest) =>
  request<void>('/auth/change-password', {
    method: 'POST',
    body: JSON.stringify(body),
  });

export const listTokens = () =>
  request<ApiTokenResponse[]>('/auth/tokens');

export const createToken = (body: CreateApiTokenRequest) =>
  request<CreateApiTokenResponse>('/auth/tokens', {
    method: 'POST',
    body: JSON.stringify(body),
  });

export const deleteToken = (id: string) =>
  request<void>(`/auth/tokens/${id}`, { method: 'DELETE' });

// === Applications ===

export const listApplications = () =>
  getList<ApplicationResponse>('/applications');

export const createApplication = (body: ApplicationRequest) =>
  request<ApplicationResponse>('/applications', {
    method: 'POST',
    body: JSON.stringify(body),
  });

export const getApplication = (id: string) =>
  request<ApplicationResponse>(`/applications/${id}`);

export const updateApplication = (id: string, body: ApplicationRequest) =>
  request<ApplicationResponse>(`/applications/${id}`, {
    method: 'PUT',
    body: JSON.stringify(body),
  });

export const deleteApplication = (id: string) =>
  request<void>(`/applications/${id}`, { method: 'DELETE' });

export const probeApplication = (id: string) =>
  request<ProbeResponse>(`/applications/${id}/probe`, { method: 'POST' });

// === DataSources ===

export const listDataSources = () =>
  getList<DataSourceResponse>('/datasources');

export const createDataSource = (body: DataSourceRequest) =>
  request<DataSourceResponse>('/datasources', {
    method: 'POST',
    body: JSON.stringify(body),
  });

export const getDataSource = (id: string) =>
  request<DataSourceResponse>(`/datasources/${id}`);

export const updateDataSource = (id: string, body: DataSourceRequest) =>
  request<DataSourceResponse>(`/datasources/${id}`, {
    method: 'PUT',
    body: JSON.stringify(body),
  });

export const deleteDataSource = (id: string) =>
  request<void>(`/datasources/${id}`, { method: 'DELETE' });

export const testDataSource = (id: string) =>
  request<TestConnectionResponse>(`/datasources/${id}/test`, { method: 'POST' });

// === LLM ===

export const getLlmConfig = () =>
  request<LlmProviderResponse>('/llm');

export const updateLlmConfig = (body: LlmProviderRequest) =>
  request<LlmProviderResponse>('/llm', {
    method: 'PUT',
    body: JSON.stringify(body),
  });

export const testLlm = (body: LlmProviderRequest) =>
  request<TestConnectionResponse>('/llm/test', {
    method: 'POST',
    body: JSON.stringify(body),
  });

// === Conversations ===

export const listConversations = (appId?: string, keyword?: string) => {
  const params = new URLSearchParams();
  if (appId) params.set('appId', appId);
  if (keyword) params.set('keyword', keyword);
  const qs = params.toString();
  return getList<ConversationResponse>(`/conversations${qs ? `?${qs}` : ''}`);
};

export const createConversation = (body: CreateConversationRequest) =>
  request<ConversationResponse>('/conversations', {
    method: 'POST',
    body: JSON.stringify(body),
  });

export const getConversation = (id: string) =>
  request<ConversationDetailResponse>(`/conversations/${id}`);

export const deleteConversation = (id: string) =>
  request<void>(`/conversations/${id}`, { method: 'DELETE' });

export const getReport = (id: string) =>
  request<ReportResponse>(`/conversations/${id}/report`);

// === System ===

export const getSystemStatus = () =>
  request<SystemStatusResponse>('/system/status');
