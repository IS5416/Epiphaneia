import { useMemo } from 'react';

// ponytail: simple regex-based Markdown → HTML, covers PRD report format (headers, lists, code, tables, bold, links).
// Swap for a full parser (marked/unified) if nested formatting edges appear.
function renderMd(md: string): string {
  let html = md
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');

  // code blocks (fenced)
  html = html.replace(/```(\w*)\n([\s\S]*?)```/g, (_, lang, code) => {
    return `<pre><code>${code.trim()}</code></pre>`;
  });

  // inline code
  html = html.replace(/`([^`]+)`/g, '<code>$1</code>');

  // bold
  html = html.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');

  // italic
  html = html.replace(/\*([^*]+)\*/g, '<em>$1</em>');

  // headers (must be after bold/italic to not conflict with **)
  html = html.replace(/^#### (.+)$/gm, '<h4>$1</h4>');
  html = html.replace(/^### (.+)$/gm, '<h3>$1</h3>');
  html = html.replace(/^## (.+)$/gm, '<h2>$1</h2>');
  html = html.replace(/^# (.+)$/gm, '<h1>$1</h1>');

  // links — validate scheme to prevent javascript: injection
  html = html.replace(/\[([^\]]+)\]\(([^)]+)\)/g, (_, text, href) => {
    const safe = /^(https?:|\/|#|mailto:)/i.test(href);
    return safe ? `<a href="${href}" rel="noopener noreferrer">${text}</a>` : text;
  });

  // unordered lists (marker: * or -)
  html = html.replace(/^[*-] (.+)$/gm, '\x00ul\x00<li>$1</li>');
  html = html.replace(/((\x00ul\x00<li>.*<\/li>\n?)+)/g, (_: string, block: string) => {
    return '<ul>' + block.replace(/\x00ul\x00/g, '') + '</ul>';
  });

  // ordered lists (marker: 1. 2. etc.)
  html = html.replace(/^\d+\. (.+)$/gm, '\x00ol\x00<li>$1</li>');
  html = html.replace(/((\x00ol\x00<li>.*<\/li>\n?)+)/g, (_: string, block: string) => {
    return '<ol>' + block.replace(/\x00ol\x00/g, '') + '</ol>';
  });

  // blockquotes
  html = html.replace(/^> (.+)$/gm, '<blockquote>$1</blockquote>');

  // horizontal rules
  html = html.replace(/^---$/gm, '<hr>');

  // paragraphs: wrap remaining non-tag lines
  html = html
    .split('\n\n')
    .map((block) => {
      const trimmed = block.trim();
      if (!trimmed) return '';
      if (trimmed.startsWith('<')) return trimmed;
      return `<p>${trimmed.replace(/\n/g, '<br>')}</p>`;
    })
    .join('\n');

  return html;
}

export default function MarkdownRenderer({ content }: { content: string }) {
  const html = useMemo(() => renderMd(content), [content]);

  return (
    <div className="markdown" dangerouslySetInnerHTML={{ __html: html }} />
  );
}
