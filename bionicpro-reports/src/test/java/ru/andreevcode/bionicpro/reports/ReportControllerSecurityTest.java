package ru.andreevcode.bionicpro.reports;

import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.andreevcode.bionicpro.reports.api.ReportController;
import ru.andreevcode.bionicpro.reports.security.SecurityConfig;
import ru.andreevcode.bionicpro.reports.service.ReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportController.class)
@Import(SecurityConfig.class)
public class ReportControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportService reportService;

    @Test
    void whenNoAuth_thenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/reports")
                        .param("user_id", "any")
                        .param("start_date", "2026-02-01")
                        .param("end_date", "2026-02-28"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void whenUserIsProtheticAndRequestsOwnData_thenReturns200() throws Exception {
        mockMvc.perform(get("/api/v1/reports")
                        .param("user_id", "alex.johnson")
                        .param("start_date", "2026-02-01")
                        .param("end_date", "2026-02-28")
                        .with(jwt()
                                .jwt(j -> j.claim("preferred_username", "alex.johnson"))
                                .authorities(new SimpleGrantedAuthority("ROLE_prothetic_user"))))
                .andExpect(status().isOk());
    }

    @Test
    void whenUserHasWrongRole_thenReturns403() throws Exception {
        // Симулируем Сару Коннор: она аутентифицирована, но у нее роль 'user', а не 'prothetic_user'
        mockMvc.perform(get("/api/v1/reports")
                        .param("user_id", "sarah.connor")
                        .param("start_date", "2026-02-01")
                        .param("end_date", "2026-02-28")
                        .with(jwt()
                                .jwt(j -> j.claim("preferred_username", "sarah.connor"))
                                .authorities(new SimpleGrantedAuthority("ROLE_user"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void whenUserAttemptsToViewOtherReport_thenReturns403() throws Exception {
        // Джон Доу пытается посмотреть отчет Алекса Джонсона
        mockMvc.perform(get("/api/v1/reports")
                        .param("user_id", "alex.johnson")
                        .param("start_date", "2026-02-01")
                        .param("end_date", "2026-02-28")
                        .with(jwt()
                                .jwt(j -> j.claim("preferred_username", "john.doe"))
                                .authorities(new SimpleGrantedAuthority("ROLE_prothetic_user"))))
                .andExpect(status().isForbidden());
    }
}