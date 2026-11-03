package org.springframework.samples.petclinic.system;

import java.security.Principal;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
class AuthenticationModelAdvice {

	@ModelAttribute("signedIn")
	boolean signedIn(Principal principal) {
		return principal != null;
	}

}
