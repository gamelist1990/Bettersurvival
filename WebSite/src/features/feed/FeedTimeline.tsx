import { displayName } from '../webservice/useWebService';
import type { WebPost } from '../webservice/types';

type FeedTimelineProps = {
  posts: WebPost[];
  onNavigate?: (href: string) => void;
  onCopyPostUrl?: (post: WebPost) => void;
};

export function FeedTimeline({ posts, onNavigate, onCopyPostUrl }: FeedTimelineProps) {
  if (posts.length === 0) {
    return <div className="panel empty-feed">まだ投稿はありません。Minecraft チャットか Web 投稿で流れます。</div>;
  }

  return (
    <div className="timeline">
      {posts.map((post) => (
        <article className="post-card" key={post.id}>
          <button className="post-avatar-link" type="button" onClick={() => onNavigate?.(`/profile/@${post.username}`)}>
            <img className="post-avatar" src={post.faceUrl || '/images/clear.png'} alt="" />
          </button>
          <div className="post-body">
            <div className="post-head"><button type="button" onClick={() => onNavigate?.(`/profile/@${post.username}`)}><strong>{displayName(post)}</strong><span>@{post.username}</span></button><span>{post.source === 'web' ? 'Web' : 'Minecraft'}</span></div>
            <p>{post.text}</p>
            {post.attachments?.length ? <div className="post-images">{post.attachments.map((attachment, index) => <img key={`${post.id}-${index}`} src={attachment.url} alt="" />)}</div> : null}
            <div className="post-actions" aria-label="投稿アクション">
              <button type="button">返信</button>
              <button type="button">リポスト</button>
              <button type="button">いいね</button>
              <button type="button" onClick={() => onCopyPostUrl?.(post)}>URLコピー</button>
            </div>
          </div>
        </article>
      ))}
    </div>
  );
}
