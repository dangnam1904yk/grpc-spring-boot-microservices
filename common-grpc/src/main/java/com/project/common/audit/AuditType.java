package com.project.common.audit;

public enum AuditType {
    /** Request REST vào service: IP, curl, request, response. */
    HTTP,
    /** gRPC call service khác gọi vào. */
    GRPC,
    /** Một bản ghi DB bị thêm / sửa / xoá (đã commit), kèm giá trị cũ -> mới của từng field. */
    ENTITY_CHANGE
}
