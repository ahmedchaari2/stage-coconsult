export interface NavigationItem {
  id: string;
  title: string;
  type: 'item' | 'collapse' | 'group' | 'divider';
  translate?: string;
  icon?: string;
  hidden?: boolean;
  url?: string;
  classes?: string;
  groupClasses?: string;
  exactMatch?: boolean;
  external?: boolean;
  target?: boolean;
  breadcrumbs?: boolean;
  children?: NavigationItem[];
  link?: string;
  description?: string;
  path?: string;
  roles?: string[];
}

export const NavigationItems: NavigationItem[] = [
  {
    id: 'dashboard-overview',
    title: "Vue d'ensemble",
    translate: 'nav.dashboard.default',
    type: 'item',
    classes: 'nav-item',
    url: '/dashboard',
    icon: 'dashboard',
    exactMatch: true
  },
  {
    id: 'patient-list',
    title: 'Patients',
    translate: 'nav.patients.list',
    type: 'item',
    classes: 'nav-item',
    url: '/patients',
    icon: 'team',
    exactMatch: true
  },
  {
    id: 'appointment-list',
    title: 'Rendez-vous',
    translate: 'nav.appointments.list',
    type: 'item',
    classes: 'nav-item',
    url: '/appointments',
    icon: 'calendar',
    exactMatch: true
  },
  {
    id: 'prescription-list',
    title: 'Prescriptions',
    translate: 'nav.prescriptions.list',
    type: 'item',
    classes: 'nav-item',
    url: '/prescriptions',
    icon: 'medicine-box',
    exactMatch: true
  },
  {
    id: 'medecin-list',
    title: 'Gestion des médecins',
    translate: 'nav.medecins.list',
    type: 'item',
    classes: 'nav-item',
    url: '/medecins',
    icon: 'team',
    exactMatch: true,
    roles: ['ADMIN']
  },
  {
    id: 'acces-log-list',
    title: "Journal d'accès",
    translate: 'nav.accesLog.list',
    type: 'item',
    classes: 'nav-item',
    url: '/journal-acces',
    icon: 'audit',
    exactMatch: true,
    roles: ['ADMIN']
  },
  {
    id: 'utilities',
    title: 'UI Components',
    type: 'group',
    icon: 'icon-navigation',
    hidden: true,
    children: [
      {
        id: 'typography',
        title: 'Typography',
        type: 'item',
        classes: 'nav-item',
        url: '/typography',
        icon: 'font-size',
        hidden: true
      },
      {
        id: 'color',
        title: 'Colors',
        type: 'item',
        classes: 'nav-item',
        url: '/color',
        icon: 'bg-colors',
        hidden: true
      },
      {
        id: 'ant-icons',
        title: 'Ant Icons',
        type: 'item',
        classes: 'nav-item',
        url: 'https://ant.design/components/icon',
        icon: 'ant-design',
        target: true,
        external: true,
        hidden: true
      }
    ]
  }
];
