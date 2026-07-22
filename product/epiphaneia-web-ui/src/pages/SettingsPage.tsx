import { useEffect, useState } from 'react';
import {
  listDataSources, createDataSource, updateDataSource, deleteDataSource, testDataSource,
  getLlmConfig, updateLlmConfig, testLlm,
  listApplications, createApplication, updateApplication, deleteApplication,
  listTokens, createToken, deleteToken,
  changePassword,
} from '../api/endpoints';
import type {
  DataSourceResponse, DataSourceRequest,
  LlmProviderResponse,
  ApplicationResponse, ApplicationRequest,
  ApiTokenResponse, CreateApiTokenResponse,
} from '../types';
import ErrorAlert from '../components/ErrorAlert';
import LoadingSpinner from '../components/LoadingSpinner';
import ConfirmDialog from '../components/ConfirmDialog';

type Tab = 'datasources' | 'llm' | 'applications' | 'tokens' | 'password';

const TABS: { key: Tab; label: string }[] = [
  { key: 'datasources', label: 'Data Sources' },
  { key: 'llm', label: 'LLM' },
  { key: 'applications', label: 'Applications' },
  { key: 'tokens', label: 'API Tokens' },
  { key: 'password', label: 'Password' },
];

export default function SettingsPage() {
  const [tab, setTab] = useState<Tab>('datasources');

  return (
    <div>
      <div className="page-header"><h1>Settings</h1></div>
      <div className="tabs">
        {TABS.map((t) => (
          <button
            key={t.key}
            className={`tab${tab === t.key ? ' active' : ''}`}
            onClick={() => setTab(t.key)}
          >
            {t.label}
          </button>
        ))}
      </div>
      {tab === 'datasources' && <DataSourcesTab />}
      {tab === 'llm' && <LlmTab />}
      {tab === 'applications' && <ApplicationsTab />}
      {tab === 'tokens' && <TokensTab />}
      {tab === 'password' && <PasswordTab />}
    </div>
  );
}

