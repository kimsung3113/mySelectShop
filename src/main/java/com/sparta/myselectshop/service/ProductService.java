package com.sparta.myselectshop.service;

import com.sparta.myselectshop.dto.ProductMypriceRequestDto;
import com.sparta.myselectshop.dto.ProductRequestDto;
import com.sparta.myselectshop.dto.ProductResponseDto;
import com.sparta.myselectshop.entity.*;
import com.sparta.myselectshop.exception.ProductNotFoundException;
import com.sparta.myselectshop.naver.dto.ItemDto;
import com.sparta.myselectshop.repository.FolderRepository;
import com.sparta.myselectshop.repository.ProductFolderRepository;
import com.sparta.myselectshop.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final FolderRepository folderRepository;
    private final ProductFolderRepository productFolderRepository;
    private final MessageSource messageSource;

    public static final int MIN_MY_PRICE = 100;

    public ProductResponseDto createProduct(ProductRequestDto requestDto, User user) {
        Product product = productRepository.save(new Product(requestDto, user));
        return new ProductResponseDto(product);
    }

    @Transactional
    public ProductResponseDto updateProduct(Long id, ProductMypriceRequestDto requestDto) {
        int price = requestDto.getMyprice();

        if(price < MIN_MY_PRICE){
            throw new IllegalArgumentException(
                    messageSource.getMessage(
                            "below.min.my.price", // properties 파일의 key
                            new Integer[]{MIN_MY_PRICE}, // 배열로 만들어 메시지 안의 중괄호의 값이 된다.
                            "Wrong Price",              // 기본 메시지
                            Locale.getDefault()         // 언어 설정
                    )
            );
        }

        Product product = productRepository.findById(id).orElseThrow(() ->
                new ProductNotFoundException(
                        messageSource.getMessage(
                                "not.found.product",
                                null,
                                "Not Found Product",
                                Locale.getDefault()
                        )
                ));

        product.update(requestDto);

        return new ProductResponseDto(product);
    }

    // return 할때 생성자 안에서 지연로딩(Lazy)가 일어나고 그로인해 Transactional 환경이 필요한데
    // 조회하는 거라 성능을 높이기 위해 readOnly = true를 설정한다.
    @Transactional(readOnly = true)
    public Page<ProductResponseDto> getProducts(User user, int page, int size, String sortBy, boolean isAsc) {

        Sort.Direction direction = isAsc ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort = Sort.by(direction, sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        UserRoleEnum userRoleEnum = user.getRole();

        Page<Product> productList;

        if(userRoleEnum == UserRoleEnum.USER){
            productList = productRepository.findAllByUser(user, pageable);
        }else{
            productList = productRepository.findAll(pageable);
        }

        return productList.map(ProductResponseDto::new);
    }

    @Transactional
    public void updateBySearch(Long id, ItemDto itemDto) {

       Product product = productRepository.findById(id).orElseThrow(() ->
               new NullPointerException("해당 상품은 존재하지 않습니다.")
       );
       product.updateByItemDto(itemDto);

    }

    public void addFolder(Long productId, Long folderId, User user) {

        Product product = productRepository.findById(productId).orElseThrow(()->
                new NullPointerException("해당 상품은 존재하지 않습니다"));

        Folder folder = folderRepository.findById(folderId).orElseThrow(() ->
                new NullPointerException("해당 폴더가 존재하지 않습니다"));

        if(!product.getUser().getId().equals(user.getId())
        || !folder.getUser().getId().equals(user.getId())){
            throw new IllegalArgumentException("회원님의 관심상품이 아니거나, 회원님의 폴더가 아닙니다.");
        }

        Optional<ProductFolder> overlapFolder = productFolderRepository.findByProductAndFolder(product, folder);

        if(overlapFolder.isPresent()){
            throw new IllegalArgumentException("중복된 폴더입니다.");
        }

        productFolderRepository.save(new ProductFolder(product, folder));

    }

    @Transactional(readOnly = true)
    public Page<ProductResponseDto> getProductsInFolder(Long folderId, int page, int size, String sortBy, boolean isAsc, User user) {

        Sort.Direction direction = isAsc ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort = Sort.by(direction, sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        // 해당 폴더에 등록된 상품을 가져옵니다.
        Page<Product> products = productRepository.findAllByUserAndProductFolderList_FolderId(user, folderId, pageable);

        Page<ProductResponseDto> responseDtoList = products.map(ProductResponseDto::new);

        return responseDtoList;
    }
}
