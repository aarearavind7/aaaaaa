package org.springframework.samples.petclinic.system;

import jakarta.servlet.DispatcherType;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfiguration {

	@Value("${PETCLINIC_ADMIN_USERNAME:${spring.security.user.name:admin}}")
	private String adminUsername;

	@Value("${PETCLINIC_ADMIN_PASSWORD:${spring.security.user.password:admin-password}}")
	private String adminPassword;

	@Value("${PETCLINIC_USER_USERNAME:pet-user}")
	private String userUsername;

	@Value("${PETCLINIC_USER_PASSWORD:user-password}")
	private String userPassword;

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	UserDetailsService users(PasswordEncoder encoder) {
		UserDetails admin = User.withUsername(this.adminUsername)
			.password(encoder.encode(this.adminPassword))
			.roles("ADMIN")
			.build();

		UserDetails user = User.withUsername(this.userUsername)
			.password(encoder.encode(this.userPassword))
			.roles("USER")
			.build();

		return new InMemoryUserDetailsManager(admin, user);
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.authorizeHttpRequests(
				authorize -> authorize.dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD)
					.permitAll()
					.requestMatchers("/login", "/resources/**", "/webjars/**", "/actuator/health",
							"/actuator/health/**", "/livez", "/readyz")
					.permitAll()
					.requestMatchers("/actuator/env")
					.hasRole("ADMIN")
					.anyRequest()
					.authenticated())
			.formLogin(form -> form.loginPage("/login").defaultSuccessUrl("/").permitAll())
			.httpBasic(Customizer.withDefaults())
			.logout(logout -> logout.logoutSuccessUrl("/login?logout"));
		return http.build();
	}

}
