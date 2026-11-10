package org.springframework.samples.petclinic.system;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.samples.petclinic.PetClinicApplication;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PetClinicApplication.class)
@AutoConfigureMockMvc
class SecurityIntegrationTests {

    @Autowired
    private MockMvc mvc;

    @Test
    void anonymousVisitorsMustSignIn() throws Exception {
        for (String path : new String[] { "/", "/owners/1", "/vets.html", "/v3/api-docs", "/actuator/env" }) {
            mvc.perform(get(path).accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        }
        mvc.perform(get("/vets").accept(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());
    }

    @Test
    void loginPageAndAssetsArePublic() throws Exception {
        mvc.perform(get("/login"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Sign in to PetClinic")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"_csrf\"")));
        mvc.perform(get("/resources/css/petclinic.css")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void bothUsersCanSignIn() throws Exception {
        mvc.perform(formLogin().user("admin").password("admin-password"))
            .andExpect(authenticated().withUsername("admin"))
            .andExpect(redirectedUrl("/"));
        mvc.perform(formLogin().user("pet-user").password("user-password"))
            .andExpect(authenticated().withUsername("pet-user"))
            .andExpect(redirectedUrl("/"));
    }

    @Test
    void incorrectPasswordIsRejected() throws Exception {
        mvc.perform(formLogin().user("pet-user").password("incorrect"))
            .andExpect(redirectedUrl("/login?error"))
            .andExpect(unauthenticated());
        mvc.perform(get("/login?error"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Invalid username or password.")));
    }

    @Test
    void signInAndSignOutProtectTheSession() throws Exception {
        MockHttpSession session = (MockHttpSession) mvc
            .perform(formLogin().user("pet-user").password("user-password"))
            .andExpect(authenticated().withUsername("pet-user"))
            .andExpect(redirectedUrl("/"))
            .andReturn()
            .getRequest()
            .getSession(false);
        assertThat(session).isNotNull();
        mvc.perform(get("/").session(session))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Sign out")));
        mvc.perform(post("/logout").with(csrf()).session(session).accept(MediaType.TEXT_HTML))
            .andExpect(redirectedUrl("/login?logout"))
            .andExpect(unauthenticated());
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/").accept(MediaType.TEXT_HTML)).andExpect(redirectedUrl("/login"));
    }

    @Test
    void adminOnlyEndpointRequiresAdminRole() throws Exception {
        mvc.perform(get("/actuator/env").with(user("pet-user").roles("USER")))
            .andExpect(status().isForbidden());
        mvc.perform(get("/actuator/env").with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
    }

    @Test
    void writesAndLogoutRequireCsrfTokens() throws Exception {
        mvc.perform(post("/owners/new").with(user("pet-user"))).andExpect(status().isForbidden());
        mvc.perform(post("/logout").with(user("pet-user"))).andExpect(status().isForbidden());
        mvc.perform(post("/login").param("username", "pet-user").param("password", "user-password"))
            .andExpect(status().isForbidden());
    }

}
