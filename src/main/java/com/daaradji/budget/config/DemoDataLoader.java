package com.daaradji.budget.config;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.daaradji.budget.account.Account;
import com.daaradji.budget.account.AccountRepository;
import com.daaradji.budget.account.AccountType;
import com.daaradji.budget.auth.AuthService;
import com.daaradji.budget.budget.MonthlyBudget;
import com.daaradji.budget.budget.MonthlyBudgetRepository;
import com.daaradji.budget.category.Category;
import com.daaradji.budget.category.CategoryRepository;
import com.daaradji.budget.category.CategoryType;
import com.daaradji.budget.goal.SavingGoal;
import com.daaradji.budget.goal.SavingGoalRepository;
import com.daaradji.budget.transaction.BudgetTransaction;
import com.daaradji.budget.transaction.TransactionRepository;
import com.daaradji.budget.user.User;
import com.daaradji.budget.user.UserRepository;

/** Crée un compte de démonstration (demo@kit.sn / demo1234) avec 6 mois d'historique. */
@Component
public class DemoDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);
    private static final String DEMO_EMAIL = "demo@kit.sn";

    private final AppProperties props;
    private final UserRepository users;
    private final AuthService authService;
    private final AccountRepository accounts;
    private final CategoryRepository categories;
    private final TransactionRepository transactions;
    private final MonthlyBudgetRepository budgets;
    private final SavingGoalRepository goals;

    public DemoDataLoader(AppProperties props, UserRepository users, AuthService authService,
            AccountRepository accounts, CategoryRepository categories, TransactionRepository transactions,
            MonthlyBudgetRepository budgets, SavingGoalRepository goals) {
        this.props = props;
        this.users = users;
        this.authService = authService;
        this.accounts = accounts;
        this.categories = categories;
        this.transactions = transactions;
        this.budgets = budgets;
        this.goals = goals;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!props.demoData() || users.existsByEmailIgnoreCase(DEMO_EMAIL)) {
            return;
        }
        User user = authService.createUser("Awa Diop", DEMO_EMAIL, "demo1234");
        Long uid = user.getId();

        Account cash = accounts.findByUserIdAndArchivedFalse(uid).getFirst();
        cash.setInitialBalance(new BigDecimal("25000"));
        Account wave = account(uid, "Wave", AccountType.WAVE, "#1dc8ff", "50000");
        Account om = account(uid, "Orange Money", AccountType.ORANGE_MONEY, "#ff7900", "15000");
        Account bank = account(uid, "Compte CBAO", AccountType.BANQUE, "#0d6efd", "350000");

        Map<String, Category> cat = categories.findByUserIdOrderByTypeAscNameAsc(uid).stream()
                .collect(Collectors.toMap(Category::getName, Function.identity()));

        Random rnd = new Random(42);
        YearMonth current = YearMonth.now();
        for (int m = 5; m >= 0; m--) {
            YearMonth ym = current.minusMonths(m);
            int lastDay = ym.equals(current) ? LocalDate.now().getDayOfMonth() : ym.lengthOfMonth();
            tx(uid, bank, cat.get("Salaire"), CategoryType.INCOME, 450000, ym.atDay(1), "Salaire " + ym);
            tx(uid, wave, cat.get("Freelance"), CategoryType.INCOME, 80000 + rnd.nextInt(5) * 10000,
                    ym.atDay(Math.min(6, lastDay)), "Mission site web");
            tx(uid, cash, cat.get("Business"), CategoryType.INCOME, 60000 + rnd.nextInt(4) * 5000,
                    ym.atDay(Math.min(4, lastDay)), "Vente boutique");
            tx(uid, om, cat.get("Transferts reçus"), CategoryType.INCOME, 30000, ym.atDay(Math.min(2, lastDay)),
                    "Envoi de la famille");
            tx(uid, bank, cat.get("Logement"), CategoryType.EXPENSE, 150000, ym.atDay(Math.min(3, lastDay)), "Loyer");
            tx(uid, bank, cat.get("Eau & Électricité"), CategoryType.EXPENSE, 18000 + rnd.nextInt(8) * 1000,
                    ym.atDay(Math.min(8, lastDay)), "Senelec + SEN'EAU");
            tx(uid, om, cat.get("Communication"), CategoryType.EXPENSE, 10000, ym.atDay(Math.min(5, lastDay)),
                    "Pass internet");
            tx(uid, bank, cat.get("Tontine"), CategoryType.EXPENSE, 25000, ym.atDay(Math.min(10, lastDay)),
                    "Tontine du quartier");
            for (int d = 2; d <= lastDay; d += 3) {
                tx(uid, d % 2 == 0 ? cash : wave, cat.get("Alimentation"), CategoryType.EXPENSE,
                        3000 + rnd.nextInt(6) * 1000, ym.atDay(d), "Marché / courses");
                tx(uid, cash, cat.get("Transport"), CategoryType.EXPENSE, 1000 + rnd.nextInt(4) * 500, ym.atDay(d),
                        rnd.nextBoolean() ? "Taxi" : "Bus Dakar Dem Dikk");
            }
            if (lastDay >= 20) {
                tx(uid, wave, cat.get("Loisirs"), CategoryType.EXPENSE, 8000 + rnd.nextInt(4) * 2000,
                        ym.atDay(20), "Sortie week-end");
                tx(uid, bank, cat.get("Shopping"), CategoryType.EXPENSE, 15000 + rnd.nextInt(6) * 5000,
                        ym.atDay(18), "Vêtements");
            }
            if (rnd.nextInt(3) == 0) {
                tx(uid, cash, cat.get("Cérémonies & Dons"), CategoryType.EXPENSE, 10000 + rnd.nextInt(3) * 5000,
                        ym.atDay(Math.min(12, lastDay)), "Baptême");
            }
            if (rnd.nextInt(3) == 0) {
                tx(uid, om, cat.get("Santé"), CategoryType.EXPENSE, 7500, ym.atDay(Math.min(14, lastDay)),
                        "Pharmacie");
            }
        }

        for (YearMonth ym : List.of(current.minusMonths(1), current)) {
            budget(uid, cat.get("Alimentation"), ym, 60000);
            budget(uid, cat.get("Transport"), ym, 20000);
            budget(uid, cat.get("Loisirs"), ym, 15000);
            budget(uid, cat.get("Shopping"), ym, 30000);
            budget(uid, cat.get("Eau & Électricité"), ym, 25000);
        }

        goal(uid, "Fonds d'urgence", 500000, 180000, current.plusMonths(8).atEndOfMonth(), "shield-check", "#198754");
        goal(uid, "Tabaski", 250000, 90000, current.plusMonths(5).atDay(15), "stars", "#fd7e14");
        goal(uid, "Nouveau laptop", 600000, 120000, current.plusMonths(10).atEndOfMonth(), "laptop", "#6f42c1");

        log.info("Compte de démonstration créé : {} / demo1234", DEMO_EMAIL);
    }

    private Account account(Long uid, String name, AccountType type, String color, String initial) {
        Account a = new Account();
        a.setUserId(uid);
        a.setName(name);
        a.setType(type);
        a.setColor(color);
        a.setInitialBalance(new BigDecimal(initial));
        return accounts.save(a);
    }

    private void tx(Long uid, Account account, Category category, CategoryType type, int amount, LocalDate date,
            String description) {
        BudgetTransaction t = new BudgetTransaction();
        t.setUserId(uid);
        t.setAccount(account);
        t.setCategory(category);
        t.setType(type);
        t.setAmount(BigDecimal.valueOf(amount));
        t.setDate(date);
        t.setDescription(description);
        transactions.save(t);
    }

    private void budget(Long uid, Category category, YearMonth ym, int limit) {
        MonthlyBudget b = new MonthlyBudget();
        b.setUserId(uid);
        b.setCategory(category);
        b.setPeriod(ym.toString());
        b.setLimitAmount(BigDecimal.valueOf(limit));
        budgets.save(b);
    }

    private void goal(Long uid, String name, int target, int saved, LocalDate deadline, String icon, String color) {
        SavingGoal g = new SavingGoal();
        g.setUserId(uid);
        g.setName(name);
        g.setTargetAmount(BigDecimal.valueOf(target));
        g.setSavedAmount(BigDecimal.valueOf(saved));
        g.setDeadline(deadline);
        g.setIcon(icon);
        g.setColor(color);
        goals.save(g);
    }
}
