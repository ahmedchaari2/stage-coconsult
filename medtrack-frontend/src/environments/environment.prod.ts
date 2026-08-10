import packageInfo from '../../package.json';

export const environment = {
  appVersion: packageInfo.version,
  production: true,
  apiUrl: 'http://localhost:8080/api',
  apiOrigin: 'http://localhost:8080',
  notificationsWsUrl: 'http://localhost:8086/ws-notifications'
};
