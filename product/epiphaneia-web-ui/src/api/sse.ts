type SseCallbacks = {
  onState?: (e: { messageId: string; state: string }) => void;
  onStep?: (e: { messageId: string; step: string }) => void;
  onToken?: (e: { messageId: string; token: string }) => void;
  onDone?: (e: { messageId: string; state: string }) => void;
  onError?: (e: { messageId: string; error: string }) => void;
  onClose?: () => void;
};

export function connectSse(
  conversationId: string,
  question: string,
  callbacks: SseCallbacks,
): AbortController {
  const controller = new AbortController();
  const url = `/api/v1/conversations/${conversationId}/messages?question=${encodeURIComponent(question)}`;

  fetch(url, {
    method: 'POST',
    credentials: 'same-origin',
    signal: controller.signal,
  })
    .then(async (response) => {
      if (response.status === 401) {
        if (window.location.pathname !== '/login') {
          window.location.href = '/login';
        }
        callbacks.onError?.({ messageId: '', error: 'Session expired' });
        return;
      }
      if (!response.ok) {
        const body = await response.json().catch(() => ({}));
        callbacks.onError?.({
          messageId: '',
          error: body?.error?.message ?? `HTTP ${response.status}`,
        });
        return;
      }

      const reader = response.body?.getReader();
      if (!reader) return;

      const decoder = new TextDecoder();
      let buffer = '';
      let eventType = '';
      let eventData = '';

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop() ?? '';

        for (const line of lines) {
          if (line.startsWith('event:')) {
            eventType = line.slice(6).trim();
          } else if (line.startsWith('data:')) {
            eventData += (eventData ? '\n' : '') + line.slice(5).trim();
          } else if (line === '' && eventType && eventData) {
            dispatchSseEvent(eventType, eventData, callbacks);
            eventType = '';
            eventData = '';
          }
        }
      }

      // flush any remaining partial event
      if (eventType && eventData) {
        dispatchSseEvent(eventType, eventData, callbacks);
      }
    })
    .catch((err) => {
      if (err.name !== 'AbortError') {
        callbacks.onError?.({ messageId: '', error: String(err) });
      }
    });

  return controller;
}

function dispatchSseEvent(
  type: string,
  data: string,
  callbacks: SseCallbacks,
) {
  try {
    const parsed = JSON.parse(data);
    switch (type) {
      case 'state':
        callbacks.onState?.(parsed);
        break;
      case 'step':
        callbacks.onStep?.(parsed);
        break;
      case 'token':
        callbacks.onToken?.(parsed);
        break;
      case 'done':
        callbacks.onDone?.(parsed);
        break;
      case 'error':
        callbacks.onError?.(parsed);
        break;
      case 'close':
        callbacks.onClose?.();
        break;
    }
  } catch {
    // skip unparseable events
  }
}
