package com.attendance.adminweb.controller;

import com.attendance.adminweb.config.SecurityConfig;
import com.attendance.adminweb.model.CompanyLocationView;
import com.attendance.adminweb.model.DashboardSummary;
import com.attendance.adminweb.service.AdminService;
import com.attendance.adminweb.service.DatabaseUserDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest({AdminController.class, AuthController.class})
@Import(SecurityConfig.class)
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminService adminService;

    @MockBean
    private DatabaseUserDetailsService databaseUserDetailsService;

    @Test
    void loginPageShouldLoad() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"));
    }

    @Test
    void unauthenticatedUserShouldRedirectToLogin() throws Exception {
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @WithMockUser(username = "ADMIN001", roles = "ADMIN")
    void dashboardShouldRenderForAuthenticatedUser() throws Exception {
        given(adminService.getTodaySummary(anyString()))
                .willReturn(new DashboardSummary(2, 1, 0, 1, 0));
        given(adminService.normalizeDashboardFilter(anyString())).willReturn("ALL");
        given(adminService.getDashboardFilterLabel(anyString())).willReturn("전체 직원");
        given(adminService.getTodayAttendances(anyString(), anyString()))
                .willReturn(List.of());
        given(adminService.getCompanyLocation(anyString()))
                .willReturn(new CompanyLocationView("OpenAI Seoul Office", 37.5665, 126.9780, 100, "09:00", "", true));

        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"))
                .andExpect(model().attributeExists("summary"))
                .andExpect(model().attributeExists("recentAttendances"));
    }

    @Test
    @WithMockUser(username = "ADMIN001", roles = "ADMIN")
    void employeeTemplateShouldDownload() throws Exception {
        given(adminService.createEmployeeUploadTemplate()).willReturn(new byte[]{1, 2, 3});

        mockMvc.perform(get("/employees/template"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("employee-upload-template.xlsx")));
    }
}
