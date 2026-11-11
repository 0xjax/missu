const REQUEST_TIMEOUT = 20_000;

export async function withTimeout<T>(
  request: (signal: AbortSignal) => Promise<T>,
  external?: AbortSignal | null,
): Promise<T> {
  const controller = new AbortController();
  let timedOut = false;

  const abort = () => controller.abort();
  if (external) {
    if (external.aborted) {
      controller.abort();
    } else {
      external.addEventListener('abort', abort, { once: true });
    }
  }

  const timer = setTimeout(() => {
    timedOut = true;
    controller.abort();
  }, REQUEST_TIMEOUT);

  try {
    return await request(controller.signal);
  } catch (error) {
    if (timedOut) throw new Error('请求超时，请重试');
    throw error;
  } finally {
    clearTimeout(timer);
    external?.removeEventListener('abort', abort);
  }
}
