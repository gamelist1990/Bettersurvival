type PermissionBadgeProps = {
  scope: 'op' | 'player';
};

export function PermissionBadge({ scope }: PermissionBadgeProps) {
  return (
    <span className={`wiki-badge wiki-badge-${scope}`}>
      {scope === 'op' ? 'OP 権限が必要' : 'プレイヤー全員が使用可'}
    </span>
  );
}
