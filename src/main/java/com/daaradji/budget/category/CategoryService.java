package com.daaradji.budget.category;

import static com.daaradji.budget.category.CategoryType.EXPENSE;
import static com.daaradji.budget.category.CategoryType.INCOME;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.daaradji.budget.category.CategoryDtos.CategoryRequest;
import com.daaradji.budget.category.CategoryDtos.CategoryResponse;
import com.daaradji.budget.common.BusinessException;
import com.daaradji.budget.common.NotFoundException;
import com.daaradji.budget.transaction.TransactionRepository;

@Service
public class CategoryService {

    private final CategoryRepository categories;
    private final TransactionRepository transactions;

    public CategoryService(CategoryRepository categories, TransactionRepository transactions) {
        this.categories = categories;
        this.transactions = transactions;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list(Long userId, CategoryType type) {
        List<Category> result = type == null
                ? categories.findByUserIdOrderByTypeAscNameAsc(userId)
                : categories.findByUserIdAndTypeOrderByNameAsc(userId, type);
        return result.stream().map(CategoryResponse::of).toList();
    }

    @Transactional
    public CategoryResponse create(Long userId, CategoryRequest req) {
        Category c = new Category(userId, req.name().trim(), req.type(),
                orDefault(req.icon(), "tag"), orDefault(req.color(), "#6c757d"));
        return CategoryResponse.of(categories.save(c));
    }

    @Transactional
    public CategoryResponse update(Long userId, Long id, CategoryRequest req) {
        Category c = get(userId, id);
        if (c.getType() != req.type() && transactions.existsByCategoryId(id)) {
            throw new BusinessException("Impossible de changer le type d'une catégorie déjà utilisée");
        }
        c.setName(req.name().trim());
        c.setType(req.type());
        c.setIcon(orDefault(req.icon(), c.getIcon()));
        c.setColor(orDefault(req.color(), c.getColor()));
        return CategoryResponse.of(c);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        Category c = get(userId, id);
        if (transactions.existsByCategoryId(id)) {
            throw new BusinessException("Cette catégorie contient des opérations : supprimez-les ou réaffectez-les d'abord");
        }
        categories.delete(c);
    }

    public Category get(Long userId, Long id) {
        return categories.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Catégorie introuvable"));
    }

    @Transactional
    public void createDefaults(Long userId) {
        categories.saveAll(List.of(
                new Category(userId, "Alimentation", EXPENSE, "basket", "#fd7e14"),
                new Category(userId, "Transport", EXPENSE, "bus-front", "#0dcaf0"),
                new Category(userId, "Logement", EXPENSE, "house", "#6f42c1"),
                new Category(userId, "Eau & Électricité", EXPENSE, "lightning-charge", "#ffc107"),
                new Category(userId, "Communication", EXPENSE, "phone", "#20c997"),
                new Category(userId, "Santé", EXPENSE, "heart-pulse", "#dc3545"),
                new Category(userId, "Éducation", EXPENSE, "mortarboard", "#0d6efd"),
                new Category(userId, "Tontine", EXPENSE, "people", "#198754"),
                new Category(userId, "Crédit", EXPENSE, "sim", "#ff7900"),
                new Category(userId, "Cérémonies", EXPENSE, "balloon-heart", "#d63384"),
                new Category(userId, "Dons", EXPENSE, "gift", "#e35d6a"),
                new Category(userId, "Loisirs", EXPENSE, "controller", "#6610f2"),
                new Category(userId, "Shopping", EXPENSE, "bag", "#e35d6a"),
                new Category(userId, "Autres dépenses", EXPENSE, "three-dots", "#6c757d"),
                new Category(userId, "Salaire", INCOME, "briefcase", "#198754"),
                new Category(userId, "Business", INCOME, "shop", "#0d6efd"),
                new Category(userId, "Freelance", INCOME, "laptop", "#6f42c1"),
                new Category(userId, "Transferts reçus", INCOME, "arrow-down-circle", "#20c997"),
                new Category(userId, "Autres revenus", INCOME, "plus-circle", "#6c757d")));
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
