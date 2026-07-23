import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { changePassword, updateLlmConfig, testLlm, createDataSource } from '../api/endpoints';
import ErrorAlert from '../components/ErrorAlert';
import LoadingSpinner from '../components/LoadingSpinner';

type Step = 'password' | 'llm' | 'datasource' | 'done';

export default function SetupWizard() {
  const [step, setStep] = useState<Step>('password');
  const navigate = useNavigate();

  return (
    <div className="setup-container">
      <div className="card" style={{ textAlign: 'center', marginBottom: 16 }}>
        <h1 style={{ fontSize: 22, color: 'var(--color-primary)', marginBottom: 4 }}>Welcome to Epiphaneia</h1>
        <p style={{ color: 'var(--color-text-muted)', fontSize: 14 }}>Let's get you set up in 3 quick steps.</p>
      </div>
      <div style={{ display: 'flex', gap: 8, marginBottom: 20, justifyContent: 'center' }}>
        {(['password', 'llm', 'datasource'] as Step[]).map((s) => (
          <div key={s} style={{
            width: 120, height: 4, borderRadius: 2,
            background: step === s ? 'var(--color-primary)' : stepIndex(s) < stepIndex(step) ? 'var(--color-success)' : 'var(--color-border)',
          }} />
        ))}
      </div>
      {step === 'password' && <PasswordStep onNext={() => setStep('llm')} />}
      {step === 'llm' && <LlmStep onNext={() => setStep('datasource')} onSkip={() => setStep('datasource')} />}
      {step === 'datasource' && <DataSourceStep onNext={() => setStep('done')} onSkip={() => navigate('/workspace')} />}
      {step === 'done' && <DoneStep onEnter={() => navigate('/workspace')} />}
    </div>
  );
}

function stepIndex(s: Step): number {
  return (['password', 'llm', 'datasource', 'done'] as Step[]).indexOf(s);
}

function PasswordStep({ onNext }: { onNext: () => void }) {
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (next.length < 8) { setError('Password must be at least 8 characters'); return; }
    setError(null);
    setLoading(true);
    try {
      await changePassword({ currentPassword: current, newPassword: next });
      onNext();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="card">
      <h2 style={{ marginBottom: 8 }}>Step 1: Change Password</h2>
      <p style={{ color: 'var(--color-text-muted)', fontSize: 14, marginBottom: 16 }}>Use the initial admin password from the console output as your current password.</p>
      {error && <ErrorAlert error={error} onDismiss={() => setError(null)} />}
      <form onSubmit={handleSubmit}>
        <div className="form-group">
          <label className="form-label">Current Password</label>
          <input className="form-input" type="password" value={current} onChange={(e) => setCurrent(e.target.value)} autoFocus />
        </div>
        <div className="form-group">
          <label className="form-label">New Password (min 8 chars)</label>
          <input className="form-input" type="password" value={next} onChange={(e) => setNext(e.target.value)} />
        </div>
        <button className="btn-primary" type="submit" disabled={loading}>
          {loading ? <LoadingSpinner /> : 'Continue'}
        </button>
      </form>
    </div>
  );
}

