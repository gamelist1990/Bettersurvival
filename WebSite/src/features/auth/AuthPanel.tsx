import { useState } from 'react';

type AuthPanelProps = {
  busy: boolean;
  message: string;
  onLogin: (username: string, password: string) => Promise<boolean>;
  onRegister: (code: string, password: string) => Promise<boolean>;
};

export function AuthPanel({ busy, message, onLogin, onRegister }: AuthPanelProps) {
  const [mode, setMode] = useState<'login' | 'register'>('register');
  const [username, setUsername] = useState('');
  const [code, setCode] = useState('');
  const [password, setPassword] = useState('');

  const submit = async () => {
    if (mode === 'register') await onRegister(code, password);
    else await onLogin(username, password);
  };

  return (
    <section className="panel auth-panel">
      <p className="panel-label">Profile Login</p>
      <h2>{mode === 'register' ? 'Minecraft と Web を接続' : 'プロフィールへログイン'}</h2>
      <p className="section-text">Minecraftで発行された確認コードを使って、安全にプロフィールを利用できます。</p>
      <div className="auth-tabs">
        <button className={mode === 'register' ? 'active' : ''} onClick={() => setMode('register')} type="button">登録</button>
        <button className={mode === 'login' ? 'active' : ''} onClick={() => setMode('login')} type="button">ログイン</button>
      </div>
      {mode === 'register' ? (
        <>
          <label>ワンタイムコード<input value={code} onChange={(event) => setCode(event.target.value)} placeholder="6桁コード" inputMode="numeric" /></label>
        </>
      ) : (
        <label>Minecraft ユーザー名<input value={username} onChange={(event) => setUsername(event.target.value)} placeholder="PlayerName" /></label>
      )}
      <label>パスワード<input value={password} onChange={(event) => setPassword(event.target.value)} placeholder="8文字以上" type="password" /></label>
      <button className="primary-button full-button" disabled={busy} onClick={submit} type="button">{busy ? '処理中...' : mode === 'register' ? '登録する' : 'ログイン'}</button>
      {message ? <p className="auth-message">{message}</p> : null}
    </section>
  );
}
