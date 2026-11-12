# Kit Budgétaire — API (Spring Boot)

API REST de gestion de budget personnel (FCFA) : comptes (Wave, Orange Money, banque, espèces…),
catégories, opérations, budgets mensuels avec alertes, objectifs d'épargne et tableau de bord.

**Stack** : Java 21, Spring Boot 4.1, Spring Security (JWT HS256 via resource server), JPA/Hibernate,
Flyway, H2 en mode MySQL (dev) / MySQL 8+ (prod), springdoc OpenAPI.

## Démarrer

```bash
./mvnw spring-boot:run          # profil dev, base H2 dans ./data, port 8080
```

- Swagger : http://localhost:8080/swagger-ui.html
- Compte de démo : `demo@kit.sn` / `demo1234` (6 mois d'historique)
- Console H2 : http://localhost:8080/h2-console (JDBC URL `jdbc:h2:file:./data/kit-budgetaire-mysql`)

Avec Docker (MySQL), depuis la racine du projet :

```bash
docker compose up -d --build    # API sur http://localhost:8095, MySQL sur le port 3307
```

## Configuration

| Variable | Défaut | Rôle |
|---|---|---|
| `APP_JWT_SECRET` | valeur de dev | Clé HMAC du JWT (≥ 32 caractères) — **à changer en prod** |
| `APP_JWT_EXPIRATION_MINUTES` | 1440 | Durée de validité du jeton |
| `APP_CORS_ORIGINS` | http://localhost:4200 | Origines autorisées (séparées par des virgules) |
| `APP_DEMO_DATA` | true | Crée le compte de démonstration au démarrage |
| `MYSQLHOST`, `MYSQLPORT`, `MYSQLDATABASE`, `MYSQLUSER`, `MYSQLPASSWORD` | localhost:3306/kit_budgetaire, budget | Base MySQL (profil `prod`) — noms des variables fournies par Railway |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | — | Surcharge complète (URL JDBC `jdbc:mysql://…`), prioritaire sur les `MYSQL*` |
| `PORT` | 8080 | Port HTTP (injecté par Railway) |

## Déploiement sur Railway

1. Créer un projet, ajouter un service **MySQL** puis un service depuis ce dépôt (dossier `budget-api`, build via le `Dockerfile`).
2. Dans les variables du service API :
   ```
   SPRING_PROFILES_ACTIVE=prod
   MYSQLHOST=${{MySQL.MYSQLHOST}}
   MYSQLPORT=${{MySQL.MYSQLPORT}}
   MYSQLDATABASE=${{MySQL.MYSQLDATABASE}}
   MYSQLUSER=${{MySQL.MYSQLUSER}}
   MYSQLPASSWORD=${{MySQL.MYSQLPASSWORD}}
   APP_JWT_SECRET=<chaîne aléatoire ≥ 32 caractères>
   APP_CORS_ORIGINS=https://<domaine-du-front>
   APP_DEMO_DATA=false
   ANTHROPIC_API_KEY=<facultatif>
   ```
3. Générer un domaine public ; healthcheck : `/actuator/health`. Flyway crée le schéma au premier démarrage.

## Endpoints

| Ressource | Routes |
|---|---|
| Auth | `POST /api/auth/register`, `POST /api/auth/login`, `GET /api/auth/me` |
| Comptes | `GET/POST /api/accounts`, `PUT/DELETE /api/accounts/{id}` |
| Catégories | `GET /api/categories?type=`, `POST`, `PUT/DELETE /{id}` |
| Opérations | `GET /api/transactions?from&to&type&accountId&categoryId&q&page&size`, `GET /export` (CSV), `POST`, `GET/PUT/DELETE /{id}` |
| Budgets | `GET /api/budgets?period=AAAA-MM`, `POST`, `POST /copy-previous?period=`, `PUT/DELETE /{id}` |
| Objectifs | `GET/POST /api/goals`, `PUT/DELETE /{id}`, `POST /{id}/contributions` |
| Tableau de bord | `GET /api/dashboard?period=AAAA-MM` |

Les erreurs suivent le format `ProblemDetail` (RFC 9457), avec `errors` par champ pour les validations.

## Tests

```bash
./mvnw test
```
