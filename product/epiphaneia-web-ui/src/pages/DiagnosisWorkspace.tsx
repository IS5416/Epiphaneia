import { useEffect, useState, useRef } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { connectSse } from '../api/sse';
import {
  listConversations, createConversation,
  listApplications,
} from '../api/endpoints';
import type { ConversationResponse, ApplicationResponse } from '../types';
import ErrorAlert from '../components/ErrorAlert';
import LoadingSpinner from '../components/LoadingSpinner';

interface SseLine {
  type: 'state' | 'step' | 'token' | 'done' | 'error';
  text: string;
  conversationId?: string;
}

export default function DiagnosisWorkspace() {
  const [conversations, setConversations] = useState<ConversationResponse[]>([]);
  const [applications, setApplications] = useState<ApplicationResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // new conversation form
  const [title, setTitle] = useState('');
  const [question, setQuestion] = useState('');
  const [appId, setAppId] = useState('');

  // SSE state
  const [sseLines, setSseLines] = useState<SseLine[]>([]);
  const [sseRunning, setSseRunning] = useState(false);
  const [sseConvoId, setSseConvoId] = useState<string | null>(null);
  const sseRef = useRef<AbortController | null>(null);
  const ssePanelRef = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();

  const fetchData = async () => {
    setLoading(true);
    setError(null);
    try {
      const [convResult, appResult] = await Promise.all([
        listConversations(),
        listApplications(),
      ]);
      setConversations(convResult.data);
      setApplications(appResult.data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchData(); }, []);

  // auto-scroll SSE panel
  useEffect(() => {
    if (ssePanelRef.current) {
      ssePanelRef.current.scrollTop = ssePanelRef.current.scrollHeight;
    }
  }, [sseLines]);

  // cleanup SSE on unmount
  useEffect(() => {
    return () => {
      sseRef.current?.abort();
    };
  }, []);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!appId || !title || !question) return;

    setError(null);
    try {
      const conv = await createConversation({ applicationId: appId, title, question });
      setSseLines([]);
      setSseRunning(true);
      setSseConvoId(conv.id);

      // abort previous SSE if any
      sseRef.current?.abort();

      const controller = connectSse(conv.id, question, {
        onState: (ev) => {
          setSseLines((prev) => [...prev, { type: 'state', text: `[State: ${ev.state}]`, conversationId: conv.id }]);
        },
        onStep: (ev) => {
          setSseLines((prev) => [...prev, { type: 'step', text: `> ${ev.step}`, conversationId: conv.id }]);
        },
        onToken: (ev) => {
          setSseLines((prev) => {
            const last = prev[prev.length - 1];
            if (last?.type === 'token') {
              return [...prev.slice(0, -1), { ...last, text: last.text + ev.token }];
            }
            return [...prev, { type: 'token', text: ev.token, conversationId: conv.id }];
          });
        },
        onDone: (ev) => {
          setSseLines((prev) => [...prev, { type: 'done', text: `Diagnosis complete: ${ev.state}`, conversationId: conv.id }]);
          setSseRunning(false);
        },
        onError: (ev) => {
          setSseLines((prev) => [...prev, { type: 'error', text: `Error: ${ev.error}`, conversationId: conv.id }]);
          setSseRunning(false);
        },
        onClose: () => {
          setSseRunning(false);
        },
      });

      sseRef.current = controller;

      // refresh conversation list
      const result = await listConversations();
      setConversations(result.data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create conversation');
    }
  };

  const handleAbort = () => {
    sseRef.current?.abort();
    setSseRunning(false);
  };

  return (
    <div>
      <div className="page-header">
        <h1>Diagnosis</h1>
      </div>

      {error && <ErrorAlert error={error} onDismiss={() => setError(null)} />}

      {/* New conversation form */}
      <div className="card">
        <h3 style={{ marginBottom: 12 }}>New Conversation</h3>
        <form onSubmit={handleCreate}>
          <div className="form-row">
            <div className="form-group">
              <label className="form-label">Application</label>
              <select className="form-input" value={appId} onChange={(e) => setAppId(e.target.value)}>
                <option value="">Select an application...</option>
                {applications.map((a) => (
                  <option key={a.id} value={a.id}>{a.name}</option>
                ))}
              </select>
            </div>
            <div className="form-group">
              <label className="form-label">Title</label>
              <input className="form-input" value={title} onChange={(e) => setTitle(e.target.value)} placeholder="e.g. P99 latency spike in user-service" />
            </div>
          </div>
          <div className="form-group">
            <label className="form-label">What do you want to investigate?</label>
            <textarea
              className="form-input"
              rows={3}
              value={question}
              onChange={(e) => setQuestion(e.target.value)}
              placeholder="e.g. Why is user-service p99 latency above 2s?"
              style={{ resize: 'vertical', fontFamily: 'inherit' }}
            />
          </div>
          <button className="btn-primary" type="submit" disabled={sseRunning || !appId || !title || !question}>
            Start Diagnosis
          </button>
        </form>
      </div>

      {/* SSE stream */}
      {(sseRunning || sseLines.length > 0) && (
        <div className="card">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
            <h3>
              Diagnosis Stream
              {sseRunning && <span className="badge badge-warning" style={{ marginLeft: 8 }}>LIVE</span>}
            </h3>
            <div style={{ display: 'flex', gap: 8 }}>
              {sseRunning && (
                <button className="btn-danger btn-sm" onClick={handleAbort}>Abort</button>
              )}
              {sseConvoId && !sseRunning && (
                <Link to={`/report/${sseConvoId}`} className="btn-primary btn-sm" style={{ display: 'inline-block' }}>
                  View Report
                </Link>
              )}
            </div>
          </div>
          <div className="sse-panel" ref={ssePanelRef}>
            {sseLines.length === 0 && (
              <span style={{ color: 'var(--color-text-muted)' }}>Waiting for diagnosis to start...</span>
            )}
            {sseLines.map((line, i) => (
              <div key={i} className={`sse-${line.type}`}>{line.text}</div>
            ))}
          </div>
        </div>
      )}

      {/* Conversations list */}
      <div className="card" style={{ marginTop: 16 }}>
        <h3 style={{ marginBottom: 12 }}>Recent Conversations</h3>
        {loading ? (
          <LoadingSpinner size="lg" />
        ) : conversations.length === 0 ? (
          <p style={{ color: 'var(--color-text-muted)' }}>No conversations yet. Create your first diagnosis above.</p>
        ) : (
          <table className="table">
            <thead>
              <tr><th>Title</th><th>Application</th><th>Date</th><th></th></tr>
            </thead>
            <tbody>
              {conversations.slice(0, 10).map((c) => (
                <tr key={c.id}>
                  <td><Link to={`/report/${c.id}`}>{c.title}</Link></td>
                  <td style={{ color: 'var(--color-text-muted)' }}>{c.applicationName}</td>
                  <td style={{ color: 'var(--color-text-muted)', fontSize: 13 }}>
                    {new Date(c.createdAt).toLocaleString()}
                  </td>
                  <td>
                    <Link to={`/report/${c.id}`} className="btn-ghost btn-sm" style={{ display: 'inline-block' }}>Report</Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
