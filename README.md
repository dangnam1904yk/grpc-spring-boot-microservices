# Spring Boot gRPC Microservices

Hệ thống microservice viết bằng Spring Boot 3.4 / Java 17:

- Client bên ngoài gọi **REST** qua **API Gateway**.
- Các service gọi nhau bằng **gRPC**.
- Service tìm thấy nhau qua **Eureka**.
- Mỗi service có database riêng.
- Đăng nhập bằng **JWT** do **auth-service** cấp. Token được kiểm tra ở gateway, ở từng service và cả ở các lời gọi gRPC.

## Mục lục

- [Kiến trúc](#kiến-trúc)
- [Cấu trúc dự án](#cấu-trúc-dự-án)
- [Luồng xử lý request](#luồng-xử-lý-request)
- [Xử lý request đồng thời](#xử-lý-request-đồng-thời)
- [Xác thực và phân quyền](#xác-thực-và-phân-quyền)
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
├── docker-compose.yml      # Chạy toàn bộ hệ thống + 3 database
├── Dockerfile              # Dùng chung cho mọi service: --build-arg SERVICE=<module>
├── Dockerfile-v1           # Biến thể image nhỏ (jlink)
├── .env.example            # Mẫu ghi đè port / mật khẩu cho docker compose
│
├── common-protos/          # Thư viện: hợp đồng gRPC dùng chung
│   └── src/main/proto/
│       ├── common/v1/paging.proto       # PageRequest, PageInfo
│       ├── category/v1/category.proto   # category.v1.CategoryService
│       └── customer/v1/customer.proto   # customer.v1.CustomerService
│
├── common-grpc/            # Thư viện: hạ tầng dùng chung (Spring Boot auto-configuration)
│   ├── src/main/java/com/project/common/
│   │   ├── exception/      # AppException, ErrorCode (map sang HTTP + gRPC status)
│   │   ├── grpc/server/    # Thread pool, ConcurrencyLimitInterceptor, GrpcExceptionAdvice
│   │   ├── grpc/client/    # GrpcFutures: FutureStub -> CompletableFuture
│   │   ├── security/       # Kiểm tra JWT (REST + gRPC), CurrentUser, chuyển tiếp token qua gRPC
│   │   └── web/            # RestExceptionHandler (lỗi REST dạng ProblemDetail)
│   └── src/test/.../security/test/TestJwt.java   # (test-jar) tạo token trong test
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

### Hợp đồng gRPC

| Service | RPC | Dùng bởi |
|---|---|---|
| `category.v1.CategoryService` | `GetCategory`, `BatchGetCategories`, `ListCategories`, `CreateCategory`, `UpdateCategory` | business |
| `customer.v1.CustomerService` | `GetCustomer`, `BatchGetCustomers` | business |

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

`POST /api/business` với body `{"code":"SHOP1","name":"Shop 1","categoryCode":"FOOD","ownerId":1}`

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

### Cách 2 — Chạy local từng service

1. Chuẩn bị database theo `application.yml` của từng service (hoặc ghi đè bằng biến môi trường):

   | Service | Mặc định | Biến môi trường |
   |---|---|---|
   | category | SQL Server `localhost:1433`, DB `micro_category` (**phải tạo trước**), user `sa` | `DB_HOST`, `DB_USERNAME`, `DB_PASSWORD` |
   | customer | MySQL `localhost:3306`, DB tự tạo, user `root` | `DB_HOST`, `DB_USERNAME`, `DB_PASSWORD` |
   | business | Oracle `jdbc:oracle:thin:@localhost:1521:orcl`, user `hr` | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` |
   | auth | MySQL `localhost:3306`, DB `micro_auth` tự tạo, user `root` | `DB_HOST`, `DB_USERNAME`, `DB_PASSWORD`, `ADMIN_EMAIL`, `ADMIN_PASSWORD` |
   | tất cả | Eureka `http://localhost:8761/eureka` | `EUREKA_URL` |
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
  -d '{"fullName":"Nguyen Van A","phone":"0912345678"}'          # -> {"id":<CUSTOMER_ID>,...}

# 4. Tạo business (Oracle): business gọi gRPC sang category + customer (kèm token của user)
curl -X POST $G/api/business -H "$J" -H "Authorization: Bearer $TOKEN" \
  -d '{"code":"SHOP1","name":"Shop 1","categoryCode":"FOOD","ownerId":<CUSTOMER_ID>}'

# 5. Danh sách business (kèm category + owner lấy qua gRPC batch)
curl "$G/api/business?page=0&size=20" -H "Authorization: Bearer $TOKEN"

# 6. Cập nhật có optimistic lock (gửi version đang giữ)
curl -X PUT $G/api/category/1 -H "$J" -H "Authorization: Bearer $ADMIN" \
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
   - Lỗi nghiệp vụ thì ném `AppException(ErrorCode.X, "...")`. Kiểm tra quyền bằng `CurrentUser`.
   - Thread pool, giới hạn đồng thời, xử lý lỗi, kiểm tra JWT (REST + gRPC) và chuyển tiếp token đã **tự bật** nhờ `common-grpc`, không cần cấu hình.
5. **application.yml**: `spring.application.name`, `grpc.server.port` (không trùng với service khác), `eureka.client.service-url.defaultZone: ${EUREKA_URL:...}`, `app.security.jwks-uri` / `app.security.issuer`, và `app.security.public-paths` nếu có endpoint không cần đăng nhập.
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
