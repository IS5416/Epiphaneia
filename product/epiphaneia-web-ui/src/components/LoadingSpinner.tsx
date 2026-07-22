export default function LoadingSpinner({ size }: { size?: 'lg' }) {
  return <span className={`spinner${size === 'lg' ? ' spinner-lg' : ''}`} />;
}