function DataSourcesTab() {
  const [items, setItems] = useState<DataSourceResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [form, setForm] = useState<DataSourceRequest>({ type: '', name: '', url: '' });
  const [editingId, setEditingId] = useState<string | null>(null);
  const [testMsg, setTestMsg] = useState<{ text: string; ok: boolean } | null>(null);

  const fetchData = async () => {
    setLoading(true);
    try {
      const r = await listDataSources();
      setItems(r.data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchData(); }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    try {
      if (editingId) {
        await updateDataSource(editingId, form);
      } else {
        await createDataSource(form);
      }
      setForm({ type: '', name: '', url: '' });
      setEditingId(null);
      fetchData();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Save failed');
    }
  };

  const handleDelete = async (id: string) => {
    try {
      await deleteDataSource(id);
      fetchData();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Delete failed');
    }
  };

  const handleTest = async (id: string) => {
    try {
      const r = await testDataSource(id);
      setError(null);
      setTestMsg({ text: r.success ? 'Connection OK' : r.message, ok: r.success });
    } catch (err) {
      setTestMsg({ text: err instanceof Error ? err.message : 'Test failed', ok: false });
    }
  };

  if (loading) return <LoadingSpinner size="lg" />;

  return (
    <div>
      {error && <ErrorAlert error={error} onDismiss={() => setError(null)} />}
      {testMsg && (
        <div className="alert" style={{ background: testMsg.ok ? 'rgba(74,222,128,0.15)' : 'rgba(248,113,113,0.15)', color: testMsg.ok ? 'var(--color-success)' : 'var(--color-danger)' }}>
          {testMsg.text}
          <button className="btn-ghost btn-sm" style={{ marginLeft: 12 }} onClick={() => setTestMsg(null)}>Dismiss</button>
        </div>
      )}
      <form onSubmit={handleSubmit} className="card">
        <div className="form-row">
          <div className="form-group">
            <label className="form-label">Type</label>
            <select className="form-input" value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value })}>
              <option value="">Select...</option>
              <option value="PROMETHEUS">Prometheus</option>
              <option value="ELASTICSEARCH">Elasticsearch</option>
              <option value="ACTUATOR">Actuator</option>
            </select>
          </div>
          <div className="form-group">
            <label className="form-label">Name</label>
            <input className="form-input" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
          </div>
        </div>
        <div className="form-group">
          <label className="form-label">URL</label>
          <input className="form-input" value={form.url} onChange={(e) => setForm({ ...form, url: e.target.value })} />
        </div>
        <button className="btn-primary btn-sm" type="submit">
          {editingId ? 'Update' : 'Add Data Source'}
        </button>
        {editingId && (
          <button className="btn-ghost btn-sm" style={{ marginLeft: 8 }} type="button" onClick={() => { setEditingId(null); setForm({ type: '', name: '', url: '' }); }}>
            Cancel
          </button>
        )}
      </form>

      {items.length > 0 && (
        <div className="card" style={{ padding: 0 }}>
          <table className="table">
            <thead>
              <tr><th>Name</th><th>Type</th><th>URL</th><th></th></tr>
            </thead>
            <tbody>
              {items.map((ds) => (
                <tr key={ds.id}>
                  <td>{ds.name}</td>
                  <td><span className="badge badge-muted">{ds.type}</span></td>
                  <td style={{ fontFamily: 'var(--font-mono)', fontSize: 13 }}>{ds.url}</td>
                  <td>
                    <button className="btn-ghost btn-sm" onClick={() => handleTest(ds.id)}>Test</button>
                    <button className="btn-ghost btn-sm" onClick={() => { setEditingId(ds.id); setForm({ type: ds.type, name: ds.name, url: ds.url }); }}>Edit</button>
                    <button className="btn-ghost btn-sm" onClick={() => handleDelete(ds.id)}>Del</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

function LlmTab() {
  const [config, setConfig] = useState<LlmProviderResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [form, setForm] = useState({ provider: 'OPENAI', modelName: '', apiKey: '', baseUrl: '' });
  const [testMsg, setTestMsg] = useState<string | null>(null);

  const fetchData = async () => {
    try {
      const r = await getLlmConfig();
      setConfig(r);
      setForm({ provider: r.provider, modelName: r.modelName, apiKey: '', baseUrl: r.baseUrl ?? '' });
    } catch {
      // no config yet — normal
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchData(); }, []);

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    try {
      const body = { provider: form.provider, modelName: form.modelName, baseUrl: form.baseUrl || undefined };
      const r = await updateLlmConfig(
        form.apiKey ? { ...body, apiKey: form.apiKey } : body,
      );
      setConfig(r);
      setForm((f) => ({ ...f, apiKey: '' }));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Save failed');
    }
  };

  const handleTest = async () => {
    setTestMsg(null);
    try {
      const r = await testLlm({ provider: form.provider, modelName: form.modelName, apiKey: form.apiKey || undefined, baseUrl: form.baseUrl || undefined });
      setTestMsg(r.success ? 'Connection OK' : r.message);
    } catch (err) {
      setTestMsg(err instanceof Error ? err.message : 'Test failed');
    }
  };

  if (loading) return <LoadingSpinner size="lg" />;

  return (
    <div>
      {error && <ErrorAlert error={error} onDismiss={() => setError(null)} />}
      <form onSubmit={handleSave} className="card">
        <div className="form-row">
          <div className="form-group">
            <label className="form-label">Provider</label>
            <select className="form-input" value={form.provider} onChange={(e) => setForm({ ...form, provider: e.target.value })}>
              <option value="OPENAI">OpenAI</option>
              <option value="DEEPSEEK">DeepSeek</option>
              <option value="ANTHROPIC">Anthropic</option>
              <option value="OLLAMA">Ollama</option>
              <option value="CUSTOM">Custom</option>
            </select>
          </div>
          <div className="form-group">
            <label className="form-label">Model</label>
            <input className="form-input" value={form.modelName} onChange={(e) => setForm({ ...form, modelName: e.target.value })} placeholder="e.g. deepseek-chat" />
          </div>
        </div>
        <div className="form-group">
          <label className="form-label">API Key</label>
          <input className="form-input" type="password" value={form.apiKey} onChange={(e) => setForm({ ...form, apiKey: e.target.value })} placeholder={config ? '(stored, leave blank to keep)' : 'sk-...'} />
        </div>
        <div className="form-group">
          <label className="form-label">Base URL</label>
          <input className="form-input" value={form.baseUrl} onChange={(e) => setForm({ ...form, baseUrl: e.target.value })} placeholder="https://api.deepseek.com" />
        </div>
        <button className="btn-primary btn-sm" type="submit">Save</button>
        <button className="btn-ghost btn-sm" type="button" style={{ marginLeft: 8 }} onClick={handleTest}>Test Connection</button>
        {testMsg && <span style={{ marginLeft: 12, fontSize: 13, color: testMsg === 'Connection OK' ? 'var(--color-success)' : 'var(--color-danger)' }}>{testMsg}</span>}
      </form>
    </div>
  );
}

function ApplicationsTab() {
  const [items, setItems] = useState<ApplicationResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [form, setForm] = useState<ApplicationRequest>({ name: '', prometheusLabel: '' });
  const [editingId, setEditingId] = useState<string | null>(null);

  const fetchData = async () => {
    setLoading(true);
    try {
      const r = await listApplications();
      setItems(r.data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchData(); }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    try {
      if (editingId) {
        await updateApplication(editingId, form);
      } else {
        await createApplication(form);
      }
      setForm({ name: '', prometheusLabel: '' });
      setEditingId(null);
      fetchData();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Save failed');
    }
  };

  const handleDelete = async (id: string) => {
    try {
      await deleteApplication(id);
      fetchData();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Delete failed');
    }
  };

  if (loading) return <LoadingSpinner size="lg" />;

  return (
    <div>
      {error && <ErrorAlert error={error} onDismiss={() => setError(null)} />}
      <form onSubmit={handleSubmit} className="card">
        <div className="form-row">
          <div className="form-group">
            <label className="form-label">Name</label>
            <input className="form-input" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
          </div>
          <div className="form-group">
            <label className="form-label">Prometheus Label</label>
            <input className="form-input" value={form.prometheusLabel} onChange={(e) => setForm({ ...form, prometheusLabel: e.target.value })} />
          </div>
        </div>
        <div className="form-group">
          <label className="form-label">Actuator URL</label>
          <input className="form-input" value={form.actuatorUrl ?? ''} onChange={(e) => setForm({ ...form, actuatorUrl: e.target.value })} />
        </div>
        <div className="form-group">
          <label className="form-label">Tags</label>
          <input className="form-input" value={form.tags ?? ''} onChange={(e) => setForm({ ...form, tags: e.target.value })} />
        </div>
        <button className="btn-primary btn-sm" type="submit">
          {editingId ? 'Update' : 'Add Application'}
        </button>
        {editingId && (
          <button className="btn-ghost btn-sm" style={{ marginLeft: 8 }} type="button" onClick={() => { setEditingId(null); setForm({ name: '', prometheusLabel: '' }); }}>Cancel</button>
        )}
      </form>

      {items.length > 0 && (
        <div className="card" style={{ padding: 0 }}>
          <table className="table">
            <thead>
              <tr><th>Name</th><th>Prometheus Label</th><th>Actuator URL</th><th></th></tr>
            </thead>
            <tbody>
              {items.map((a) => (
                <tr key={a.id}>
                  <td>{a.name}</td>
                  <td style={{ fontFamily: 'var(--font-mono)', fontSize: 13 }}>{a.prometheusLabel}</td>
                  <td style={{ fontSize: 13 }}>{a.actuatorUrl ?? '-'}</td>
                  <td>
                    <button className="btn-ghost btn-sm" onClick={() => { setEditingId(a.id); setForm({ name: a.name, prometheusLabel: a.prometheusLabel, actuatorUrl: a.actuatorUrl, tags: a.tags }); }}>Edit</button>
                    <button className="btn-ghost btn-sm" onClick={() => handleDelete(a.id)}>Del</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

function TokensTab() {
  const [tokens, setTokens] = useState<ApiTokenResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [newName, setNewName] = useState('');
  const [createdToken, setCreatedToken] = useState<CreateApiTokenResponse | null>(null);

  const fetchData = async () => {
    setLoading(true);
    try {
      const r = await listTokens();
      setTokens(r);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchData(); }, []);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    try {
      const r = await createToken({ name: newName });
      setCreatedToken(r);
      setNewName('');
      fetchData();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Create failed');
    }
  };

  const handleDelete = async (id: string) => {
    try {
      await deleteToken(id);
      fetchData();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Delete failed');
    }
  };

  if (loading) return <LoadingSpinner size="lg" />;

  return (
    <div>
      {error && <ErrorAlert error={error} onDismiss={() => setError(null)} />}

      {createdToken && (
        <div className="card" style={{ borderColor: 'var(--color-warning)' }}>
          <p style={{ marginBottom: 8 }}><strong>Token created — copy it now. It won't be shown again.</strong></p>
          <code style={{ fontFamily: 'var(--font-mono)', wordBreak: 'break-all', color: 'var(--color-warning)' }}>{createdToken.token}</code>
          <button className="btn-ghost btn-sm" style={{ display: 'block', marginTop: 12 }} onClick={() => setCreatedToken(null)}>Dismiss</button>
        </div>
      )}

      <form onSubmit={handleCreate} className="card" style={{ display: 'flex', gap: 8, alignItems: 'flex-end' }}>
        <div className="form-group" style={{ flex: 1, marginBottom: 0 }}>
          <label className="form-label">Token Name</label>
          <input className="form-input" value={newName} onChange={(e) => setNewName(e.target.value)} placeholder="e.g. CI/CD pipeline" />
        </div>
        <button className="btn-primary btn-sm" type="submit">Create Token</button>
      </form>

      {tokens.length > 0 && (
        <div className="card" style={{ padding: 0 }}>
          <table className="table">
            <thead>
              <tr><th>Name</th><th>Prefix</th><th>Created</th><th></th></tr>
            </thead>
            <tbody>
              {tokens.map((t) => (
                <tr key={t.id}>
                  <td>{t.name}</td>
                  <td><code>{t.prefix}</code></td>
                  <td style={{ fontSize: 13, color: 'var(--color-text-muted)' }}>{new Date(t.createdAt).toLocaleDateString()}</td>
                  <td><button className="btn-ghost btn-sm" onClick={() => handleDelete(t.id)}>Revoke</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

function PasswordTab() {
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSuccess(false);
    try {
      await changePassword({ currentPassword, newPassword });
      setSuccess(true);
      setCurrentPassword('');
      setNewPassword('');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed');
    }
  };

  return (
    <div>
      {error && <ErrorAlert error={error} onDismiss={() => setError(null)} />}
      {success && <div className="alert" style={{ background: 'rgba(74,222,128,0.15)', color: 'var(--color-success)' }}>Password changed successfully.</div>}
      <form onSubmit={handleSubmit} className="card" style={{ maxWidth: 400 }}>
        <div className="form-group">
          <label className="form-label">Current Password</label>
          <input className="form-input" type="password" value={currentPassword} onChange={(e) => setCurrentPassword(e.target.value)} />
        </div>
        <div className="form-group">
          <label className="form-label">New Password</label>
          <input className="form-input" type="password" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} />
        </div>
        <button className="btn-primary btn-sm" type="submit">Change Password</button>
      </form>
    </div>
  );
}
