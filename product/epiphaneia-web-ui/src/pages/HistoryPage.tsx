import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { listConversations, deleteConversation } from '../api/endpoints';
import type { ConversationResponse } from '../types';
import ErrorAlert from '../components/ErrorAlert';
import LoadingSpinner from '../components/LoadingSpinner';
import ConfirmDialog from '../components/ConfirmDialog';

export default function HistoryPage() {
  const [conversations, setConversations] = useState<ConversationResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [keyword, setKeyword] = useState('');
  const [deleteTarget, setDeleteTarget] = useState<string | null>(null);

  const fetchData = async (kw?: string) => {
    setLoading(true);
    setError(null);
    try {
      const result = await listConversations(undefined, kw);
      setConversations(result.data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchData(); }, []);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    fetchData(keyword);
  };

  const handleDelete = async () => {
    if (!deleteTarget) return;
    try {
      await deleteConversation(deleteTarget);
      setConversations((prev) => prev.filter((c) => c.id !== deleteTarget));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Delete failed');
    } finally {
      setDeleteTarget(null);
    }
  };

  return (
    <div>
      <div className="page-header">
        <h1>History</h1>
        <form onSubmit={handleSearch} style={{ display: 'flex', gap: 8 }}>
          <input
            className="form-input"
            placeholder="Search..."
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            style={{ width: 240 }}
          />
          <button className="btn-primary btn-sm" type="submit">Search</button>
        </form>
      </div>

      {error && <ErrorAlert error={error} onDismiss={() => setError(null)} />}

      {loading ? (
        <LoadingSpinner size="lg" />
      ) : conversations.length === 0 ? (
        <p style={{ color: 'var(--color-text-muted)' }}>No conversations yet.</p>
      ) : (
        <div className="card" style={{ padding: 0 }}>
          <table className="table">
            <thead>
              <tr>
                <th>Title</th>
                <th>Application</th>
                <th>Last Message</th>
                <th>Date</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {conversations.map((c) => (
                <tr key={c.id}>
                  <td>
                    <Link to={`/report/${c.id}`}>{c.title}</Link>
                  </td>
                  <td style={{ color: 'var(--color-text-muted)' }}>{c.applicationName}</td>
                  <td style={{ color: 'var(--color-text-muted)', maxWidth: 300, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {c.lastMessage ?? '-'}
                  </td>
                  <td style={{ color: 'var(--color-text-muted)', fontSize: 13 }}>
                    {new Date(c.createdAt).toLocaleDateString()}
                  </td>
                  <td>
                    <button className="btn-ghost btn-sm" onClick={() => setDeleteTarget(c.id)}>Delete</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {deleteTarget && (
        <ConfirmDialog
          title="Delete Conversation"
          message="This cannot be undone."
          confirmLabel="Delete"
          onConfirm={handleDelete}
          onCancel={() => setDeleteTarget(null)}
        />
      )}
    </div>
  );
}
