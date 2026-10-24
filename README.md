# Spring Boot gRPC Microservices

Hệ thống microservice viết bằng Spring Boot 3.4 / Java 17:

- Client bên ngoài gọi **REST** qua **API Gateway**.
- Các service gọi nhau bằng **gRPC**.
- Service tìm thấy nhau qua **Eureka**.
- Mỗi service có database riêng.
- Đăng nhập bằng **JWT** do **auth-service** cấp. Token được kiểm tra ở gateway, ở từng service và cả ở các lời gọi gRPC.
- Mọi request được ghi **audit log** (IP, curl, request, response, nội dung thay đổi dữ liệu), gửi qua **Kafka** về **audit-service**.
- Id của mọi bản ghi là **UUID v7** (`@UUIDV7`), mọi entity kế thừa **`BaseEntity`** (ai tạo / sửa, lúc nào).

## Mục lục

- [Kiến trúc](#kiến-trúc)
- [Cấu trúc dự án](#cấu-trúc-dự-án)
- [Luồng xử lý request](#luồng-xử-lý-request)
- [Xử lý request đồng thời](#xử-lý-request-đồng-thời)
- [Xác thực và phân quyền](#xác-thực-và-phân-quyền)
- [Audit log](#audit-log)
- [Id UUID v7 và BaseEntity](#id-uuid-v7-và-baseentity)
- [Khởi động hệ thống](#khởi-động-hệ-thống)
- [Gọi thử API](#gọi-thử-api)
- [Test](#test)
- [Thêm service mới](#thêm-service-mới)
- [Xử lý sự cố](#xử-lý-sự-cố)

---

## Kiến trúc

```
                    ┌──────────────────────────────┐
  Client ──HTTP──►  │ API Gateway :8080            │  kiểm tra JWT, CORS, định tuyến,
  (ecommerce)       │ (Spring Cloud Gateway)       │  circuit breaker, retry GET
                    └──┬─────────┬──────────┬──────────┬──┘
                 lb:// │   lb:// │    lb:// │    lb:// │     (danh sách instance lấy từ Eureka)
                       ▼         ▼          ▼          ▼
               ┌──────────┐ ┌────────────┐ ┌────────────┐ ┌────────────┐
               │ auth     │ │ category   │ │ business   │ │ customer   │
               │ REST     │ │ REST :7777 │ │ REST :8888 │ │ REST :8990 │
               │ :9000    │ │ gRPC :9091 │ │            │ │ gRPC :9093 │
               └────┬─────┘ └─────▲──────┘ └──┬──────┬──┘ └─────▲──────┘
                    │             │ gRPC + JWT│      │ gRPC + JWT│
                    │             └───────────┘      └───────────┘
                    │ JWKS (public key) ──► gateway + mọi service tự kiểm tra JWT
                  MySQL        SQL Server        Oracle        MySQL

               discovery (Eureka) :8761 ◄── mọi service đăng ký vào đây
```

**Nguyên tắc:**

| Đường đi | Giao thức | Ghi chú |
|---|---|---|
| Client → hệ thống | REST qua gateway | Gateway **không** chứa logic nghiệp vụ |
| Service → service | gRPC | Hợp đồng (`.proto`) dùng chung trong `common-protos` |
| Tìm service | Eureka | Gateway dùng `lb://<service>`, gRPC client dùng `discovery:///<service>` |
| Lưu dữ liệu | Mỗi service một DB | Không có khoá ngoại giữa các service; tham chiếu bằng id/code |
| Xác thực | JWT RS256 (auth-service) | Gateway và từng service đều kiểm tra token. Khi gọi gRPC, token của user được chuyển tiếp theo |

---

## Cấu trúc dự án

```
spring-boot-grpc-microservices/
├── pom.xml                 # Parent chung: version Spring Boot / Cloud / gRPC / protobuf (BOM)
├── docker-compose.yml      # Chạy toàn bộ hệ thống + 3 database + Kafka
├── docker-compose.db.yml   # Chỉ hạ tầng: 3 database + Kafka (mở port ra host) để chạy service local
├── scripts/                # run-local.ps1 / .sh: chạy service trỏ vào DB Docker; smoke-audit.sh: test audit log
├── Dockerfile              # Dùng chung cho mọi service: --build-arg SERVICE=<module>
├── Dockerfile-v1           # Biến thể image nhỏ (jlink)
├── .env.example            # Mẫu ghi đè port / mật khẩu cho docker compose
│
├── common-protos/          # Thư viện: hợp đồng gRPC dùng chung
│   └── src/main/proto/
│       ├── common/v1/paging.proto       # PageRequest, PageInfo
│       ├── category/v1/category.proto   # category.v1.CategoryService
│       ├── customer/v1/customer.proto   # customer.v1.CustomerService
│       └── audit/v1/audit.proto         # audit.v1.AuditService (nhận audit log, đường dự phòng)
│
├── common-grpc/            # Thư viện: hạ tầng dùng chung (Spring Boot auto-configuration)
│   ├── src/main/java/com/project/common/
│   │   ├── exception/      # AppException, ErrorCode (map sang HTTP + gRPC status)
│   │   ├── grpc/server/    # Thread pool, ConcurrencyLimitInterceptor, GrpcExceptionAdvice
│   │   ├── grpc/client/    # GrpcFutures: FutureStub -> CompletableFuture
│   │   ├── security/       # Kiểm tra JWT (REST + gRPC), CurrentUser, chuyển tiếp token qua gRPC
│   │   ├── audit/          # Audit log: filter REST, listener JPA, interceptor gRPC, gửi qua Kafka (kafka/)
│   │   ├── jpa/            # @UUIDV7 (id UUID v7), BaseEntity (create_by, last_modify, ...), JPA Auditing
│   │   ├── id/             # UuidV7.next() (không cần JPA), Ids.parse (chuỗi -> UUID, sai thì 400)
│   │   └── web/            # RestExceptionHandler (lỗi REST dạng ProblemDetail)
│   ├── src/test/.../security/test/TestJwt.java   # (test-jar) tạo token trong test
│   └── src/test/.../audit/test/CapturingAuditSink.java   # (test-jar) thu audit log trong test
│
├── discovery/              # Eureka Server
├── auth/                   # Auth service (MySQL micro_auth): đăng ký, đăng nhập, cấp JWT, JWKS
├── apigateway/             # Spring Cloud Gateway (reactive)
│   └── router/Router.java          # Khai báo route + circuit breaker + retry
│   └── controller/FallbackController.java
│
├── category/               # Category service (SQL Server)
├── customer/               # Customer service (MySQL)
├── business/               # Business service (Oracle) — gọi category + customer qua gRPC
├── audit/                  # Audit service (MySQL micro_audit) — đọc log từ Kafka, tra cứu qua REST
│
└── ecommerce/              # Frontend React + Vite (chưa nối API)
```

Mỗi service nghiệp vụ chia package theo cùng một kiểu:

```
com.project.<service>/
├── controller/   # REST controller (cho gateway gọi vào)
├── grpc/         # @GrpcService: adapter proto <-> entity (cho service khác gọi vào)
├── client/       # gRPC client gọi sang service khác (chỉ business)
├── service/      # Logic nghiệp vụ; REST và gRPC cùng gọi vào đây
├── repository/   # Spring Data JPA
├── entity/       # JPA entity
├── dto/          # Request/response record cho REST
└── config/       # Cấu hình riêng của service (nếu có)
```

### Bảng service

| Module | Tên trong Eureka | REST | gRPC | Database | Đường dẫn qua gateway |
|---|---|---|---|---|---|
| discovery | – | 8761 | – | – | – |
| apigateway | `api-gateway` | 8080 | – | – | – |
| auth | `auth-service` | 9000 | – | MySQL `micro_auth` | `/api/auth/**` |
| category | `category-service` | 7777 | 9091 | SQL Server `micro_category` | `/api/category/**` |
| business | `business-service` | 8888 | (9092, chưa có service) | Oracle `hr` | `/api/business/**` |
| customer | `customer-service` | 8990 | 9093 | MySQL `micro_customer` | `/api/customer/**` |
| audit | `audit-service` | 8777 | 9094 | MySQL `micro_audit` | `/api/audit/**` (chỉ ADMIN) |
| kafka (hạ tầng) | – | 9092 | – | topic `audit-log` (3 partition) | – |

### Hợp đồng gRPC

| Service | RPC | Dùng bởi |
|---|---|---|
| `category.v1.CategoryService` | `GetCategory`, `BatchGetCategories`, `ListCategories`, `CreateCategory`, `UpdateCategory` | business |
| `customer.v1.CustomerService` | `GetCustomer`, `BatchGetCustomers` | business |
| `audit.v1.AuditService` | `Record` (lô log; xác thực bằng `x-audit-key`, không dùng JWT) | **dự phòng**, chỉ khi `AUDIT_TRANSPORT=grpc` (mặc định log đi qua Kafka) |

Lỗi trả bằng **gRPC status chuẩn**, không nhét mã lỗi vào trong body:

| Lỗi nghiệp vụ (`ErrorCode`) | gRPC status | HTTP status |
|---|---|---|
| `NOT_FOUND` | `NOT_FOUND` | 404 |
| `ALREADY_EXISTS` | `ALREADY_EXISTS` | 409 |
| `VERSION_CONFLICT` | `ABORTED` | 409 |
| `INVALID_ARGUMENT` | `INVALID_ARGUMENT` | 400 |
| `UNAUTHENTICATED` (thiếu token, token sai, đăng nhập sai) | `UNAUTHENTICATED` | 401 |
| `FORBIDDEN` (không phải ADMIN / không phải chủ sở hữu) | `PERMISSION_DENIED` | 403 |
| Server quá tải | `RESOURCE_EXHAUSTED` | 503 |
| Quá deadline | `DEADLINE_EXCEEDED` | 504 |

Mọi `id` trong proto là **UUID v7 dạng chuỗi** (`string`). Khi đổi từ `int64` sang `string`, field cũ được `reserved` và id dùng số field mới, để client cũ không đọc nhầm dữ liệu. Id không phải UUID -> `INVALID_ARGUMENT`.

Quy ước đổi proto: **chỉ thêm field mới, không đổi số thứ tự và không xoá field đang dùng**. Thay đổi phá vỡ tương thích thì tạo package mới (`category.v2`).

---

## Luồng xử lý request

### 1. Đọc dữ liệu đơn giản — `GET /api/category/FOOD`

```
Client ─► Gateway ─(lb://category-service)─► CategoryController.getByCode
                                               └─► CategoryService.getByCode ─► SQL Server
```

1. Gateway khớp route `/api/category/**` và lấy danh sách instance `category-service` từ Eureka, rồi chuyển request sang.
2. [CategoryController](category/src/main/java/com/project/category/controller/CategoryController.java) gọi [CategoryService](category/src/main/java/com/project/category/service/CategoryService.java).
3. Không tìm thấy thì ném `AppException(NOT_FOUND)`. `RestExceptionHandler` (trong `common-grpc`) trả về 404 dạng ProblemDetail.

### 2. Tạo business — service gọi service qua gRPC

`POST /api/business` với body `{"code":"SHOP1","name":"Shop 1","categoryCode":"FOOD","ownerId":"<UUID customer>"}`

```
Client ─► Gateway ─► BusinessController.create (trả CompletableFuture, nhả Tomcat thread)
                       └─► BusinessService.create
                             ├─ existsByCode?  (kiểm tra sớm, trùng -> 409)
                             ├─ CategoryGrpcClient.getByCode ──gRPC──► category :9091 ┐ chạy
                             ├─ CustomerGrpcClient.getById  ──gRPC──► customer :9093 ┘ song song
                             └─ thenCombine ─► kiểm tra active ─► saveAndFlush (pool riêng) ─► Oracle
```

1. [BusinessController](business/src/main/java/com/project/business/controller/BusinessController.java) trả về `CompletableFuture`. Tomcat thread được trả lại pool trong lúc chờ gRPC.
2. [BusinessService.create](business/src/main/java/com/project/business/service/BusinessService.java) gửi **2 lời gọi gRPC cùng lúc**. Tổng thời gian chờ bằng lời gọi chậm nhất, không phải tổng của hai lời gọi.
3. Mỗi lời gọi có deadline 2s (`app.grpc.<service>.deadline`). `UNAVAILABLE` được retry tối đa 3 lần, chỉ áp dụng cho các method đọc ([GrpcClientConfig](business/src/main/java/com/project/business/config/GrpcClientConfig.java)).
4. Ở phía server, category và customer xử lý call trên thread pool cố định. Lỗi được [GrpcExceptionAdvice](common-grpc/src/main/java/com/project/common/grpc/server/GrpcExceptionAdvice.java) chuyển thành gRPC status.
5. Business đổi lỗi gRPC thành phản hồi HTTP:
   - `NOT_FOUND` thành 400 ("Category ... không tồn tại").
   - `UNAVAILABLE` thành 503.
   - `DEADLINE_EXCEEDED` thành 504.
6. Bước ghi DB là blocking, nên chạy trên `applicationTaskExecutor`, không chạy trên thread callback của gRPC.

### 3. Danh sách business — batch thay cho N+1

`GET /api/business?page=0&size=20`

- Lấy 1 trang business từ Oracle.
- Gom các `categoryCode` và `ownerId` khác nhau, rồi gọi `BatchGetCategories` và `BatchGetCustomers`. Mỗi service **1 lời gọi**, 2 lời gọi chạy song song.
- Nếu một service lỗi, API vẫn trả 200, chỉ để trống phần dữ liệu lấy từ service đó (`category: null` / `owner: null`). Khi đọc, hệ thống chấp nhận thiếu một phần dữ liệu (degrade) thay vì báo lỗi cả request.

### 4. Khi service đích không chạy

| Tình huống | Kết quả |
|---|---|
| Gateway không gọi được service (chưa chạy, timeout, circuit breaker đang mở) | `FallbackController` trả **503** kèm thông báo |
| Business tạo mới khi customer/category chết | **503** (nhanh, không treo) |
| Business đọc khi customer/category chết | **200**, thiếu phần dữ liệu tương ứng |
| Category/customer nhận quá `max-concurrent-calls` | `RESOURCE_EXHAUSTED` → business trả **503** |

---

## Xử lý request đồng thời

| Tầng | Cơ chế | Cấu hình |
|---|---|---|
| Gateway | Reactive (Netty), timeout kết nối/phản hồi, circuit breaker Resilience4j, chỉ retry GET | `apigateway/.../application.yml`: `spring.cloud.gateway.httpclient`, `resilience4j.*` |
| REST (business) | Controller trả `CompletableFuture`; ghi DB trên pool riêng | `spring.mvc.async.request-timeout`, `spring.task.execution.pool.*` |
| gRPC client | `FutureStub` non-blocking, deadline mỗi call, retry `UNAVAILABLE`, batch + gọi song song | `grpc.client.*`, `app.grpc.<service>.deadline` |
| gRPC server | Thread pool cố định + giới hạn số call đồng thời (vượt thì từ chối ngay) | `app.grpc.server.worker-threads`, `app.grpc.server.max-concurrent-calls` |
| Database | Unique constraint chặn tạo trùng khi nhiều request chạy song song; `@Version` chặn ghi đè khi cập nhật | `spring.datasource.hikari.maximum-pool-size` |

Ví dụ khi có 10 request tạo cùng một `code` đồng thời: đúng 1 request nhận `201`, 9 request còn lại nhận `409`. Cập nhật bằng `version` cũ sẽ nhận `409` (REST) hoặc `ABORTED` (gRPC). Phía client cần tải lại dữ liệu rồi thử lại.

---

## Xác thực và phân quyền

### Token

| Loại | Dạng | Thời hạn | Ghi chú |
|---|---|---|---|
| Access token | JWT ký **RS256** | 15 phút (`app.jwt.access-token-ttl`) | Claim: `sub` (id user), `email`, `roles`, `iss` |
| Refresh token | Chuỗi ngẫu nhiên, DB chỉ lưu SHA-256 | 7 ngày (`app.jwt.refresh-token-ttl`) | **Xoay vòng**: mỗi lần refresh, token cũ bị thu hồi |

- Private key chỉ nằm ở auth-service. Gateway và các service lấy **public key** qua `GET /api/auth/jwks` (`app.security.jwks-uri`), nên không phải chia sẻ secret giữa các service.
- **Phát hiện token bị dùng lại**: nếu một refresh token đã thu hồi được dùng lại, hệ thống coi đó là dấu hiệu token bị đánh cắp và thu hồi **mọi phiên** của user đó.
- Hai request refresh cùng một token cùng lúc: chỉ 1 request nhận token mới. Hệ thống dùng câu lệnh `UPDATE ... WHERE revoked_at IS NULL` để đảm bảo điều này.
- Đăng nhập sai email hay sai mật khẩu đều nhận cùng một thông báo, với thời gian phản hồi như nhau, để kẻ tấn công không dò được email nào đã đăng ký.

### Luồng đăng nhập và gọi API

```
1. POST /api/auth/register {email, password}        -> 201 (role USER)
2. POST /api/auth/login    {email, password}        -> {accessToken, refreshToken, expiresIn}
3. Gọi API: header  Authorization: Bearer <accessToken>
       Gateway kiểm tra JWT (sai/thiếu -> 401 ngay tại gateway)
         └─► Service kiểm tra lại JWT, phân quyền bằng CurrentUser
               └─► gRPC sang service khác: BearerTokenPropagationInterceptor gắn cùng token
                     └─► Service đích kiểm tra JWT (thiếu -> UNAUTHENTICATED)
4. Access token hết hạn: POST /api/auth/refresh {refreshToken} -> cặp token mới
5. Đăng xuất:            POST /api/auth/logout  {refreshToken} -> 204
```

### Quy tắc phân quyền

| Tài nguyên | Không đăng nhập | USER | ADMIN |
|---|---|---|---|
| `GET /api/category/**` | ✓ | ✓ | ✓ |
| Thêm/sửa category (REST và gRPC) | 401 | 403 | ✓ |
| `POST /api/customer` | 401 | Tạo hồ sơ **của chính mình** (email lấy từ token, mỗi user 1 hồ sơ) | ✓ |
| `GET/PUT /api/customer/{id}` | 401 | Chỉ hồ sơ của mình; **không** tự khoá/mở khoá được (`active`) | ✓ |
| `GET /api/customer` (danh sách) | 401 | 403 | ✓ |
| `POST /api/business` | 401 | Chỉ với `ownerId` là hồ sơ customer của mình | ✓ cho bất kỳ ai |
| `GET /api/business/**` | 401 | ✓ | ✓ |
| `PUT /api/auth/users/{id}/roles` | 401 | 403 | ✓ |
| gRPC nội bộ (`GetCustomer`, `BatchGetCategories`...) | `UNAUTHENTICATED` | ✓ (không kiểm tra sở hữu) | ✓ |

Quyền được kiểm tra ở **tầng service**, ví dụ `CurrentUser.requireAdmin()` trong `CategoryService`, nên REST và gRPC áp dụng cùng một quy tắc. API gRPC là API nội bộ: chỉ yêu cầu token hợp lệ, còn việc user được xem dữ liệu nào do service gọi tự quyết định.

Sau khi ADMIN đổi quyền của một user, mọi phiên của user đó bị thu hồi. Quyền mới có hiệu lực khi user đăng nhập lại; access token đang dùng vẫn giữ quyền cũ cho tới khi hết hạn (tối đa 15 phút).

### Dùng trong code

```java
AuthUser user = CurrentUser.require();      // 401 nếu chưa đăng nhập
CurrentUser.requireAdmin();                 // 403 nếu không phải ADMIN
user.id(); user.email(); user.isAdmin();
```

> `SecurityContext` gắn với thread. Trong code bất đồng bộ (`thenApplyAsync`...), phải gọi `CurrentUser.require()` **trên thread của request** rồi truyền giá trị đó vào, như [BusinessService.create](business/src/main/java/com/project/business/service/BusinessService.java).

Trong test, dùng `TestJwt` (từ test-jar của `common-grpc`) để tạo token mà không cần auth-service:

```java
@SpringBootTest
@Import(TestJwt.Config.class)
class MyTest {
    String token = TestJwt.user("u-1");                  // hoặc TestJwt.admin("a-1")
    mvc.perform(get("/api/...").header("Authorization", TestJwt.bearer(token)));
    TestJwt.withToken(grpcStub, token).someRpc(...);
}
```

### Khoá ký JWT

Mặc định auth-service **tự sinh khoá tạm** lúc khởi động (có log cảnh báo). Cách này đủ cho dev, nhưng mỗi lần restart auth là mọi token cũ hết hiệu lực, và không chạy được nhiều instance auth-service. Production phải dùng khoá cố định:

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt.key   # PKCS#8
openssl rsa -in jwt.key -pubout -out jwt.pub
# rồi đặt
JWT_PRIVATE_KEY_LOCATION=file:/run/secrets/jwt.key
JWT_PUBLIC_KEY_LOCATION=file:/run/secrets/jwt.pub
```

---

## Audit log

Mọi service có `common-grpc` **tự động** ghi log, không phải sửa controller hay entity:

| Loại (`type`) | Ghi ở đâu | Nội dung |
|---|---|---|
| `HTTP` | [AuditFilter](common-grpc/src/main/java/com/project/common/audit/web/AuditFilter.java) (Servlet filter, chạy trước Spring Security) | IP client, user, method, URL, **lệnh curl**, header, **request body**, **response body**, status, thời gian xử lý. Ghi cả request bị 401/403 |
| `GRPC` | [GrpcAuditServerInterceptor](common-grpc/src/main/java/com/project/common/audit/grpc/GrpcAuditServerInterceptor.java) | RPC, request / response (text proto), gRPC status, IP client gốc, user. Ghi cả call lỗi / thiếu token / quá tải |
| `ENTITY_CHANGE` | [EntityChangeListener](common-grpc/src/main/java/com/project/common/audit/jpa/EntityChangeListener.java) (Hibernate post-commit) | **Nội dung thay đổi**: entity, id, `CREATE`/`UPDATE`/`DELETE`, danh sách `{"field","old","new"}`. UPDATE chỉ ghi field thực sự đổi |

Ví dụ `changes` khi đổi tên category:

```json
[{"field":"name","old":"Do an","new":"Đồ ăn"},{"field":"updatedAt","old":"2026-10-24T08:00:00Z","new":"2026-10-24T09:15:02Z"},{"field":"version","old":0,"new":1}]
```

### Đường đi của log

```
service (filter / listener / interceptor)
   └─ hàng đợi RAM (không chờ I/O) ─► thread nền gom lô ─► Kafka topic "audit-log" (key = request id)
                                                              └─► audit-service: @KafkaListener đọc lô ─► MySQL
```

### Nối log của cùng một request

```
Client ─► Gateway: gắn X-Request-Id (trả lại cho client trong response)
            └─► business: log HTTP  (requestId = R)
                  ├─gRPC (metadata x-request-id = R, x-client-ip)─► category: log GRPC (R)
                  └─ saveAndFlush trên applicationTaskExecutor ─► log ENTITY_CHANGE (R)
```

Request id, IP, user nằm trong MDC và đi theo request qua mọi thread: `GrpcFutures` copy sang callback gRPC, `MdcTaskDecorator` copy sang `applicationTaskExecutor`, gRPC client gửi kèm sang service khác. Xem toàn bộ một request: `GET /api/audit/requests/{X-Request-Id}`.

### Gửi và lưu log

- Thread của request chỉ bỏ log vào hàng đợi trong RAM (không chờ I/O). Thread nền gom lô (100 log / 1 giây) và gửi vào Kafka ([KafkaAuditSink](common-grpc/src/main/java/com/project/common/audit/kafka/KafkaAuditSink.java)): JSON, nén lz4, `acks=all` + idempotence, key = request id (mọi log của 1 request vào cùng partition, đúng thứ tự).
- **audit-service tắt không mất log**: log nằm chờ trong topic, bật lại thì [AuditLogConsumer](audit/src/main/java/com/project/audit/kafka/AuditLogConsumer.java) đọc tiếp. Offset chỉ được commit sau khi ghi DB xong; DB lỗi thì đọc lại và thử tiếp (chờ tăng dần 1s → 30s, không giới hạn số lần).
- **Không ghi trùng**: mỗi log có id UUID v7 sinh lúc tạo, dùng làm khoá chính ở audit-service. Kafka giao "ít nhất 1 lần" (producer retry, consumer đọc lại sau khi chết), bản trùng bị bỏ qua, kể cả khi nằm cùng 1 lô ([AuditLogWriter](audit/src/main/java/com/project/audit/service/AuditLogWriter.java)).
- **Ghi log không bao giờ làm hỏng hay làm chậm request**: Kafka chết thì producer tự giữ trong bộ đệm và retry tối đa 2 phút (`delivery.timeout.ms`), quá thời gian đó hoặc hàng đợi RAM đầy (10.000) thì bỏ log (có log cảnh báo). Tức là chỉ mất log khi **Kafka** chết lâu, không phải khi audit-service chết.
- Đường **dự phòng** khi không có Kafka: `AUDIT_TRANSPORT=grpc` -> gửi thẳng gRPC `AuditService.Record` (xác thực `x-audit-key`). Đường này mất log nếu audit-service đang tắt.
- `ENTITY_CHANGE` chỉ ghi khi transaction **commit thành công**; rollback thì không có log thay đổi.
- audit-service xoá log cũ hơn `app.audit-server.retention` (mặc định 90 ngày) lúc 3h sáng mỗi ngày.

### Che dữ liệu nhạy cảm

| Ở đâu | Bị thay bằng `***` |
|---|---|
| Header | `Authorization` (giữ lại `Bearer ***`), `Cookie`, `Set-Cookie`, `Proxy-Authorization`, `x-audit-key` |
| JSON body, form, query string, field entity | Tên chứa `password`, `token`, `secret` (ví dụ `password`, `accessToken`, `refreshToken`, `passwordHash`, `tokenHash`) |

Vì vậy lệnh curl trong log không chạy lại được ngay, phải tự điền token. Body dài quá `max-body-length` (10.000 ký tự) bị cắt; body nhị phân chỉ ghi loại và kích thước.

### Tra cứu (chỉ ADMIN)

```bash
# Lịch sử thay đổi của 1 bản ghi
curl "$G/api/audit?type=ENTITY_CHANGE&entityName=Category&entityId=<UUID>" -H "Authorization: Bearer $ADMIN"
# User làm gì từ ngày 1/10, hoặc mọi request từ 1 IP
curl "$G/api/audit?userId=<sub>&from=2026-10-01T00:00:00Z" -H "Authorization: Bearer $ADMIN"
curl "$G/api/audit?clientIp=203.0.113.7&type=HTTP" -H "Authorization: Bearer $ADMIN"
# Chi tiết 1 log (curl, header, body) / mọi log của 1 request
curl "$G/api/audit/<UUID của log>" -H "Authorization: Bearer $ADMIN"
curl "$G/api/audit/requests/<X-Request-Id>" -H "Authorization: Bearer $ADMIN"
```

Bộ lọc: `type`, `service`, `requestId`, `userId`, `clientIp`, `entityName`, `entityId`, `action`, `status`, `from`, `to` (ISO-8601), `page`, `size`. Danh sách không kèm body/curl; xem chi tiết bằng `/api/audit/{id}`.

### Cấu hình (`app.audit.*`, đều có mặc định)

```yaml
app:
  audit:
    enabled: true              # false: tắt toàn bộ (test của auth/business/customer đang tắt)
    entity-changes: true       # log thay đổi dữ liệu (audit-service BẮT BUỘC false, tránh vòng lặp)
    grpc: true                 # log gRPC call đi vào
    masked-fields: [password, token, secret]
    http:
      exclude-paths: [/actuator/**, /error]
      exclude-response-body-paths: []
      max-body-length: 10000
    sender:
      transport: ${AUDIT_TRANSPORT:kafka}   # kafka (mặc định) | grpc (dự phòng)
      topic: audit-log
      key: ${AUDIT_KEY:dev-audit-key}       # chỉ cho grpc: trùng app.audit-server.ingest-key
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
grpc:
  client:
    audit-service:                          # chỉ cho transport=grpc
      address: discovery:///audit-service
      negotiation-type: plaintext
```

audit-service (`app.audit-server.*`): `topic`, `topic-partitions` (3 = tối đa 3 consumer song song, chạy nhiều instance audit-service), `retention`, `ingest-key`. Consumer: `spring.kafka.consumer.group-id: audit-service`, `spring.kafka.listener.type: batch`.

**IP client**: lấy phần tử **cuối** của `X-Forwarded-For` (do gateway thêm vào, client không giả được). Gọi thẳng vào service, không qua gateway, thì dùng IP kết nối.

**Giới hạn**:

- Câu lệnh ghi hàng loạt (`@Modifying @Query`, ví dụ thu hồi refresh token ở auth) không đi qua entity nên không có log `ENTITY_CHANGE`. Log `HTTP` của request đó vẫn có.
- Request/response gRPC không được che field nhạy cảm (các proto hiện tại không có mật khẩu/token).
- `AUDIT_KEY` mặc định `dev-audit-key` chỉ dùng cho dev; production đặt chuỗi ngẫu nhiên trong `.env`.
- Kafka trong compose chỉ 1 broker, không bật xác thực (mạng nội bộ Docker). Production: nhiều broker (replication >= 3), bật SASL/TLS qua `spring.kafka.properties.*` (KafkaAuditSink đọc cả các thuộc tính này).
- Message hỏng (không đọc được JSON) bị bỏ qua và ghi cảnh báo, để không chặn cả partition. Lỗi DB thì retry mãi: nếu một lô lỗi vì nguyên nhân khác ngoài DB chết hoặc trùng khoá thì partition đó sẽ đứng, cần xem log của audit-service.

Trong test, thu log bằng `CapturingAuditSink` (test-jar của `common-grpc`), xem [CategoryApplicationTests](category/src/test/java/com/project/category/CategoryApplicationTests.java):

```java
@Import({ TestJwt.Config.class, CapturingAuditSink.Config.class })
...
audit.find("req-1", AuditType.ENTITY_CHANGE);
```

---

## Id UUID v7 và BaseEntity

Mọi entity dùng chung 2 thứ trong `common-grpc` (package `com.project.common.jpa`):

```java
@Entity
public class Category extends BaseEntity {   // create_by, last_modify, create_date, last_modify_date

    @Id
    @UUIDV7                                  // thay cho @GeneratedValue
    UUID id;                                 // hoặc String id
    ...
}
```

### `@UUIDV7` — id UUID version 7

[UUIDV7](common-grpc/src/main/java/com/project/common/jpa/UUIDV7.java) gắn [UuidV7Generator](common-grpc/src/main/java/com/project/common/jpa/UuidV7Generator.java) (Hibernate). Thuật toán nằm ở [UuidV7](common-grpc/src/main/java/com/project/common/id/UuidV7.java), gọi được ở bất kỳ đâu (`UuidV7.next()`), không cần JPA.

| | IDENTITY (cũ) | UUID v4 | **UUID v7** |
|---|---|---|---|
| Tăng dần theo thời gian | ✓ | ✗ (index bị phân mảnh) | ✓ (48 bit thời gian ms + bộ đếm 12 bit) |
| Biết id trước khi lưu | ✗ (phải hỏi DB) | ✓ | ✓ |
| Hibernate gom nhiều insert thành 1 batch | ✗ | ✓ | ✓ |
| Đoán được / lộ số lượng bản ghi | ✓ (id=1, 2, 3...) | ✗ | ✗ (62 bit ngẫu nhiên) |
| Duy nhất giữa các service / DB | ✗ | ✓ | ✓ |

- Trong cùng 1 ms vẫn tăng dần, kể cả khi đồng hồ lùi (mượn ms kế tiếp). Đã test 20.000 id liên tiếp và 80.000 id từ 8 thread.
- Entity đã được gán id trước khi lưu thì giữ nguyên id đó: audit-service dùng chính id do service gửi đặt cho log, nhờ vậy chống được trùng.
- Kiểu cột: MySQL `binary(16)`, SQL Server `uniqueidentifier`, Oracle `raw(16)`, H2 `uuid`.
- Sắp xếp theo `id` = sắp xếp theo thời gian tạo (các API phân trang đang `Sort.by("id")`).
- Lưu ý SQL Server: `uniqueidentifier` so sánh theo 6 byte **cuối** trước, nên UUID v7 không còn tăng dần trong index clustered của SQL Server. Bảng lớn ở category nên đổi cột sang `binary(16)` (`@JdbcTypeCode(SqlTypes.BINARY)`).
- REST: id là chuỗi UUID (`/api/customer/0192…`); gRPC: `string id`. Id sai định dạng -> 400 / `INVALID_ARGUMENT` ([Ids.parse](common-grpc/src/main/java/com/project/common/id/Ids.java)).
- Claim `sub` của JWT = id user (UUID v7) bên auth-service.

### `BaseEntity` — ai thao tác, lúc nào

[BaseEntity](common-grpc/src/main/java/com/project/common/jpa/BaseEntity.java) (`@MappedSuperclass`) dùng Spring Data JPA Auditing, bật sẵn bằng [JpaAuditingAutoConfiguration](common-grpc/src/main/java/com/project/common/jpa/JpaAuditingAutoConfiguration.java) (service **không** khai báo thêm `@EnableJpaAuditing`):

| Cột | Field | Giá trị |
|---|---|---|
| `create_by` | `createBy` | Id người tạo (claim `sub`), không đổi sau khi tạo |
| `last_modify` | `lastModify` | Id người sửa lần cuối |
| `create_date` | `createDate` | Thời điểm tạo (`Instant`, UTC), không đổi sau khi tạo |
| `last_modify_date` | `lastModifyDate` | Thời điểm sửa lần cuối |

- Không cần tự set: lưu entity là tự điền. User lấy từ SecurityContext (REST, gRPC) hoặc MDC (thread async như `applicationTaskExecutor` của business). Không có user (job nền, `AdminBootstrap` tạo admin) thì là `system`.
- Đang áp dụng cho: `Category`, `Customer`, `Business`, `UserAccount`, `RefreshToken`. `AuditLog` thì không (log chỉ thêm, không sửa; đã có thời điểm và user của sự kiện).
- Câu lệnh cập nhật hàng loạt (`@Modifying @Query`, ví dụ thu hồi refresh token) không đi qua entity nên không cập nhật `last_modify` / `last_modify_date`.
- Các field này cũng xuất hiện trong log `ENTITY_CHANGE` (ví dụ `lastModify: "u-1" -> "u-2"`).

---

## Khởi động hệ thống

### Yêu cầu

| Cách chạy | Cần có |
|---|---|
| Docker (khuyên dùng) | Docker Desktop (Compose v2), RAM trống khoảng 6 GB |
| Chạy local | JDK 17+, Maven 3.9+, SQL Server, Oracle, MySQL tự cài |

### Cách 1 — Docker Compose (toàn bộ hệ thống)

```bash
# (tuỳ chọn) đổi port / mật khẩu
cp .env.example .env

docker compose up -d --build
docker compose ps                         # đợi tất cả "healthy" (lần đầu khoảng 2–3 phút)
```

| Địa chỉ | Mô tả |
|---|---|
| http://localhost:8080 | API Gateway |
| http://localhost:8761 | Eureka dashboard |

Tài khoản ADMIN được tạo sẵn: `admin@example.com` / `Admin@123` (đổi bằng `ADMIN_EMAIL`, `ADMIN_PASSWORD` trong `.env`).

Thứ tự khởi động được điều khiển bằng healthcheck:

```
DB (mssql → mssql-init tạo DB, mysql, oracle) + discovery
   → auth, category, customer
   → business
apigateway chỉ cần discovery
```

Các lệnh hay dùng:

```bash
docker compose logs -f business category           # xem log
docker compose up -d --build category              # build và chạy lại 1 service
GATEWAY_PORT=8085 docker compose up -d             # port 8080 trên máy đã bị chiếm
docker compose down                                # tắt (giữ dữ liệu)
docker compose down -v                             # tắt và xoá luôn dữ liệu DB
```

Port của database **không** mở ra máy host, nên không đụng với DB cài sẵn trên máy.

Kiểm tra nhanh audit log sau khi hệ thống đã chạy: `bash scripts/smoke-audit.sh` (tạo + sửa 1 category, rồi kiểm tra audit-service đã lưu log HTTP và log thay đổi của đúng request đó).

### Cách 2 — Chạy local từng service, database trong Docker

Không cần cài DB: [docker-compose.db.yml](docker-compose.db.yml) chỉ chạy 3 database + Kafka và mở port ra máy host. Port lệch khỏi port chuẩn để không đụng DB cài sẵn trên máy:

| Database | Port host | Tài khoản | Dùng cho |
|---|---|---|---|
| MySQL 8.4 | `13306` (`MYSQL_PORT`) | `root` / `dangnam` | auth, customer, audit (DB tự tạo) |
| SQL Server 2022 | `11433` (`MSSQL_PORT`) | `sa` / `Dangnam@2024` | category (`micro_category` do `mssql-init` tạo) |
| Oracle Free 23 | `11521` (`ORACLE_PORT`) | `hr` / `hr`, service `FREEPDB1` | business |
| Kafka 3.8 (KRaft) | `19092` (`KAFKA_PORT`) | – | mọi service (audit log) |

```bash
docker compose -f docker-compose.db.yml up -d
docker compose -f docker-compose.db.yml ps      # đợi tất cả "healthy"
mvn clean install -DskipTests

# Mỗi service 1 terminal, theo thứ tự. Script tự đặt DB_PORT / DB_PASSWORD / DB_URL / KAFKA_BOOTSTRAP_SERVERS
.\scripts\run-local.ps1 discovery               # PowerShell (bash: ./scripts/run-local.sh discovery)
.\scripts\run-local.ps1 auth
.\scripts\run-local.ps1 audit
.\scripts\run-local.ps1 category
.\scripts\run-local.ps1 customer
.\scripts\run-local.ps1 business
.\scripts\run-local.ps1 apigateway

bash scripts/smoke-audit.sh                     # kiểm tra end-to-end
docker compose -f docker-compose.db.yml down    # tắt DB (thêm -v để xoá dữ liệu)
```

Hoặc tự chuẩn bị DB:

1. Chuẩn bị database theo `application.yml` của từng service (hoặc ghi đè bằng biến môi trường):

   | Service | Mặc định | Biến môi trường |
   |---|---|---|
   | category | SQL Server `localhost:1433`, DB `micro_category` (**phải tạo trước**), user `sa` | `DB_HOST`, `DB_PORT`, `DB_USERNAME`, `DB_PASSWORD` |
   | customer | MySQL `localhost:3306`, DB tự tạo, user `root` | `DB_HOST`, `DB_PORT`, `DB_USERNAME`, `DB_PASSWORD` |
   | business | Oracle `jdbc:oracle:thin:@localhost:1521:orcl`, user `hr` | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` |
   | auth | MySQL `localhost:3306`, DB `micro_auth` tự tạo, user `root` | `DB_HOST`, `DB_PORT`, `DB_USERNAME`, `DB_PASSWORD`, `ADMIN_EMAIL`, `ADMIN_PASSWORD` |
   | audit | MySQL `localhost:3306`, DB `micro_audit` tự tạo, user `root` | `DB_HOST`, `DB_PORT`, `DB_USERNAME`, `DB_PASSWORD`, `AUDIT_KEY` |
   | tất cả | Eureka `http://localhost:8761/eureka` | `EUREKA_URL` |
   | tất cả | Kafka `localhost:9092` (audit log) | `KAFKA_BOOTSTRAP_SERVERS`, `AUDIT_TRANSPORT` |
   | tất cả (trừ auth) | JWKS `http://localhost:9000/api/auth/jwks`, issuer `http://auth-service` | `AUTH_JWKS_URI`, `JWT_ISSUER` |

2. Build từ **thư mục gốc**. Các module dùng chung parent pom và phụ thuộc vào `common-protos`, `common-grpc`:

   ```bash
   mvn clean install -DskipTests
   ```

3. Chạy theo thứ tự, mỗi service một terminal:

   ```bash
   java -jar discovery/target/discovery-0.0.1-SNAPSHOT.jar
   ADMIN_EMAIL=admin@example.com ADMIN_PASSWORD=Admin@123 java -jar auth/target/auth-0.0.1-SNAPSHOT.jar
   java -jar category/target/category-0.0.1-SNAPSHOT.jar
   java -jar customer/target/customer-0.0.1-SNAPSHOT.jar
   java -jar audit/target/audit-0.0.1-SNAPSHOT.jar
   java -jar business/target/business-0.0.1-SNAPSHOT.jar
   java -jar apigateway/target/apigateway-0.0.1-SNAPSHOT.jar
   ```

   Hoặc chạy bằng Maven: `mvn -pl category spring-boot:run`.

> Sửa file `.proto` xong thì phải build lại `common-protos` (`mvn -pl common-protos install`) để sinh lại code Java trước khi build các service.

### Build image riêng lẻ

```bash
docker build --build-arg SERVICE=category -t grpc-ms/category .
docker build -f Dockerfile-v1 --build-arg SERVICE=apigateway -t grpc-ms/apigateway:slim .
```

---

## Gọi thử API

Tất cả đều đi qua gateway (`http://localhost:8080`).

```bash
G=localhost:8080; J="Content-Type: application/json"

# 1. ADMIN đăng nhập, tạo category (SQL Server)
ADMIN=$(curl -s -X POST $G/api/auth/login -H "$J" \
  -d '{"email":"admin@example.com","password":"Admin@123"}' | sed 's/.*"accessToken":"\([^"]*\)".*/\1/')
curl -X POST $G/api/category -H "$J" -H "Authorization: Bearer $ADMIN" \
  -d '{"code":"FOOD","name":"Do an"}'

# 2. User đăng ký + đăng nhập
curl -X POST $G/api/auth/register -H "$J" -d '{"email":"a@example.com","password":"secret123"}'
TOKEN=$(curl -s -X POST $G/api/auth/login -H "$J" \
  -d '{"email":"a@example.com","password":"secret123"}' | sed 's/.*"accessToken":"\([^"]*\)".*/\1/')

# 3. User tạo hồ sơ customer của mình (MySQL). Email lấy từ token
curl -X POST $G/api/customer -H "$J" -H "Authorization: Bearer $TOKEN" \
  -d '{"fullName":"Nguyen Van A","phone":"0912345678"}'          # -> {"id":"<CUSTOMER_ID>",...} (UUID)

# 4. Tạo business (Oracle): business gọi gRPC sang category + customer (kèm token của user)
curl -X POST $G/api/business -H "$J" -H "Authorization: Bearer $TOKEN" \
  -d '{"code":"SHOP1","name":"Shop 1","categoryCode":"FOOD","ownerId":"<CUSTOMER_ID>"}'

# 5. Danh sách business (kèm category + owner lấy qua gRPC batch)
curl "$G/api/business?page=0&size=20" -H "Authorization: Bearer $TOKEN"

# 6. Cập nhật có optimistic lock (gửi version đang giữ)
curl -X PUT $G/api/category/<CATEGORY_ID> -H "$J" -H "Authorization: Bearer $ADMIN" \
  -d '{"name":"Do an nhanh","active":true,"version":0}'
```

| Service | Endpoint |
|---|---|
| auth | `POST /api/auth/register`, `POST /api/auth/login`, `POST /api/auth/refresh`, `POST /api/auth/logout`, `GET /api/auth/me`, `PUT /api/auth/users/{id}/roles` (ADMIN), `GET /api/auth/jwks` |
| category | `GET /api/category/{code}`, `GET /api/category?parentCode=&page=&size=`, `POST /api/category`, `PUT /api/category/{id}` |
| customer | `GET /api/customer/me`, `GET /api/customer/{id}`, `GET /api/customer?page=&size=` (ADMIN), `POST /api/customer`, `PUT /api/customer/{id}` |
| business | `GET /api/business/{id}`, `GET /api/business?page=&size=`, `POST /api/business` |

Lỗi trả về theo chuẩn RFC 7807, ví dụ:

```json
{ "status": 409, "title": "Conflict", "detail": "Category code=FOOD đã tồn tại", "code": "ALREADY_EXISTS" }
```

---

## Test

```bash
mvn test                 # toàn bộ (không cần DB thật: test dùng H2 + gRPC in-process)
mvn -pl business test    # một module
```

| Module | Nội dung test |
|---|---|
| common-grpc | Bật auto-config đúng điều kiện và đúng thứ tự với net.devh; interceptor từ chối khi quá tải |
| auth | Đăng ký/đăng nhập, xoay vòng refresh token, phát hiện token bị dùng lại, refresh/đăng ký đồng thời, chỉ ADMIN đổi quyền, JWKS chỉ lộ public key |
| category | gRPC thật (in-process) + H2: map lỗi sang gRPC status, tạo/cập nhật đồng thời, gRPC/REST thiếu token hoặc sai quyền |
| customer | REST + gRPC, validation, chủ sở hữu/ADMIN, tạo hồ sơ trùng đồng thời |
| business | Category/customer giả chạy in-process: gọi song song, deadline, degrade, batch, tạo trùng đồng thời, phân quyền, chuyển tiếp token qua gRPC |

---

## Thêm service mới

Ví dụ thêm `order-service` có gRPC server:

1. **Hợp đồng**: tạo `common-protos/src/main/proto/order/v1/order.proto` với `package order.v1;` và `option java_package = "com.project.grpc.order.v1";`.
2. **Module**: tạo thư mục `order/`. Trong pom, khai báo parent là `spring-boot-grpc-microservices`, rồi thêm `<module>order</module>` vào [pom.xml](pom.xml) gốc.
3. **Dependency**: `common-protos`, `common-grpc`, `net.devh:grpc-spring-boot-starter`, `spring-boot-starter-oauth2-resource-server`. Thêm `common-grpc` dạng `test-jar` (scope test) để có `TestJwt`. Không cần ghi version, version đã quản lý ở pom gốc (riêng test-jar thì ghi `${common-grpc.version}`).
4. **Code**:
   - Class `@GrpcService` kế thừa `OrderServiceGrpc.OrderServiceImplBase`.
   - Entity kế thừa `BaseEntity`, id là `@Id @UUIDV7 UUID id` (xem [Id UUID v7 và BaseEntity](#id-uuid-v7-và-baseentity)). Id trong proto là `string`, đổi sang UUID bằng `Ids.parse`.
   - Lỗi nghiệp vụ thì ném `AppException(ErrorCode.X, "...")`. Kiểm tra quyền bằng `CurrentUser`.
   - Thread pool, giới hạn đồng thời, xử lý lỗi, kiểm tra JWT (REST + gRPC), chuyển tiếp token và audit log đã **tự bật** nhờ `common-grpc`, không cần cấu hình.
5. **application.yml**: `spring.application.name`, `grpc.server.port` (không trùng với service khác), `eureka.client.service-url.defaultZone: ${EUREKA_URL:...}`, `app.security.jwks-uri` / `app.security.issuer`, `app.security.public-paths` nếu có endpoint không cần đăng nhập, và `grpc.client.audit-service` + `app.audit.sender.key` để gửi audit log (xem [Audit log](#audit-log)).
6. **Gateway**: thêm một dòng `route(...)` trong [Router.java](apigateway/src/main/java/com/project/apigateway/router/Router.java). Nếu có đường dẫn không cần đăng nhập, thêm vào [SecurityConfig](apigateway/src/main/java/com/project/apigateway/security/SecurityConfig.java).
7. **Docker**: copy một block service trong `docker-compose.yml` và đổi `SERVICE`, port healthcheck, DB.

Gọi service đó từ service khác: tạo client dùng `FutureStub` + `GrpcFutures.toCompletableFuture` và đặt deadline cho mỗi call, giống [CategoryGrpcClient](business/src/main/java/com/project/business/client/CategoryGrpcClient.java). Khai báo `grpc.client.<tên>.address: discovery:///<tên-service>`.

---

## Xử lý sự cố

| Hiện tượng | Nguyên nhân / cách xử lý |
|---|---|
| Gateway trả 503 "tạm thời không khả dụng" ngay sau khi start | Gateway chưa thấy service mới trong Eureka (cache khoảng 30s). Đợi rồi thử lại. |
| Mọi request đột nhiên nhận 401 sau khi restart auth-service | Auth đang dùng khoá tạm, nên restart sinh khoá mới và token cũ không còn hợp lệ. Đăng nhập lại, hoặc cấu hình `JWT_PRIVATE_KEY_LOCATION` / `JWT_PUBLIC_KEY_LOCATION`. |
| 401 dù token đúng khi chạy local | Kiểm tra `app.security.issuer` của service có trùng `app.jwt.issuer` của auth không, và `AUTH_JWKS_URI` có truy cập được từ service không. |
| Trình duyệt báo lỗi CORS | Thêm origin của frontend vào `CORS_ALLOWED_ORIGINS` (gateway). Không dùng `*` khi cho phép credentials. |
| gRPC trả `UNAUTHENTICATED` khi gọi từ code chạy nền (không có request của user) | Lời gọi gRPC chỉ mang theo token khi có user đang đăng nhập. Tác vụ nền cần token riêng cho service (chưa hỗ trợ, xem ghi chú bên dưới). |
| `Port 8080 was already in use` | Đặt `GATEWAY_PORT=8085` (Docker) hoặc `--server.port=8085` (local). |
| Tiếng Việt lưu vào SQL Server thành `?` | Category đã bật `hibernate.use_nationalized_character_data` để tạo cột `nvarchar`. Bảng đã tạo trước đó với `varchar` thì phải sửa cột bằng tay hoặc xoá bảng để tạo lại (`ddl-auto: update` không tự đổi kiểu cột). |
| `mvn clean` lỗi `Proto path element is not a directory` (Windows) | IDE đang khoá `common-protos/target`. Chạy lại không kèm `clean`, hoặc đóng IDE. |
| Service gRPC không tìm thấy nhau | Kiểm tra trong Eureka dashboard xem instance có metadata `gRPC_port` chưa, và `grpc.client.<tên>.address` có đúng tên service không. |
| Gọi bằng curl trên Git Bash (Windows) bị lỗi `Failed to read request` khi có dấu tiếng Việt | Do terminal không gửi dữ liệu dạng UTF-8. Gửi body từ file: `--data-binary @body.json`. |

### Giới hạn hiện tại của phần bảo mật

- **Chưa giới hạn số lần đăng nhập sai** (chống dò mật khẩu): nên thêm rate limit ở gateway (`RequestRateLimiter`, cần Redis) hoặc khoá tài khoản tạm thời.
- **Chưa có token cho service-to-service**: gRPC chỉ mang token của user đang gọi. Tác vụ nền / job định kỳ cần thêm luồng client-credentials ở auth-service.
- **Access token không thu hồi được tức thì**: logout và đổi quyền chỉ thu hồi refresh token. Access token vẫn dùng được tới khi hết hạn (15 phút).
- **Kênh gRPC đang là plaintext**: token đi trong mạng nội bộ không được mã hoá. Production nên bật TLS/mTLS cho gRPC.
