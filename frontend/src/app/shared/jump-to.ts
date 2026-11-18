/**
 * In-page jump for `<a href="#id">` links. With `<base href="/">` the browser would resolve the
 * plain fragment to «/#id» and leave the page, so the link moves focus (and thereby scroll) itself.
 */
export function jumpTo(event: Event, id: string): void {
  event.preventDefault();
  const target = document.getElementById(id);
  if (!target) return;
  if (!target.hasAttribute('tabindex')) target.setAttribute('tabindex', '-1');
  target.focus();
}
