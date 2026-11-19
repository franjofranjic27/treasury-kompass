import DOMPurify from 'dompurify';
import { Marked, Tokens, TokenizerAndRendererExtension } from 'marked';

/** A source the answer can cite with a marker such as «[S1]». */
export interface ChatSource {
  readonly id: string;
  readonly label: string;
}

/*
 * Everything the chat bubble may contain. Headings are shifted below the panel's own h2, so the model
 * cannot break the page outline; images and raw HTML are never rendered.
 */
const ALLOWED_TAGS = (
  'p br strong em del code pre blockquote hr ul ol li h3 h4 h5 h6 ' +
  'div table thead tbody tr th td a sup'
).split(' ');
const ALLOWED_ATTR = ['href', 'title', 'class', 'align', 'start'];
// DOMPurify checks every attribute that is not "URI-safe" against the URL allowlist, so the two
// plain-value attributes are exempted explicitly; href still has to be an http(s) URL.
const PLAIN_VALUE_ATTR = ['align', 'start'];
const SAFE_URL = /^https?:\/\//i;
const HEADING_OFFSET = 2;
const MAX_HEADING = 6;
const CITATION_MARKER = /^\[(S\d+(?:\s*[,;]\s*S\d+)*)\]/;

interface CitationToken extends Tokens.Generic {
  readonly type: 'citation';
  readonly ids: readonly string[];
}

const purify = DOMPurify(window);
// Every link opens in a new tab without access to the app. Set after the attribute check, so the
// model cannot choose its own target or rel.
purify.addHook('afterSanitizeAttributes', (node) => {
  if (node.tagName === 'A') {
    node.setAttribute('target', '_blank');
    node.setAttribute('rel', 'noopener noreferrer');
  }
});

/**
 * Renders an assistant answer (GitHub-flavoured markdown) to sanitized HTML for `[innerHTML]`.
 * Citation markers become superscript chips titled with the matching source of the same message.
 */
export function renderMarkdown(markdown: string, sources: readonly ChatSource[]): string {
  const html = createParser(sources).parse(markdown, { async: false });
  return purify.sanitize(html, {
    ALLOWED_TAGS,
    ALLOWED_ATTR,
    ALLOWED_URI_REGEXP: SAFE_URL,
    ADD_URI_SAFE_ATTR: PLAIN_VALUE_ATTR,
    ALLOW_ARIA_ATTR: false,
    ALLOW_DATA_ATTR: false,
  });
}

/** A parser per render, because the citation chips depend on the sources of that one message. */
function createParser(sources: readonly ChatSource[]): Marked {
  return new Marked({
    gfm: true,
    breaks: true,
    extensions: [citationExtension(sources)],
    renderer: {
      // The model must not inject markup: raw HTML is shown as text.
      html: ({ text }) => escapeHtml(text),
      heading({ tokens, depth }) {
        const level = Math.min(depth + HEADING_OFFSET, MAX_HEADING);
        return `<h${level}>${this.parser.parseInline(tokens)}</h${level}>`;
      },
      link({ href, title, tokens }) {
        const label = this.parser.parseInline(tokens);
        if (!SAFE_URL.test(href)) return label;
        const titleAttr = title ? ` title="${escapeHtml(title)}"` : '';
        return `<a href="${escapeHtml(href)}"${titleAttr}>${label}</a>`;
      },
      image: ({ text }) => escapeHtml(text),
      // Wrapped so wide tables scroll inside the bubble instead of widening the panel.
      table(token) {
        const row = (cells: readonly Tokens.TableCell[]) =>
          this.tablerow({ text: cells.map((cell) => this.tablecell(cell)).join('') });
        const body = token.rows.map(row).join('');
        return (
          `<div class="md-table"><table><thead>${row(token.header)}</thead>` +
          `${body ? `<tbody>${body}</tbody>` : ''}</table></div>`
        );
      },
    },
  });
}

function citationExtension(sources: readonly ChatSource[]): TokenizerAndRendererExtension {
  const labels = new Map(sources.map((source) => [source.id, source.label]));
  return {
    name: 'citation',
    level: 'inline',
    start: (src) => src.match(/\[S\d/)?.index,
    tokenizer(src): CitationToken | undefined {
      const match = CITATION_MARKER.exec(src);
      if (!match) return undefined;
      return { type: 'citation', raw: match[0], ids: match[1].split(/\s*[,;]\s*/) };
    },
    renderer(token) {
      return (token as CitationToken).ids
        .map((id) => {
          const label = labels.get(id);
          const title = label ? ` title="${escapeHtml(`${id}: ${label}`)}"` : '';
          return `<sup class="cite"${title}>${id}</sup>`;
        })
        .join('');
    },
  };
}

const HTML_ESCAPES: Readonly<Record<string, string>> = {
  '&': '&amp;',
  '<': '&lt;',
  '>': '&gt;',
  '"': '&quot;',
  "'": '&#39;',
};

function escapeHtml(value: string): string {
  return value.replace(/[&<>"']/g, (char) => HTML_ESCAPES[char]);
}
