import { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { getReport } from '../api/endpoints';
import MarkdownRenderer from '../components/MarkdownRenderer';
import ErrorAlert from '../components/ErrorAlert';
import LoadingSpinner from '../components/LoadingSpinner';

export default function ReportView() {
  const { id } = useParams<{ id: string }>();
  const [content, setContent] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!id) return;
    getReport(id)
      .then((r) => setContent(r.content))
      .catch((err) => setError(err instanceof Error ? err.message : 'Failed to load'))
      .finally(() => setLoading(false));
  }, [id]);

  const handleExport = () => {
    if (!content) return;
    const blob = new Blob([content], { type: 'text/markdown' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `epiphaneia-report-${id}.md`;
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div>
      <div className="page-header">
        <h1>Diagnosis Report</h1>
        <div style={{ display: 'flex', gap: 8 }}>
          {content && (
            <button className="btn-primary btn-sm" onClick={handleExport}>Export Markdown</button>
          )}
          <Link to="/history" className="btn-ghost btn-sm" style={{ display: 'inline-block' }}>Back</Link>
        </div>
      </div>

      {error && <ErrorAlert error={error} />}
      {loading && <LoadingSpinner size="lg" />}
      {content && <div className="card"><MarkdownRenderer content={content} /></div>}
    </div>
  );
}
