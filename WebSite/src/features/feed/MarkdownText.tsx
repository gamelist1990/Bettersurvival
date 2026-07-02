import { Fragment, type ReactNode } from 'react';

/**
 * フィード本文用の安全な Markdown レンダラー。
 *
 * - HTML を一切解釈せず React のテキストノードとして描画するため XSS 耐性がある
 *   (dangerouslySetInnerHTML は使用しない)
 * - リンクは http/https のみ許可
 * - 素の URL は「サイト名だけ表示・クリックで開く」形式 (Discord/Minecraft と同じ見た目)
 * - 対応: **太字** / *斜体* / __下線__ / ~~打ち消し~~ / `コード` / ```コードブロック```
 *   / > 引用 / # 見出し / [ラベル](URL) / 素のURL
 */

const URL_SOURCE = "https?:\\/\\/[\\w\\-._~:/?#[\\]@!$&'()*+,;=%]+";
const LINK_OR_URL = new RegExp(`\\[([^\\]\\r\\n]+)\\]\\((${URL_SOURCE})\\)|(${URL_SOURCE})`, 'g');
const INLINE_STYLE = /\*\*([^*]+)\*\*|__([^_]+)__|~~([^~]+)~~|\*([^*\n]+)\*|`([^`\r\n]+)`/g;

function hostLabel(url: string) {
  try {
    const host = new URL(url).host;
    if (!host) return url;
    return host.startsWith('www.') ? host.slice(4) : host;
  } catch {
    return url;
  }
}

function safeHref(url: string): string | null {
  return url.startsWith('http://') || url.startsWith('https://') ? url : null;
}

function ExternalLink({ href, label }: { href: string; label: string }) {
  const safe = safeHref(href);
  if (!safe) return <>{label}</>;
  return (
    <a
      className="md-link"
      href={safe}
      title={safe}
      target="_blank"
      rel="noopener noreferrer nofollow"
      onClick={(event) => event.stopPropagation()}
    >
      {label}
    </a>
  );
}

/** 太字などのインライン装飾を描画する (リンク処理済みのプレーン部分用)。 */
function renderStyled(text: string, keyPrefix: string): ReactNode[] {
  const nodes: ReactNode[] = [];
  let last = 0;
  let index = 0;
  INLINE_STYLE.lastIndex = 0;
  for (let match = INLINE_STYLE.exec(text); match; match = INLINE_STYLE.exec(text)) {
    if (match.index > last) nodes.push(text.slice(last, match.index));
    const key = `${keyPrefix}-s${index++}`;
    if (match[1] !== undefined) nodes.push(<strong key={key}>{match[1]}</strong>);
    else if (match[2] !== undefined) nodes.push(<u key={key}>{match[2]}</u>);
    else if (match[3] !== undefined) nodes.push(<s key={key}>{match[3]}</s>);
    else if (match[4] !== undefined) nodes.push(<em key={key}>{match[4]}</em>);
    else if (match[5] !== undefined) nodes.push(<code key={key} className="md-code">{match[5]}</code>);
    last = match.index + match[0].length;
  }
  if (last < text.length) nodes.push(text.slice(last));
  return nodes;
}

/** リンク + インライン装飾を描画する。 */
function renderInline(text: string, keyPrefix: string): ReactNode[] {
  const nodes: ReactNode[] = [];
  let last = 0;
  let index = 0;
  LINK_OR_URL.lastIndex = 0;
  for (let match = LINK_OR_URL.exec(text); match; match = LINK_OR_URL.exec(text)) {
    if (match.index > last) nodes.push(...renderStyled(text.slice(last, match.index), `${keyPrefix}-${index}`));
    const key = `${keyPrefix}-l${index++}`;
    if (match[1] !== undefined && match[2] !== undefined) {
      // [ラベル](URL)
      nodes.push(<ExternalLink key={key} href={match[2]} label={match[1]} />);
    } else if (match[3] !== undefined) {
      // 素の URL はサイト名だけ表示
      nodes.push(<ExternalLink key={key} href={match[3]} label={hostLabel(match[3])} />);
    }
    last = match.index + match[0].length;
  }
  if (last < text.length) nodes.push(...renderStyled(text.slice(last), `${keyPrefix}-end`));
  return nodes;
}

/** 行グループ (段落・引用・見出し) を描画する。 */
function renderLines(block: string, keyPrefix: string): ReactNode[] {
  const nodes: ReactNode[] = [];
  const lines = block.split('\n');
  let quoteBuffer: string[] = [];
  const flushQuote = (key: string) => {
    if (!quoteBuffer.length) return;
    const quoted = quoteBuffer.slice();
    quoteBuffer = [];
    nodes.push(
      <blockquote key={key} className="md-quote">
        {quoted.map((line, i) => (
          <Fragment key={`${key}-q${i}`}>
            {i > 0 ? <br /> : null}
            {renderInline(line, `${key}-q${i}`)}
          </Fragment>
        ))}
      </blockquote>,
    );
  };
  lines.forEach((line, i) => {
    const key = `${keyPrefix}-ln${i}`;
    const quote = line.match(/^>\s?(.*)$/);
    if (quote) {
      quoteBuffer.push(quote[1]);
      return;
    }
    flushQuote(`${key}-flush`);
    const heading = line.match(/^(#{1,3})\s+(.*)$/);
    if (heading) {
      nodes.push(
        <strong key={key} className={`md-heading md-h${heading[1].length}`}>
          {renderInline(heading[2], key)}
        </strong>,
      );
      return;
    }
    nodes.push(
      <Fragment key={key}>
        {i > 0 ? <br /> : null}
        {renderInline(line, key)}
      </Fragment>,
    );
  });
  flushQuote(`${keyPrefix}-flush-end`);
  return nodes;
}

export function MarkdownText({ text }: { text: string }) {
  if (!text) return null;
  // ``` コードブロックで分割 (奇数番目がコード)
  const segments = text.split(/```/);
  return (
    <span className="md-body">
      {segments.map((segment, index) => {
        if (index % 2 === 1) {
          const code = segment.replace(/^[a-zA-Z0-9]*\n/, '').replace(/\n$/, '');
          return (
            <pre key={`cb${index}`} className="md-codeblock">
              <code>{code}</code>
            </pre>
          );
        }
        if (!segment) return null;
        return <Fragment key={`tx${index}`}>{renderLines(segment.replace(/^\n|\n$/g, ''), `b${index}`)}</Fragment>;
      })}
    </span>
  );
}
