interface Props {
  error: unknown;
  onDismiss?: () => void;
}

export default function ErrorAlert({ error, onDismiss }: Props) {
  const message =
    error instanceof Error ? error.message
    : typeof error === 'string' ? error
    : 'An unexpected error occurred';

  return (
    <div className="alert alert-error" style={{ position: 'relative' }}>
      {message}
      {onDismiss && (
        <button
          className="btn-ghost btn-sm"
          style={{ position: 'absolute', right: 8, top: 8 }}
          onClick={onDismiss}
        >
          Dismiss
        </button>
      )}
    </div>
  );
}
