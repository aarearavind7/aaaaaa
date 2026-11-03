package org.springframework.samples.petclinic.system;

import jakarta.servlet.DispatcherType;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfiguration {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.authorizeHttpRequests(
				authorize -> authorize.dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD)
					.permitAll()
					.requestMatchers("/login", "/resources/**", "/webjars/**", "/actuator/health",
							"/actuator/health/**", "/livez", "/readyz")
					.permitAll()
					.anyRequest()
					.authenticated())
			.formLogin(form -> form.loginPage("/login").defaultSuccessUrl("/").permitAll())
			.httpBasic(Customizer.withDefaults())
			.logout(logout -> logout.logoutSuccessUrl("/login?logout"));
		return http.build();
	}

}
