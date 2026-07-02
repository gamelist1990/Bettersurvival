import { useEffect, useState } from 'react';
import { AppLayout } from './components/layout/AppLayout';
import { pageKeyFromPath } from './app/navigation';
import { FeaturesPage } from './pages/FeaturesPage';
import { FeedPage } from './pages/FeedPage';
import { HomePage } from './pages/HomePage';
import { ProfilePage } from './pages/ProfilePage';
import WebMapPage from './Page/WebMapPage';
import { useWebService } from './features/webservice/useWebService';

export default function App() {
  const [path, setPath] = useState(window.location.pathname);
  const service = useWebService();
  const activePage = pageKeyFromPath(path);
  const fullMap = path === '/webmap/full' || path.startsWith('/webmap/full/');

  const navigate = (href: string) => {
    window.history.pushState({}, '', href);
    setPath(window.location.pathname);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  useEffect(() => {
    const onPopState = () => setPath(window.location.pathname);
    window.addEventListener('popstate', onPopState);
    return () => window.removeEventListener('popstate', onPopState);
  }, []);

  const page = (() => {
    switch (activePage) {
      case 'profile':
        return <ProfilePage busy={service.busy} message={service.message} profile={service.profile} posts={service.feedPosts} onLogin={service.login} onRegister={service.register} onLogout={service.logout} onSave={service.updateProfile} onLike={service.likePost} onRepost={service.repostPost} onNavigate={navigate} />;
      case 'feed':
        return <FeedPage busy={service.busy} profile={service.profile} posts={service.feedPosts} onPost={service.postFeed} onLike={service.likePost} onRepost={service.repostPost} onNavigate={navigate} />;
      case 'features':
        return <FeaturesPage />;
      case 'webmap':
        return <WebMapPage full={fullMap} />;
      default:
        return <HomePage profile={service.profile} posts={service.feedPosts} onNavigate={navigate} />;
    }
  })();

  if (fullMap) {
    return <WebMapPage full />;
  }

  return (
    <AppLayout activePage={activePage} profile={service.profile} wide={activePage === 'webmap'} onNavigate={navigate} onLogout={service.logout}>
      {page}
    </AppLayout>
  );
}

