# Medtrack — Frontend

Application Angular du système de suivi de patients Medtrack : gestion des patients, dossiers
médicaux, consultations, rendez-vous, prescriptions et tableau de bord, pour un médecin ou un
administrateur de cabinet.

Ce dépôt ne contient que le frontend. Il ne fonctionne pas seul : il faut le backend (services
Spring Boot) démarré à côté.

## Stack

- Angular 22, Bootstrap 5 + ng-bootstrap
- ApexCharts (tableau de bord)
- Transloco pour l'i18n (français/anglais)
- STOMP sur SockJS pour les notifications temps réel

## Architecture réseau

- Tous les appels HTTP passent par l'API Gateway (`http://localhost:8080/api` en développement,
  voir `src/environments/environment.ts`) — le frontend ne connaît jamais l'adresse d'un
  microservice individuel.
- Les notifications temps réel sont une exception : le WebSocket se connecte directement à
  notification-service (`http://localhost:8086/ws-notifications`), la Gateway ne relayant pas
  les upgrades WebSocket dans cette configuration.

## Prérequis

Le backend complet doit tourner : Eureka, le config-server, l'API Gateway, et les microservices
métier (user, patient, medicalrecord, appointment, prescription, notification, dashboard), plus
PostgreSQL et RabbitMQ. Voir les dépôts frères (`api-gateway`, `eureka-server`, `config-server`,
`config-repo`, `user-service`, `patient-service`, `medicalrecord-service`, `appointment-service`,
`prescription-service`, `notification-service`, `dashboard-service`) pour les démarrer.

## Démarrage

```bash
npm install
npm start
```

L'application est servie sur `http://localhost:4200`.

## Build

```bash
npm run build           # build de développement
npm run build-prod      # build de production
```

## Tests et lint

```bash
npm test
npm run lint
```

## Frontend component organization

The frontend uses a feature-first architecture enhanced with Atomic Design principles.

- Generic, reusable, presentation-only components belong in `theme/shared`.
- Business-specific components belong in `features/<domain>`.
- Route-level components coordinate data, services, state, and business actions.
- Shared UI components must not depend on feature services or domain models.
- Atomic Design is used as a composition principle, not as a mandatory global folder taxonomy.
- Components are extracted when they provide real reuse, clearer responsibility, or improved testability.
