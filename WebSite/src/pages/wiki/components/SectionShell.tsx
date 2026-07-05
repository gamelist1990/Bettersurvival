import type { ReactNode } from 'react';
import { SectionHeader } from '../../../components/common/SectionHeader';
import { PermissionBadge } from './PermissionBadge';

type SectionShellProps = {
  eyebrow: string;
  title: string;
  intro: string;
  scope: 'op' | 'player';
  children: ReactNode;
};

export function SectionShell({ eyebrow, title, intro, scope, children }: SectionShellProps) {
  return (
    <article className="wiki-section">
      <header className="wiki-section-head">
        <SectionHeader eyebrow={eyebrow} title={title} text={intro} />
        <PermissionBadge scope={scope} />
      </header>
      <div className="wiki-section-body">{children}</div>
    </article>
  );
}
