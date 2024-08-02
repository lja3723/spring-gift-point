package gift.domain.service;

import com.google.common.base.CaseFormat;
import gift.domain.dto.request.OptionAddRequest;
import gift.domain.dto.request.OptionUpdateRequest;
import gift.domain.dto.request.ProductAddRequest;
import gift.domain.dto.request.ProductUpdateRequest;
import gift.domain.dto.response.OptionResponse;
import gift.domain.dto.response.ProductResponse;
import gift.domain.dto.response.ProductWithCategoryIdResponse;
import gift.domain.entity.Product;
import gift.domain.exception.ErrorCode;
import gift.domain.exception.ServerException;
import gift.domain.repository.ProductRepository;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryService categoryService;
    private final OptionService optionService;
    private final Set<String> fieldName;

    public ProductService(ProductRepository productRepository, CategoryService categoryService, OptionService optionService) {
        this.productRepository = productRepository;
        this.categoryService = categoryService;
        this.optionService = optionService;
        this.fieldName = new HashSet<>();
        Arrays.stream(Product.class.getDeclaredFields()).forEach(f -> fieldName.add(f.getName()));
    }

    @Transactional(readOnly = true)
    public Product getProductById(Long id) {
        //존재하지 않는 상품이면 예외 발생
        return productRepository.findById(id).orElseThrow(() -> new ServerException(ErrorCode.PRODUCT_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getAllProducts(String sortParams, Long categoryId) {
        String[] split = sortParams.split(",");
        String sortField = CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, split[0]);
        String sortType = split[1];
        return productRepository.findAllByCategory(
                categoryService.findById(categoryId),
                Sort.by(getSortDirection(sortType), assertProductFieldValid(sortField)))
            .stream()
            .map(ProductResponse::of)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<OptionResponse> getOptionsByProductId(Long id) {
        return getProductById(id).getOptions().stream()
            .map(OptionResponse::of)
            .toList();
    }

    @Transactional
    public ProductWithCategoryIdResponse addProduct(ProductAddRequest requestDto) {
        //이미 존재하는 상품 등록 시도시 예외 발생
        productRepository.findByContents(requestDto).ifPresent((p) -> {
            throw new ServerException(ErrorCode.PRODUCT_ALREADY_EXISTS);});

        //상품 옵션이 하나도 없는 경우 예외 발생
        if (requestDto.options().isEmpty()) {
            throw new ServerException(ErrorCode.PRODUCT_OPTIONS_EMPTY);
        }

        Product product = productRepository.save(requestDto.toEntity(categoryService));
        optionService.addOptions(product, requestDto.options());
        return ProductWithCategoryIdResponse.of(product);
    }

    @Transactional
    public OptionResponse addProductOption(Long productId, OptionAddRequest request) {
        Product product = getProductById(productId);
        return OptionResponse.of(optionService.addOption(product, request));
    }

    @Transactional
    public ProductResponse updateProductById(Long id, ProductUpdateRequest updateRequestDto) {
        //존재하지 않는 상품 업데이트 시도시 예외 발생
        Product product = productRepository.findById(id).orElseThrow(() -> new ServerException(ErrorCode.PRODUCT_NOT_FOUND));
        //TODO: 상품 업데이트시 기존 상품과 겹칠 경우 막는 로직 추가필요
        product.set(updateRequestDto, categoryService);
        //상품 업데이트
        return ProductResponse.of(product);
    }

    @Transactional
    public OptionResponse updateProductOptionById(Long productId, Long optionId, OptionUpdateRequest request) {
        Product product = getProductById(productId);
        return OptionResponse.of(optionService.updateOptionById(product, optionId, request));
    }

    @Transactional
    public void deleteProduct(Long id) {
        productRepository.delete(productRepository.findById(id)
            //존재하지 않는 상품 삭제 시도시 예외 발생
            .orElseThrow(() -> new ServerException(ErrorCode.PRODUCT_NOT_FOUND)));
    }

    @Transactional
    public void deleteProductOption(Long productId, Long optionId) {
        Product product = getProductById(productId);
        optionService.deleteOptionById(product, optionId);
    }

    private Sort.Direction getSortDirection(String direction) {
        return switch (direction) {
            case "asc" -> Direction.ASC;
            case "desc" -> Direction.DESC;
            default -> throw new ServerException(ErrorCode.SORT_TYPE_ILLEGAL);
        };
    }

    private String assertProductFieldValid(String field) {
        if (fieldName.contains(field)) {
            return field;
        }
        throw new ServerException(ErrorCode.FIELD_NAME_ILLEGAL);
    }
}
