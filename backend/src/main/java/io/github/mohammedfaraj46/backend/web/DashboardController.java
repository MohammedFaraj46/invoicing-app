package io.github.mohammedfaraj46.backend.web;

import io.github.mohammedfaraj46.backend.service.DashboardService;
import io.github.mohammedfaraj46.backend.web.dto.DashboardResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The overview figures for the signed-in user's own company.
 *
 * <p>Like {@code /api/companies/me}, there is no id in the path: which company
 * is being summarised follows from the login cookie, so there is nothing for a
 * caller to point at someone else's data.
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public DashboardResponse overview() {
        return dashboardService.overview();
    }
}
