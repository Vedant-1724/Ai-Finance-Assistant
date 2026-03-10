package com.financeassistant.financeassistant.service;

import com.financeassistant.financeassistant.dto.CategoryOptionDto;
import com.financeassistant.financeassistant.dto.CategorySuggestionDto;
import com.financeassistant.financeassistant.entity.Category;
import com.financeassistant.financeassistant.entity.Company;
import com.financeassistant.financeassistant.repository.CategoryRepository;
import com.financeassistant.financeassistant.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private static final List<SeedCategory> DEFAULT_CATEGORIES = List.of(
            new SeedCategory("Sales", Category.CategoryType.INCOME, BigDecimal.valueOf(18)),
            new SeedCategory("Consulting", Category.CategoryType.INCOME, BigDecimal.valueOf(18)),
            new SeedCategory("Interest Income", Category.CategoryType.INCOME, BigDecimal.ZERO),
            new SeedCategory("Other Income", Category.CategoryType.INCOME, BigDecimal.ZERO),
            new SeedCategory("Rent", Category.CategoryType.EXPENSE, BigDecimal.ZERO),
            new SeedCategory("Utilities", Category.CategoryType.EXPENSE, BigDecimal.valueOf(18)),
            new SeedCategory("Travel", Category.CategoryType.EXPENSE, BigDecimal.valueOf(5)),
            new SeedCategory("Food", Category.CategoryType.EXPENSE, BigDecimal.valueOf(5)),
            new SeedCategory("Salary", Category.CategoryType.EXPENSE, BigDecimal.ZERO),
            new SeedCategory("Software", Category.CategoryType.EXPENSE, BigDecimal.valueOf(18)),
            new SeedCategory("Marketing", Category.CategoryType.EXPENSE, BigDecimal.valueOf(18)),
            new SeedCategory("Office Supplies", Category.CategoryType.EXPENSE, BigDecimal.valueOf(18)),
            new SeedCategory("Tax", Category.CategoryType.EXPENSE, BigDecimal.ZERO),
            new SeedCategory("Other Expense", Category.CategoryType.EXPENSE, BigDecimal.ZERO));

    private final CategoryRepository categoryRepository;
    private final CompanyRepository companyRepository;
    private final RestTemplate restTemplate;

    @Value("${ai.service.url:http://localhost:5001}")
    private String aiServiceUrl;

    @Value("${ai.service.api.key:}")
    private String aiServiceApiKey;

    @Transactional
    public List<CategoryOptionDto> getCategories(Long companyId) {
        List<Category> categories = ensureCompanyCategories(companyId);
        List<CategoryOptionDto> result = new ArrayList<>(categories.size());
        for (Category category : categories) {
            result.add(new CategoryOptionDto(
                    category.getId(),
                    category.getName(),
                    category.getType().name()));
        }
        return result;
    }

    @Transactional
    public CategorySuggestionDto suggestCategory(Long companyId, String description, String requestedType) {
        Category.CategoryType type = parseType(requestedType);
        List<Category> categories = ensureCompanyCategories(companyId);

        if (description == null || description.isBlank()) {
            return fallback(categories, type, "");
        }

        CategorySuggestionDto aiSuggestion = suggestFromAi(categories, description, type);
        if (aiSuggestion != null) {
            return aiSuggestion;
        }

        return fallback(categories, type, description);
    }

    @Transactional
    protected List<Category> ensureCompanyCategories(Long companyId) {
        if (!categoryRepository.existsByCompany_Id(companyId)) {
            Company company = companyRepository.getReferenceById(companyId);
            List<Category> seeded = new ArrayList<>(DEFAULT_CATEGORIES.size());
            for (SeedCategory seed : DEFAULT_CATEGORIES) {
                Category category = new Category();
                category.setCompany(company);
                category.setName(seed.name());
                category.setType(seed.type());
                category.setDescription(seed.name());
                category.setDefault(true);
                category.setGstRate(seed.gstRate());
                seeded.add(category);
            }
            categoryRepository.saveAll(seeded);
        }
        return categoryRepository.findAvailableForCompany(companyId);
    }

    private CategorySuggestionDto suggestFromAi(List<Category> categories, String description,
            Category.CategoryType requestedType) {
        if (aiServiceApiKey == null || aiServiceApiKey.isBlank()) {
            return null;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-API-Key", aiServiceApiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(Map.of("description", description), headers);
            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    aiServiceUrl + "/categorize",
                    org.springframework.http.HttpMethod.POST,
                    entity,
                    (Class<Map<String, Object>>) (Class<?>) Map.class);

            Map<String, Object> body = response.getBody();
            if (body == null) {
                return null;
            }

            String predicted = Objects.toString(body.get("category"), "");
            double confidence = parseDouble(body.get("confidence"), 0.0d);
            String normalized = normalizeCategoryName(categories, predicted, requestedType);
            if (normalized == null || normalized.isBlank()) {
                return null;
            }
            return new CategorySuggestionDto(normalized, confidence, "AI");
        } catch (Exception e) {
            log.debug("AI categorization unavailable: {}", e.getMessage());
            return null;
        }
    }

    private CategorySuggestionDto fallback(List<Category> categories, Category.CategoryType requestedType, String description) {
        String normalized = description.toLowerCase(Locale.ROOT);
        String chosen;

        if (requestedType == Category.CategoryType.INCOME) {
            if (containsAny(normalized, "consult", "retainer", "service")) {
                chosen = "Consulting";
            } else if (containsAny(normalized, "interest", "fd", "dividend")) {
                chosen = "Interest Income";
            } else {
                chosen = containsAny(normalized, "sale", "invoice", "client", "revenue", "payment")
                        ? "Sales"
                        : "Other Income";
            }
        } else {
            if (containsAny(normalized, "rent", "lease")) {
                chosen = "Rent";
            } else if (containsAny(normalized, "uber", "ola", "flight", "hotel", "travel", "fuel")) {
                chosen = "Travel";
            } else if (containsAny(normalized, "lunch", "dinner", "zomato", "swiggy", "food")) {
                chosen = "Food";
            } else if (containsAny(normalized, "salary", "payroll", "stipend")) {
                chosen = "Salary";
            } else if (containsAny(normalized, "aws", "azure", "google cloud", "software", "saas", "subscription")) {
                chosen = "Software";
            } else if (containsAny(normalized, "ad", "marketing", "meta", "google ads")) {
                chosen = "Marketing";
            } else if (containsAny(normalized, "office", "stationery", "printer", "supplies")) {
                chosen = "Office Supplies";
            } else if (containsAny(normalized, "gst", "tds", "tax")) {
                chosen = "Tax";
            } else if (containsAny(normalized, "electricity", "internet", "phone", "utility", "water")) {
                chosen = "Utilities";
            } else {
                chosen = "Other Expense";
            }
        }

        String resolved = normalizeCategoryName(categories, chosen, requestedType);
        return new CategorySuggestionDto(
                resolved != null ? resolved : chosen,
                0.45d,
                "FALLBACK");
    }

    private String normalizeCategoryName(List<Category> categories, String predicted,
            Category.CategoryType requestedType) {
        if (predicted == null || predicted.isBlank()) {
            return null;
        }
        String normalized = predicted.trim().toLowerCase(Locale.ROOT);
        Optional<Category> exact = categories.stream()
                .filter(category -> category.getType() == requestedType)
                .filter(category -> category.getName().equalsIgnoreCase(normalized))
                .findFirst();
        if (exact.isPresent()) {
            return exact.get().getName();
        }

        Optional<Category> fuzzy = categories.stream()
                .filter(category -> category.getType() == requestedType)
                .filter(category -> normalized.contains(category.getName().toLowerCase(Locale.ROOT))
                        || category.getName().toLowerCase(Locale.ROOT).contains(normalized))
                .findFirst();
        return fuzzy.map(Category::getName).orElse(null);
    }

    private Category.CategoryType parseType(String requestedType) {
        if (requestedType != null && requestedType.equalsIgnoreCase("income")) {
            return Category.CategoryType.INCOME;
        }
        return Category.CategoryType.EXPENSE;
    }

    private boolean containsAny(String haystack, String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private double parseDouble(Object value, double fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private record SeedCategory(String name, Category.CategoryType type, BigDecimal gstRate) {
    }
}
