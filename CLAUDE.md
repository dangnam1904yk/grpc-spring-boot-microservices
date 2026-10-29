# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

`README.md` (in Vietnamese) is the detailed reference: request flows, the permission rules table, audit-log config, troubleshooting. Code comments and log messages are also written in Vietnamese; keep new ones the same way. User-facing error messages are i18n keys in each service's `src/main/resources/i18n/messages_vi.properties` and `messages_en.properties` (always add a key to both).

## Commands

Maven multi-module (Java 17, Spring Boot 3.4.5). Always build from the **repo root**: services depend on `common-protos` and `common-grpc` through the parent pom.

```bash
mvn clean install -DskipTests            # build everything (needed before running services locally)
mvn -pl common-protos install            # regenerate Java after editing any .proto, before building services
mvn -pl category -am package -DskipTests # build one service + its deps

mvn test                                 # all tests; no real DB/Kafka/Eureka needed (H2 + in-process gRPC)
mvn -pl business test                    # one module
mvn -pl business test -Dtest=BusinessApplicationTests#methodName   # one test
```

On Windows, `mvn clean` can fail with `Proto path element is not a directory` (or "error reading" generated gRPC sources) when the IDE holds a lock on `common-protos/target` or rebuilds it concurrently. Re-run without `clean`.

Running the system:

```bash
docker compose up -d --build                      # whole system + MySQL, SQL Server, Oracle, Kafka
docker compose up -d --build category             # rebuild one service
docker compose -f docker-compose.db.yml up -d     # infra only, ports exposed on host (13306, 11433, 11521, 19092)
./scripts/run-local.sh <module>                   # run one service locally against the infra above (PowerShell: .\scripts\run-local.ps1)
bash scripts/smoke-audit.sh                       # end-to-end audit-log check against a running system
kubectl apply -k k8s/                             # Kustomize, namespace grpc-ms (see README "Cách 3")
```

Local start order: discovery → auth → audit → category → customer → business → apigateway. Default ADMIN is `admin@example.com` / `Admin@123`.

A single shared `Dockerfile` builds any module: `docker build --build-arg SERVICE=<module> .`

Frontend `ecommerce/` (React 19 + Vite + Ant Design 6 + react-router 7, TypeScript): `npm run dev` (port 5175, proxies `/api` to the gateway on :8080, override with `VITE_GATEWAY_URL`), `npm run typecheck`, `npm run build`. `src/api/client.ts` handles tokens: access token in memory, refresh token in localStorage, one auto-refresh on 401 serialized across tabs via Web Locks (the backend revokes all sessions when a rotated refresh token is reused). UI permission checks (`useAuth().hasPermission('user:EDIT')`) only hide things; the backend is the real gate. The admin pages under `src/pages/admin/` (users, roles, permissions + matrix, menus) are ported from the manager-studio frontend. Use `App.useApp()` for message/modal, and antd 6 prop names (`Space orientation`, `Drawer size`, `destroyOnHidden`, `styles.body`), not the deprecated antd 5 ones.

## Architecture

- **Client → apigateway (REST)** → services via `lb://<service>` from Eureka (`discovery`). Routes, circuit breakers and GET-only retries live in `apigateway/.../router/Router.java`; public paths in its `SecurityConfig`. The gateway has no business logic. Everything under `/api/auth/**` (including the authorization admin APIs) is routed to auth-service.
- **Service → service is gRPC only**, using contracts in `common-protos/src/main/proto/<svc>/v1/`. Clients address peers with `grpc.client.<name>.address: discovery:///<service-name>`. Proto rule: only add fields; never renumber or delete them; a breaking change means a new package (`v2`). All ids in protos are UUID v7 `string`s.
- **One database per service** (auth/customer/audit: MySQL, category: SQL Server, business: Oracle). No cross-service FKs; reference other services by id/code. Schema comes from `ddl-auto: update` (no Flyway), so column type changes need explicit handling, e.g. `auth/.../service/LegacySchemaFixer.java`.

### Authentication and authorization

