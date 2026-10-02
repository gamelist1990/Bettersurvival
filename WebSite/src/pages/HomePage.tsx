import type { MouseEvent } from "react";
import type { AuthProfile, WebPost } from "../features/webservice/types";
import { displayName } from "../features/webservice/useWebService";

type HomePageProps = {
  profile: AuthProfile | null;
  posts: WebPost[];
  onNavigate: (href: string) => void;
};
const destinations = [
  {
    number: "01",
    title: "ワールドを見渡す",
    label: "WORLD MAP",
    text: "探索の続きも、新しい建築の場所も。マップから次の目的地を探そう。",
    href: "/webmap/",
    action: "マップを開く",
  },
  {
    number: "02",
    title: "暮らしをもっと快適に",
    label: "SURVIVAL GUIDE",
    text: "便利な機能やコマンド、遊び方をWikiで確認。自分に合ったサバイバルへ。",
    href: "/wiki",
    action: "ガイドを読む",
  },
  {
    number: "03",
    title: "みんなの日常をのぞく",
    label: "COMMUNITY",
    text: "できた建築、見つけた景色、今日の出来事。サーバーでの時間を共有しよう。",
    href: "/feed",
    action: "投稿を見る",
  },
];
export function HomePage({ profile, posts, onNavigate }: HomePageProps) {
  const go = (href: string) => (event: MouseEvent<HTMLAnchorElement>) => {
    event.preventDefault();
    onNavigate(href);
  };
  return (
    <div className="survival-home">
      <section className="survival-hero">
        <div className="survival-intro">
          <p className="eyebrow">PEXSERVER / SURVIVAL</p>
          <h1>
            いつもの世界に、
            <br />
            自分だけの続きを。
          </h1>
          <p className="survival-lead">
            遠くまで冒険する。お気に入りの場所に家を建てる。
            <br />
            BetterSurvivalで、あなたのペースのサバイバルを。
          </p>
          <div className="survival-actions">
            <a
              className="primary-button"
              href="/webmap/"
              onClick={go("/webmap/")}
            >
              ワールドマップへ <span aria-hidden="true">↗</span>
            </a>
            <a className="secondary-button" href="/wiki" onClick={go("/wiki")}>
              はじめてのガイド
            </a>
          </div>
          <div className="survival-address">
            <span>JOIN THE WORLD</span>
            <code>play.pexserver.com</code>
            <small>Java Edition · Bedrock Edition（ポート 19132）</small>
          </div>
        </div>
        <figure className="survival-scene">
          <img
            src="/images/wiki/pexserver-hero.png"
            alt="Minecraftのサバイバルワールド"
            fetchPriority="high"
          />
          <figcaption>
            <span>YOUR NEXT CHAPTER</span>
            <strong>BetterSurvival</strong>
          </figcaption>
        </figure>
      </section>
      <section className="survival-links" aria-label="サーバーでの遊び方">
        {destinations.map((item) => (
          <a
            className="survival-destination"
            href={item.href}
            key={item.href}
            onClick={go(item.href)}
          >
            <div className="destination-heading">
              <span>{item.label}</span>
              <span>{item.number}</span>
            </div>
            <h2>{item.title}</h2>
            <p>{item.text}</p>
            <strong>
              {item.action}
              <span aria-hidden="true">↗</span>
            </strong>
          </a>
        ))}
      </section>
      <section className="survival-community">
        <div>
          <p className="eyebrow">AROUND THE WORLD</p>
          <h2>この世界の、今。</h2>
          <p>プレイヤーの投稿から、サバイバルの日常を見つけよう。</p>
          <a href="/feed" onClick={go("/feed")}>
            すべての投稿を見る ↗
          </a>
        </div>
        <div className="survival-posts">
          {posts.slice(0, 2).map((post) => (
            <a
              href="/feed"
              onClick={go("/feed")}
              className="survival-post"
              key={post.id}
            >
              <span>{displayName(post)}</span>
              <p>{post.text || "画像投稿"}</p>
            </a>
          ))}
          {posts.length === 0 && (
            <p className="survival-empty">
              まだ投稿がありません。あなたの最初の景色を共有してみませんか。
            </p>
          )}
        </div>
      </section>
      <section className="survival-account">
        <div>
          <p className="eyebrow">MAKE YOURSELF AT HOME</p>
          <h2>
            {profile
              ? `${displayName(profile)} さん、おかえりなさい。`
              : "あなたのサバイバルを、ここに。"}
          </h2>
          <p>プロフィールを整えて、冒険の記録を残そう。</p>
        </div>
        <a className="primary-button" href="/profile" onClick={go("/profile")}>
          {profile ? "プロフィールへ" : "ログイン・登録"} ↗
        </a>
      </section>
    </div>
  );
}
