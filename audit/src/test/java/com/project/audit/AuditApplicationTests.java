package com.project.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.audit.entity.AuditLog;
import com.project.audit.repository.AuditLogRepository;
import com.project.audit.service.AuditLogWriter;
import com.project.common.audit.AuditProperties;
import com.project.common.audit.grpc.AuditMetadata;
import com.project.common.audit.kafka.KafkaAuditSink;
import com.project.common.id.UuidV7;
import com.project.common.security.test.TestJwt;
import com.project.grpc.audit.v1.AuditEvent;
import com.project.grpc.audit.v1.AuditServiceGrpc;
import com.project.grpc.audit.v1.AuditType;
import com.project.grpc.audit.v1.RecordRequest;

import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import net.devh.boot.grpc.client.inject.GrpcClient;

/**
 * Nhận log qua Kafka (broker nhúng) và qua gRPC dự phòng (in-process) như các service gửi về,
 * rồi tra cứu qua REST với DB H2.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwt.Config.class)
@EmbeddedKafka(partitions = 1, topics = "audit-log")
class AuditApplicationTests {

	@Autowired
	EmbeddedKafkaBroker kafka;

	@Autowired
	AuditLogRepository repository;

	@Autowired
	AuditLogWriter writer;

	@GrpcClient("inProcess")
	AuditServiceGrpc.AuditServiceBlockingStub anonymousStub;

	@Autowired
	MockMvc mvc;

	@Autowired
	ObjectMapper objectMapper;


	@Test
	void writerKeepsSenderIdAndSkipsDuplicates() {
		UUID id = UuidV7.next();
		com.project.common.audit.AuditEvent event = com.project.common.audit.AuditEvent.builder()
				.id(id).type(com.project.common.audit.AuditType.HTTP).requestId("req-dup").timestamp(Instant.now())
				.build();
		com.project.common.audit.AuditEvent other = event.toBuilder().id(UuidV7.next()).build();

		assertThat(writer.write(List.of(event))).isEqualTo(1);
		// Lô có 1 log trùng + 1 log mới: chỉ log mới được lưu, không lỗi cả lô
		assertThat(writer.write(List.of(event, other))).isEqualTo(1);

		assertThat(repository.findById(id)).isPresent();
		assertThat(repository.findAll()).filteredOn(l -> "req-dup".equals(l.getRequestId())).hasSize(2);
	}

	@Test
	void logsSentToKafkaAreStoredOnceEvenIfDeliveredTwice() throws Exception {
		AuditProperties properties = new AuditProperties();
		properties.getSender().setFlushInterval(Duration.ofMillis(20));
		KafkaAuditSink sink = new KafkaAuditSink(
				KafkaAuditSink.createProducer(List.of(kafka.getBrokersAsString()), Map.of()), objectMapper, properties);
		sink.afterPropertiesSet();

		UUID id = UuidV7.next();
		com.project.common.audit.AuditEvent event = com.project.common.audit.AuditEvent.builder()
				.id(id)
				.type(com.project.common.audit.AuditType.ENTITY_CHANGE)
				.requestId("req-kafka")
				.service("category-service")
				.timestamp(Instant.now())
				.userId("u-1")
				.entityName("Category")
				.entityId("c-1")
				.action("UPDATE")
				.changes("[{\"field\":\"name\",\"old\":\"A\",\"new\":\"B\"}]")
				.build();
		// Giả lập Kafka giao lại cùng 1 log (producer retry / consumer đọc lại sau khi chết)
		sink.publish(event);
		sink.publish(event);
		sink.destroy();

		AuditLog saved = await(() -> repository.findById(id).orElse(null));
		assertThat(saved.getAction()).isEqualTo("UPDATE");
		assertThat(saved.getChanges()).contains("\"new\":\"B\"");
		// Đợi thêm cho chắc bản gửi lại cũng đã được consumer xử lý
		Thread.sleep(500);
		assertThat(repository.findAll()).filteredOn(l -> "req-kafka".equals(l.getRequestId())).hasSize(1);
	}

	@Test
	void ingestRequiresAuditKey() {
		RecordRequest request = RecordRequest.newBuilder().addEvents(httpEvent("req-nokey")).build();

		assertThatThrownBy(() -> anonymousStub.record(request))
				.isInstanceOf(StatusRuntimeException.class)
				.extracting(e -> ((StatusRuntimeException) e).getStatus().getCode())
				.isEqualTo(Status.Code.UNAUTHENTICATED);
		assertThatThrownBy(() -> withKey("wrong-key").record(request))
				.isInstanceOf(StatusRuntimeException.class)
				.extracting(e -> ((StatusRuntimeException) e).getStatus().getCode())
				.isEqualTo(Status.Code.UNAUTHENTICATED);
	}

	@Test
	void ingestedLogsCanBeSearchedByAdminAsTimeline() throws Exception {
		long now = Instant.now().toEpochMilli();
		RecordRequest request = RecordRequest.newBuilder()
				.addEvents(httpEvent("req-timeline").toBuilder().setTimestampMillis(now).build())
				.addEvents(AuditEvent.newBuilder()
						.setType(AuditType.AUDIT_TYPE_ENTITY_CHANGE)
						.setRequestId("req-timeline")
						.setService("category-service")
						.setTimestampMillis(now + 1)
						.setUserId("u-1")
						.setEntityName("Category")
						.setEntityId("42")
						.setAction("UPDATE")
						.setChanges("[{\"field\":\"name\",\"old\":\"A\",\"new\":\"B\"}]")
						.build())
				.build();
		assertThat(withKey("test-key").record(request).getAccepted()).isEqualTo(2);

		String admin = TestJwt.bearer(TestJwt.admin("admin-1"));
		JsonNode timeline = json(mvc.perform(get("/api/audit/requests/req-timeline").header("Authorization", admin))
				.andReturn());
		assertThat(timeline).hasSize(2);
		assertThat(timeline.get(0).get("type").asText()).isEqualTo("HTTP");
		assertThat(timeline.get(0).get("curl").asText()).startsWith("curl -X PUT");
		assertThat(timeline.get(0).get("requestHeaders").get("Content-Type").get(0).asText())
				.isEqualTo("application/json");
		// changes trả về dạng JSON thật, không phải chuỗi
		assertThat(timeline.get(1).get("changes").get(0).get("new").asText()).isEqualTo("B");

		JsonNode history = json(mvc.perform(get("/api/audit")
				.param("type", "ENTITY_CHANGE").param("entityName", "Category").param("entityId", "42")
				.header("Authorization", admin)).andReturn());
		assertThat(history.get("totalElements").asLong()).isEqualTo(1);
		assertThat(history.get("content").get(0).get("action").asText()).isEqualTo("UPDATE");
		// Danh sách không kèm body / curl
		assertThat(history.get("content").get(0).has("curl")).isFalse();
	}

	@Test
	void onlyAdminCanReadLogsAndReadsAreAuditedToo() throws Exception {
		assertThat(mvc.perform(get("/api/audit")).andReturn().getResponse().getStatus()).isEqualTo(401);
		assertThat(mvc.perform(get("/api/audit").header("Authorization", TestJwt.bearer(TestJwt.user("u-2"))))
				.andReturn().getResponse().getStatus()).isEqualTo(403);

		// Lần tra cứu bị từ chối cũng được ghi lại (ghi thẳng DB, bất đồng bộ)
		long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
		boolean found = false;
		while (!found && System.nanoTime() < deadline) {
			found = repository.findAll().stream()
					.anyMatch(l -> "u-2".equals(l.getUserId()) && "403".equals(l.getStatus())
							&& l.getService().equals("audit-service") && l.getResponseBody() == null);
			if (!found) {
				Thread.sleep(50);
			}
		}
		assertThat(found).isTrue();
	}

	private static <T> T await(Supplier<T> probe) throws InterruptedException {
		long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
		while (System.nanoTime() < deadline) {
			T value = probe.get();
			if (value != null) {
				return value;
			}
			Thread.sleep(100);
		}
		throw new AssertionError("Hết thời gian chờ");
	}

	private AuditServiceGrpc.AuditServiceBlockingStub withKey(String key) {
		Metadata headers = new Metadata();
		headers.put(AuditMetadata.AUDIT_KEY, key);
		return anonymousStub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
	}

	private static AuditEvent httpEvent(String requestId) {
		return AuditEvent.newBuilder()
				.setType(AuditType.AUDIT_TYPE_HTTP)
				.setRequestId(requestId)
				.setService("category-service")
				.setClientIp("203.0.113.7")
				.setUserId("u-1")
				.setMethod("PUT")
				.setUrl("http://localhost:8080/api/category/42")
				.setCurl("curl -X PUT 'http://localhost:8080/api/category/42'")
				.setRequestHeaders("{\"Content-Type\":[\"application/json\"]}")
				.setRequestBody("{\"name\":\"B\"}")
				.setStatus("200")
				.setResponseBody("{\"id\":42}")
				.setDurationMs(12)
				.build();
	}

	private JsonNode json(MvcResult result) throws Exception {
		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		return objectMapper.readTree(result.getResponse().getContentAsString());
	}
}
