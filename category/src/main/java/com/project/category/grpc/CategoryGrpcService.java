package com.project.category.grpc;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.project.category.dto.CategoryDtos;
import com.project.category.entity.Category;
import com.project.common.exception.AppException;
import com.project.common.exception.ErrorCode;
import com.project.category.service.CategoryService;
import com.project.grpc.category.v1.BatchGetCategoriesRequest;
import com.project.grpc.category.v1.BatchGetCategoriesResponse;
import com.project.grpc.category.v1.CategoryServiceGrpc;
import com.project.grpc.category.v1.CreateCategoryRequest;
import com.project.grpc.category.v1.GetCategoryRequest;
import com.project.grpc.category.v1.ListCategoriesRequest;
import com.project.grpc.category.v1.ListCategoriesResponse;
import com.project.grpc.category.v1.UpdateCategoryRequest;

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;

/**
 * gRPC adapter cho service khác gọi vào. Chỉ chuyển đổi proto <-> DTO, logic nằm ở {@link CategoryService}.
 * Lỗi ném ra được GrpcExceptionAdvice (module common-grpc) chuyển thành gRPC Status.
 */
@GrpcService
@RequiredArgsConstructor
public class CategoryGrpcService extends CategoryServiceGrpc.CategoryServiceImplBase {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final CategoryService categoryService;

    @Override
    public void getCategory(GetCategoryRequest request,
            StreamObserver<com.project.grpc.category.v1.Category> responseObserver) {
        Category category = switch (request.getKeyCase()) {
            case ID -> categoryService.getById(request.getId());
            case CODE -> categoryService.getByCode(request.getCode());
            case KEY_NOT_SET -> throw new AppException(ErrorCode.INVALID_ARGUMENT, "Phải truyền id hoặc code");
        };
        reply(responseObserver, CategoryProtoMapper.toProto(category));
    }

    @Override
    public void batchGetCategories(BatchGetCategoriesRequest request,
            StreamObserver<BatchGetCategoriesResponse> responseObserver) {
        Set<String> codes = new LinkedHashSet<>(request.getCodesList());
        List<Category> found = categoryService.findByCodes(codes);
        Set<String> foundCodes = found.stream().map(Category::getCode).collect(Collectors.toSet());

        BatchGetCategoriesResponse.Builder builder = BatchGetCategoriesResponse.newBuilder();
        found.forEach(c -> builder.addCategories(CategoryProtoMapper.toProto(c)));
        codes.stream().filter(code -> !foundCodes.contains(code)).forEach(builder::addMissingCodes);
        reply(responseObserver, builder.build());
    }

    @Override
    public void listCategories(ListCategoriesRequest request,
            StreamObserver<ListCategoriesResponse> responseObserver) {
        int size = request.getPage().getSize() <= 0 ? DEFAULT_PAGE_SIZE
                : Math.min(request.getPage().getSize(), MAX_PAGE_SIZE);
        PageRequest pageable = PageRequest.of(Math.max(request.getPage().getPage(), 0), size, Sort.by("id"));
        String parentCode = request.hasParentCode() ? request.getParentCode() : null;

        Page<Category> page = categoryService.list(parentCode, pageable);
        ListCategoriesResponse.Builder builder = ListCategoriesResponse.newBuilder()
                .setPageInfo(CategoryProtoMapper.toPageInfo(page));
        page.forEach(c -> builder.addCategories(CategoryProtoMapper.toProto(c)));
        reply(responseObserver, builder.build());
    }

    @Override
    public void createCategory(CreateCategoryRequest request,
            StreamObserver<com.project.grpc.category.v1.Category> responseObserver) {
        Category created = categoryService.create(new CategoryDtos.CreateCategoryRequest(
                request.getCode(),
                request.getName(),
                request.hasDescription() ? request.getDescription() : null,
                request.hasParentCode() ? request.getParentCode() : null));
        reply(responseObserver, CategoryProtoMapper.toProto(created));
    }

    @Override
    public void updateCategory(UpdateCategoryRequest request,
            StreamObserver<com.project.grpc.category.v1.Category> responseObserver) {
        Category updated = categoryService.update(request.getId(), new CategoryDtos.UpdateCategoryRequest(
                request.getName(),
                request.hasDescription() ? request.getDescription() : null,
                request.hasParentCode() ? request.getParentCode() : null,
                request.getActive(),
                request.getVersion()));
        reply(responseObserver, CategoryProtoMapper.toProto(updated));
    }

    private static <T> void reply(StreamObserver<T> observer, T value) {
        observer.onNext(value);
        observer.onCompleted();
    }
}
