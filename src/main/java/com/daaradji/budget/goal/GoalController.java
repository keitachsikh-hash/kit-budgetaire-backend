package com.daaradji.budget.goal;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.daaradji.budget.config.CurrentUser;
import com.daaradji.budget.goal.GoalDtos.ContributionRequest;
import com.daaradji.budget.goal.GoalDtos.GoalRequest;
import com.daaradji.budget.goal.GoalDtos.GoalResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/goals")
public class GoalController {

    private final GoalService service;
    private final CurrentUser currentUser;

    public GoalController(GoalService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<GoalResponse> list() {
        return service.list(currentUser.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GoalResponse create(@Valid @RequestBody GoalRequest req) {
        return service.create(currentUser.id(), req);
    }

    @PutMapping("/{id}")
    public GoalResponse update(@PathVariable Long id, @Valid @RequestBody GoalRequest req) {
        return service.update(currentUser.id(), id, req);
    }

    @PostMapping("/{id}/contributions")
    public GoalResponse contribute(@PathVariable Long id, @Valid @RequestBody ContributionRequest req) {
        return service.contribute(currentUser.id(), id, req.amount());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(currentUser.id(), id);
    }
}
