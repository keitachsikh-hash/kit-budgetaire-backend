package com.daaradji.budget.dashboard;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.daaradji.budget.common.Periods;
import com.daaradji.budget.config.CurrentUser;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService service;
    private final CurrentUser currentUser;

    public DashboardController(DashboardService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    public DashboardResponse get(@RequestParam(required = false) String period) {
        return service.build(currentUser.id(), Periods.parseOrCurrent(period));
    }
}
