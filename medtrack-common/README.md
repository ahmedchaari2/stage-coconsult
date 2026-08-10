# medtrack-common

Entités JPA (et quelques repositories Spring Data transverses) partagées entre les services
Medtrack, préparant la migration vers une architecture microservices adossée à une base de
données commune. Simple lib jar (pas Spring Boot) : les services qui en dépendent (`patient-service`,
`user-service`) fournissent eux-mêmes le contexte Spring/JPA (datasource, auditing, etc.).

`UserRepository` vit ici plutôt que dans `user-service` : `patient-service` en a besoin en
lecture (résoudre l'utilisateur à partir du header `X-User-Id` injecté par la Gateway, relations
médecin référent, etc.), les deux services pointant sur la même base tant qu'elle n'est pas
scindée par service.

## Ordre de build

`medtrack-common` n'est pas publié sur un dépôt distant : elle doit être installée dans le
dépôt Maven local **avant** tout build d'un service qui en dépend.

```bash
cd medtrack-common
mvn clean install
```

Puis, dans chaque service consommateur (ex. `Medtrack`) :

```bash
mvn clean compile
```

Sans cette étape préalable, la résolution de la dépendance `tn.coconsult.medtrack:medtrack-common`
échoue.