- `auth` issues RS256 JWTs (claims `sub`, `email`, `roles`, `permissions`) and exposes `/api/auth/jwks`. The gateway *and* every service validate the token. `BearerTokenPropagationInterceptor` forwards the user's token on outgoing gRPC calls. Background code with no user has no token, so its gRPC calls get `UNAUTHENTICATED`.
- **Permissions are `menuCode:PERMISSION` strings** (e.g. `category:ADD`, `user:VIEW`). The model was ported from the managerstudio project: a `Menu` (a tree of screens) is the thing being protected, a `Permission` is an action (`VIEW`, `ADD`, `EDIT`, `DELETE`, `EXPORT`...), and a `Role` holds the ticked cells of a role × menu × action matrix (`Role.permissions`) plus the menus shown in its sidebar (`Role.menuIds`). Enforce at the **service layer** with `CurrentUser.requirePermission(Permissions.X)` or `AuthUser.hasPermission(...)`, so REST and gRPC share the same rules. Don't use `@PreAuthorize` or `requireAdmin()` for new checks. Constants live in `common-grpc/.../security/Permissions.java`. Most permissions cover *other users'* data: without one, a user can still act on their own records.
- **`ADMIN` is a super-admin**: `hasPermission` always returns true, and it sees every active menu. Disabled roles grant nothing; disabled actions are left out of tokens; a disabled menu is only hidden from the sidebar.
- Users reference roles by **code** (`user_role` table). Menu, permission and role codes are immutable after creation. `AuthzSeeds` lists the actions, menus and built-in roles (`USER`, `ADMIN`) the code depends on. `AuthorizationBootstrap` creates whatever is missing at startup (seeded items can't be deleted through the API) and migrates the earlier resource/action data (`category:create` → `category:ADD`). **When adding a new permission check, add the constant to `Permissions` and, if the menu/action is new, add it to `AuthzSeeds`.**
- Matrix/status changes take effect on the user's next token (refresh/login, at most 15 minutes). Changing a user's roles, from either the user screen or the role screen (`RoleService.syncMembers`), revokes their refresh tokens. Only ADMIN may grant or revoke the `ADMIN` role.
- New NOT NULL columns on tables that already have rows are named so that MySQL's implicit `0` means the safe default (e.g. `Role.disabled`, not `active`). Existing columns keep their old names behind renamed fields (`Menu.url` → column `path`, `orderIndex` → `sort_order`).

### `common-grpc`: shared infrastructure via auto-configuration

Most cross-cutting behavior lives here and turns on just because a service has `common-grpc` on its classpath. See `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. Don't re-declare it in services. It covers:
- gRPC server thread pool + `ConcurrencyLimitInterceptor` (rejects with `RESOURCE_EXHAUSTED`), and `GrpcExceptionAdvice`.
- Error mapping: throw `AppException(ErrorCode.X, "...")`. `ErrorCode` maps to both an HTTP status (RFC 7807 ProblemDetail via `RestExceptionHandler`) and a gRPC status (`VERSION_CONFLICT` → `ABORTED`/409, `IN_USE` → `FAILED_PRECONDITION`/409, etc.).
- Error messages and i18n: `AppException(ErrorCode.X, "some.key", args...)` and `FieldErrors.add(field, "some.key", args...)` carry message keys (`LocalizedMessage`), translated when the error is returned (`Messages`): REST by `Accept-Language`, gRPC by `accept-language` metadata, which `GrpcLocaleClientInterceptor` forwards on outgoing calls; default `vi`. Shared keys (`error.required`, `error.invalid`...) are in common-grpc `i18n/common_*.properties`; a string with no key is returned as is. Messages with arguments go through MessageFormat (`''` for a quote, `{0,number,#}` for numbers). common-grpc only formats field errors (`errors: {field: message}` in the ProblemDetail, `google.rpc.BadRequest` in gRPC via `GrpcFieldErrors`); services own the validation rules. Validate in the service layer when an endpoint is also exposed over gRPC.
- Shared validation annotations in `com.project.common.validation`: `@CustomRequired`, `@CustomMaxLength(max)`, `@CustomMinLength(min)`, `@CustomRegex(regexp)`, each with `label` (message key or text, set it on every annotation), `args` (`{arg0}`, `{arg1}`... in the message; keys are translated) and an overridable `message = "{key}"` (one key per braces; `"{key, x}"` is not argument syntax). Prefer them over `@NotBlank`/`@Size`/`@Pattern` so messages are translated with the field name. Their validators get `Messages` injected by Spring's validator factory.
- JWT resource-server config for servlet and gRPC (`AuthUser`, `CurrentUser`).
- **Audit logging**: `AuditFilter` (HTTP), `GrpcAuditServerInterceptor` (gRPC) and `EntityChangeListener` (Hibernate post-commit field diffs) feed an in-memory batching sink → Kafka topic `audit-log` → `audit` service → MySQL. `AUDIT_TRANSPORT=grpc` is a fallback that calls `audit.v1.AuditService` directly with an `x-audit-key`. Request id/IP/user travel in MDC: `GrpcFutures` copies it into gRPC callbacks, `MdcTaskDecorator` into `applicationTaskExecutor`, and gRPC metadata carries `x-request-id` to other services.
- **JPA**: entities extend `BaseEntity`, which owns the id (`@Id @UUIDV7 UUID id`; don't redeclare it or use `@GeneratedValue`) plus `createBy`/`lastModify`/dates auto-filled by JPA auditing (already enabled; don't add `@EnableJpaAuditing`). `AuditLog` deliberately doesn't extend it and declares its own `@UUIDV7` id. Parse incoming string ids with `Ids.parse` (bad input → 400/`INVALID_ARGUMENT`). Bulk `@Modifying @Query` updates bypass auditing.
- A **test-jar** provides `TestJwt` (mint tokens without auth-service: `user`, `admin`, `withPermissions(userId, "category:create", ...)`) and `CapturingAuditSink`.

### Service layout and conventions

Each business service uses the packages `controller/` (REST), `grpc/` (`@GrpcService` adapter proto↔entity), `service/` (logic and permission checks, shared by REST and gRPC), `repository/`, `entity/`, `dto/` (records), and `client/` (outgoing gRPC; only `business` has one).

Controllers map entities to DTOs *after* the service's transaction has closed (`open-in-view: false`), so collections that DTOs read must be `FetchType.EAGER`, as in `UserAccount.roles` and `Role.permissions`.

In `category`, catalog tables (`Category`, `Unit`...) share a generic CRUD/search base in `category/.../catalog/` (`CatalogEntity`, `CatalogRepository`, `CatalogService`, `CatalogController`). A new catalog is an entity + repository + request/response records + a `@Service`/`@RestController` subclass under `/api/category/catalogs/<name>`, with hooks (`validate`, `applyExtra`, `extraFilter`, `extraSortFields`, `beforeDelete`) for per-catalog rules; see README "Thêm danh mục mới".

`business` is the reference for calling other services (`BusinessService`, `CategoryGrpcClient`, `GrpcClientConfig`):
- Use `FutureStub` + `GrpcFutures.toCompletableFuture` with a per-call deadline (`app.grpc.<service>.deadline`). Fire independent calls in parallel and combine them with `thenCombine`. Retry on `UNAVAILABLE` only for read methods.
- Controllers return `CompletableFuture`. Blocking DB writes run on `applicationTaskExecutor`, never on gRPC callback threads.
- `SecurityContext` is thread-bound, so call `CurrentUser.require()` on the request thread and pass the value into async stages.
- List endpoints batch lookups (`BatchGet*`) to avoid N+1, and degrade (return `null` for the missing part, still 200) when a dependency is down. Writes fail fast with 503/504 instead.
- Concurrency safety comes from DB unique constraints (duplicate create → 409) and `@Version` optimistic locking (update requests carry `version`; a stale one → 409 / `ABORTED`).

### Tests

Tests are `@SpringBootTest` + `@ActiveProfiles("test")`, with `src/test/resources/application-test.yml` providing H2 (in the target DB's compatibility mode), Eureka disabled, and gRPC on in-process transport (`grpc.server.port: -1`, `in-process:<name>`). `business` tests run fake category/customer gRPC servers in-process. Import `TestJwt.Config` to authenticate. auth tests log in as the seeded ADMIN and share one H2 database across test classes, so use unique codes and emails.

### Adding a service

Follow README "Thêm service mới": add the proto in `common-protos`, add the module to the root `pom.xml` (versions are managed there; the `common-grpc` test-jar needs an explicit `${common-grpc.version}`), add an `application.yml` with a unique `grpc.server.port`, Eureka, `app.security.*` and audit client settings, add a route in the gateway `Router.java`, and add a block to `docker-compose.yml` and `k8s/app/`.
