package com.daaradji.budget.budget;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.daaradji.budget.budget.BudgetDtos.BudgetOverview;
import com.daaradji.budget.budget.BudgetDtos.BudgetRequest;
import com.daaradji.budget.common.Periods;
import com.daaradji.budget.config.CurrentUser;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/budgets")
public class BudgetController {

    private final BudgetService service;
    private final CurrentUser currentUser;

    public BudgetController(BudgetService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    public BudgetOverview overview(@RequestParam(required = false) String period) {
        return service.overview(currentUser.id(), Periods.parseOrCurrent(period));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void create(@Valid @RequestBody BudgetRequest req) {
        service.create(currentUser.id(), req);
    }

    @PostMapping("/copy-previous")
    public Map<String, Integer> copyPrevious(@RequestParam(required = false) String period) {
        return Map.of("copied", service.copyFromPrevious(currentUser.id(), Periods.parseOrCurrent(period)));
    }

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(@PathVariable Long id, @Valid @RequestBody BudgetRequest req) {
        service.update(currentUser.id(), id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(currentUser.id(), id);
    }
}
