import { NavLink, Outlet } from 'react-router-dom';
import {
  IconHome2, IconBooks, IconBriefcase2, IconMicrophone2,
  IconRobot, IconStar, IconSettings, IconLogout, IconBolt,
} from '@tabler/icons-react';
import { useLogout } from '@/hooks/useAuth';
import { useUsage } from '@/hooks/useDashboard';
import { useMe } from '@/hooks/useMe';
import { Banner } from '@/components/ui/Banner';
import styles from './AppShell.module.css';

const NAV = [
  { to: '/',           icon: IconHome2,        label: 'Home'       },
  { to: '/study',      icon: IconBooks,         label: 'Study plan' },
  { to: '/interviews', icon: IconBriefcase2,    label: 'Interviews' },
  { to: '/mock',       icon: IconMicrophone2,   label: 'Mock'       },
  { to: '/jobs',       icon: IconBolt,          label: 'Jobs'       },
  { to: '/star',       icon: IconStar,          label: 'STAR bank'  },
  { to: '/settings',   icon: IconSettings,      label: 'Settings'   },
];

export function AppShell() {
  const logout    = useLogout();
  const { data: usage } = useUsage();
  const { data: me }    = useMe();

  return (
    <div className={styles.shell}>
      {/* Sidebar */}
      <nav className={styles.sidebar}>
        <div className={styles.logo}>
          <IconRobot size={22} style={{ color: 'var(--accent)' }} />
          <span>PrepLoop</span>
        </div>

        <ul className={styles.navList}>
          {NAV.map(({ to, icon: Icon, label }) => (
            <li key={to}>
              <NavLink
                to={to}
                end={to === '/'}
                className={({ isActive }) =>
                  [styles.navItem, isActive ? styles.navActive : ''].join(' ')
                }
              >
                <Icon size={18} strokeWidth={1.75} />
                <span>{label}</span>
              </NavLink>
            </li>
          ))}
        </ul>

        <div className={styles.sidebarBottom}>
          {me && (
            <div className={styles.userRow}>
              <span className={styles.userAvatar}>
                {me.user.displayName?.charAt(0).toUpperCase() ?? '?'}
              </span>
              <span className={styles.userName}>{me.user.displayName ?? me.user.email}</span>
            </div>
          )}
          <button className={styles.logoutBtn} onClick={logout} title="Sign out">
            <IconLogout size={16} strokeWidth={1.75} />
          </button>
        </div>
      </nav>

      {/* Main */}
      <div className={styles.main}>
        {usage?.warning && (
          <div className={styles.bannerRow}>
            <Banner
              variant="amber"
              message="Running on economy mode — monthly budget reached. AI responses may use a cheaper model."
            />
          </div>
        )}
        <main className={styles.content}>
          <Outlet />
        </main>
      </div>
    </div>
  );
}
