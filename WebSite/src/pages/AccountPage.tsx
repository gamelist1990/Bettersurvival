import { AuthPanel } from '../features/auth/AuthPanel';
import { ProfileCard } from '../features/profile/ProfileCard';
import type { AuthProfile } from '../features/webservice/types';

type AccountPageProps = {
  busy: boolean;
  message: string;
  profile: AuthProfile | null;
  onLogin: (username: string, password: string) => Promise<boolean>;
  onRegister: (code: string, email: string, password: string) => Promise<boolean>;
  onLogout: () => Promise<void>;
};

export function AccountPage(props: AccountPageProps) {
  return (
    <div className="account-page-grid">
      <AuthPanel busy={props.busy} message={props.message} onLogin={props.onLogin} onRegister={props.onRegister} />
      <ProfileCard profile={props.profile} onLogout={props.onLogout} />
    </div>
  );
}