function LlmStep({ onNext, onSkip }: { onNext: () => void; onSkip: () => void }) {
  const [form, setForm] = useState({ provider: 'OPENAI', modelName: '', apiKey: '', baseUrl: 'https://api.deepseek.com' });
  const [error, setError] = useState<string | null>(null);
  const [testMsg, setTestMsg] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSaving(true);
    try {
      await updateLlmConfig({
        provider: form.provider,
        modelName: form.modelName,
        apiKey: form.apiKey || undefined,
        baseUrl: form.baseUrl || undefined,
      });
      onNext();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Save failed');
    } finally {
      setSaving(false);
    }
  };

  const handleTest = async () => {
    setTestMsg(null);
    try {
      const r = await testLlm({
        provider: form.provider,
        modelName: form.modelName,
        apiKey: form.apiKey || undefined,
        baseUrl: form.baseUrl || undefined,
      });
      setTestMsg(r.success ? 'Connection OK' : r.message);
    } catch (err) {
      setTestMsg(err instanceof Error ? err.message : 'Test failed');
    }
  };

  return (
    <div className="card">
      <h2 style={{ marginBottom: 8 }}>Step 2: Configure LLM</h2>
      <p style={{ color: 'var(--color-text-muted)', fontSize: 14, marginBottom: 16 }}>
        Your API key is encrypted before storage. You can also configure this later in Settings.
      </p>
      {error && <ErrorAlert error={error} onDismiss={() => setError(null)} />}
      <form onSubmit={handleSave}>
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
            <label className="form-label">Model Name</label>
            <input className="form-input" value={form.modelName} onChange={(e) => setForm({ ...form, modelName: e.target.value })} placeholder="e.g. gpt-4o" />
          </div>
        </div>
        <div className="form-group">
          <label className="form-label">API Key</label>
          <input className="form-input" type="password" value={form.apiKey} onChange={(e) => setForm({ ...form, apiKey: e.target.value })} placeholder="sk-..." />
        </div>
        <div className="form-group">
          <label className="form-label">Base URL</label>
          <input className="form-input" value={form.baseUrl} onChange={(e) => setForm({ ...form, baseUrl: e.target.value })} />
        </div>
        <button className="btn-primary" type="submit" disabled={saving}>
          {saving ? <LoadingSpinner /> : 'Save & Continue'}
        </button>
        <button className="btn-ghost" type="button" style={{ marginLeft: 8 }} onClick={handleTest}>Test Connection</button>
        <button className="btn-ghost" type="button" style={{ marginLeft: 8 }} onClick={onSkip}>Skip</button>
        {testMsg && <span style={{ marginLeft: 12, fontSize: 13, color: testMsg === 'Connection OK' ? 'var(--color-success)' : 'var(--color-danger)' }}>{testMsg}</span>}
      </form>
    </div>
  );
}

function DataSourceStep({ onNext, onSkip }: { onNext: () => void; onSkip: () => void }) {
  const [form, setForm] = useState({ type: 'PROMETHEUS', name: '', url: '' });
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.name || !form.url) { setError('Name and URL are required'); return; }
    setError(null);
    setSaving(true);
    try {
      await createDataSource(form);
      onNext();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Save failed');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="card">
      <h2 style={{ marginBottom: 8 }}>Step 3: Add Your First Data Source</h2>
      <p style={{ color: 'var(--color-text-muted)', fontSize: 14, marginBottom: 16 }}>
        Connect to Prometheus, Elasticsearch, or an Actuator endpoint. You can add more later in Settings.
      </p>
      {error && <ErrorAlert error={error} onDismiss={() => setError(null)} />}
      <form onSubmit={handleSave}>
        <div className="form-row">
          <div className="form-group">
            <label className="form-label">Type</label>
            <select className="form-input" value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value })}>
              <option value="PROMETHEUS">Prometheus</option>
              <option value="ELASTICSEARCH">Elasticsearch</option>
              <option value="ACTUATOR">Actuator</option>
            </select>
          </div>
          <div className="form-group">
            <label className="form-label">Name</label>
            <input className="form-input" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} placeholder="My Prometheus" />
          </div>
        </div>
        <div className="form-group">
          <label className="form-label">URL</label>
          <input className="form-input" value={form.url} onChange={(e) => setForm({ ...form, url: e.target.value })} placeholder="http://localhost:9090" />
        </div>
        <button className="btn-primary" type="submit" disabled={saving}>
          {saving ? <LoadingSpinner /> : 'Add & Continue'}
        </button>
        <button className="btn-ghost" type="button" style={{ marginLeft: 8 }} onClick={onSkip}>Skip</button>
      </form>
    </div>
  );
}

function DoneStep({ onEnter }: { onEnter: () => void }) {
  return (
    <div className="card" style={{ textAlign: 'center' }}>
      <h2 style={{ marginBottom: 8, color: 'var(--color-success)' }}>You're all set!</h2>
      <p style={{ color: 'var(--color-text-muted)', fontSize: 14, marginBottom: 20 }}>
        Epiphaneia is ready. Start diagnosing your applications.
      </p>
      <button className="btn-primary" onClick={onEnter}>Enter Workspace</button>
    </div>
  );
}
