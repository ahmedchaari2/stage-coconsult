import packageInfo from '../../package.json';

export const environment = {
  appVersion: packageInfo.version,
  production: false,
  // API Gateway (routes vers tous les microservices sauf les notifications temps réel).
  apiUrl: 'http://localhost:8080/api',
  apiOrigin: 'http://localhost:8080',
  // Le WebSocket de notifications reste une connexion directe vers notification-service,
  // non routée par la Gateway.
  notificationsWsUrl: 'http://localhost:8086/ws-notifications'
};
