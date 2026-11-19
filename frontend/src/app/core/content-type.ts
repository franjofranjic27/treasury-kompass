/** Matches `application/json` and structured suffixes such as `application/problem+json`. */
const JSON_TYPE = /^application\/(?:[\w.+-]+\+)?json\b/i;
const EVENT_STREAM_TYPE = /^text\/event-stream\b/i;

function contentType(response: Response): string {
  return response.headers.get('content-type')?.trim() ?? '';
}

export function isJsonResponse(response: Response): boolean {
  return JSON_TYPE.test(contentType(response));
}

export function isEventStreamResponse(response: Response): boolean {
  return EVENT_STREAM_TYPE.test(contentType(response));
}
