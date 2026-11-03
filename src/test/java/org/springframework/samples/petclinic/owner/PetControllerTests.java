/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.samples.petclinic.owner;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.context.annotation.Import;
import org.springframework.samples.petclinic.system.SecurityConfiguration;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Test class for the {@link PetController}
 *
 * @author Colin But
 * @author Wick Dynex
 */
@WithMockUser
@Import(SecurityConfiguration.class)
@WebMvcTest(value = PetController.class,
		includeFilters = @ComponentScan.Filter(value = PetTypeFormatter.class, type = FilterType.ASSIGNABLE_TYPE))
@DisabledInNativeImage
@DisabledInAotMode
class PetControllerTests {

	private static final int TEST_OWNER_ID = 1;

	private static final int TEST_PET_ID = 1;

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private OwnerRepository owners;

	@MockitoBean
	private PetTypeRepository types;

	@BeforeEach
	void setup() {
		PetType cat = new PetType();
		cat.setId(3);
		cat.setName("hamster");
		given(this.types.findPetTypes()).willReturn(List.of(cat));

		Owner owner = new Owner();
		Pet pet = new Pet();
		Pet dog = new Pet();
		owner.addPet(pet);
		owner.addPet(dog);
		pet.setId(TEST_PET_ID);
		dog.setId(TEST_PET_ID + 1);
		pet.setName("petty");
		dog.setName("doggy");
		given(this.owners.findById(TEST_OWNER_ID)).willReturn(Optional.of(owner));
	}

	@Test
	void initCreationForm() throws Exception {
		mockMvc.perform(get("/owners/{ownerId}/pets/new", TEST_OWNER_ID))
			.andExpect(status().isOk())
			.andExpect(view().name("pets/createOrUpdatePetForm"))
			.andExpect(model().attributeExists("pet"))
			.andExpect(result -> {
				Owner owner = (Owner) result.getModelAndView().getModel().get("owner");
				Pet pet = (Pet) result.getModelAndView().getModel().get("pet");
				assertThat(owner.getPets()).contains(pet);
			});
	}

	@Test
	void missingOwnerReturnsNotFound() throws Exception {
		given(owners.findById(999)).willReturn(Optional.empty());
		mockMvc.perform(get("/owners/{ownerId}/pets/new", 999)).andExpect(status().isNotFound());
		mockMvc.perform(get("/owners/{ownerId}/pets/{petId}/edit", 999, TEST_PET_ID)).andExpect(status().isNotFound());
		mockMvc.perform(post("/owners/{ownerId}/pets/new", 999).with(csrf())).andExpect(status().isNotFound());
		mockMvc.perform(post("/owners/{ownerId}/pets/{petId}/edit", 999, TEST_PET_ID).with(csrf()))
			.andExpect(status().isNotFound());
		verify(owners, never()).saveAndFlush(any(Owner.class));
	}

	@Test
	void petOutsideOwnerReturnsNotFound() throws Exception {
		Owner otherOwner = new Owner();
		Pet otherPet = new Pet();
		otherPet.setId(999);
		otherOwner.addPet(otherPet);
		given(owners.findById(2)).willReturn(Optional.of(otherOwner));
		mockMvc.perform(get("/owners/{ownerId}/pets/{petId}/edit", 2, 999)).andExpect(status().isOk());
		mockMvc.perform(get("/owners/{ownerId}/pets/{petId}/edit", TEST_OWNER_ID, 999))
			.andExpect(status().isNotFound());
		mockMvc
			.perform(post("/owners/{ownerId}/pets/{petId}/edit", TEST_OWNER_ID, 999).with(csrf())
				.param("name", "Betty")
				.param("type", "hamster")
				.param("birthDate", "2015-02-12"))
			.andExpect(status().isNotFound());
		verify(owners, never()).saveAndFlush(any(Owner.class));
	}

	@Test
	void updateFormReusesOwnerPet() throws Exception {
		mockMvc.perform(get("/owners/{ownerId}/pets/{petId}/edit", TEST_OWNER_ID, TEST_PET_ID))
			.andExpect(status().isOk())
			.andExpect(result -> {
				Owner owner = (Owner) result.getModelAndView().getModel().get("owner");
				assertThat(result.getModelAndView().getModel().get("pet")).isSameAs(owner.getPet(TEST_PET_ID));
			});
		verify(owners, times(1)).findById(TEST_OWNER_ID);
	}

	@Test
	void processCreationFormSuccess() throws Exception {
		mockMvc
			.perform(post("/owners/{ownerId}/pets/new", TEST_OWNER_ID).with(csrf())
				.param("name", "Betty")
				.param("type", "hamster")
				.param("birthDate", "2015-02-12"))
			.andExpect(status().is3xxRedirection())
			.andExpect(view().name("redirect:/owners/{ownerId}"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "/pets/new", "/pets/1/edit" })
	void nestedDuplicatePetNameViolation(String path) throws Exception {
		given(owners.saveAndFlush(any(Owner.class))).willThrow(new DataIntegrityViolationException(null,
				new IllegalStateException("Constraint UNIQUE_OWNER_PET_NAME violated")));
		mockMvc
			.perform(post("/owners/{ownerId}" + path, TEST_OWNER_ID).with(csrf())
				.param("name", "Betty")
				.param("type", "hamster")
				.param("birthDate", "2015-02-12"))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasFieldErrorCode("pet", "name", "duplicate"))
			.andExpect(view().name("pets/createOrUpdatePetForm"));
	}

	@ParameterizedTest
	@CsvSource(value = { "/pets/new,NULL", "/pets/new,Other constraint", "/pets/1/edit,NULL",
			"/pets/1/edit,Other constraint" }, nullValues = "NULL")
	void unrelatedIntegrityViolationsAreRethrown(String path, String message) {
		DataIntegrityViolationException violation = new DataIntegrityViolationException(message);
		given(owners.saveAndFlush(any(Owner.class))).willThrow(violation);
		assertThatThrownBy(() -> mockMvc.perform(post("/owners/{ownerId}" + path, TEST_OWNER_ID).with(csrf())
			.param("name", "Betty")
			.param("type", "hamster")
			.param("birthDate", "2015-02-12"))).hasCause(violation);
	}

	@Nested
	class ProcessCreationFormHasErrors {

		@Test
		void processCreationFormWithBlankName() throws Exception {
			mockMvc
				.perform(post("/owners/{ownerId}/pets/new", TEST_OWNER_ID).with(csrf())
					.param("name", "\t \n")
					.param("birthDate", "2015-02-12"))
				.andExpect(model().attributeHasNoErrors("owner"))
				.andExpect(model().attributeHasErrors("pet"))
				.andExpect(model().attributeHasFieldErrors("pet", "name"))
				.andExpect(model().attributeHasFieldErrorCode("pet", "name", "required"))
				.andExpect(status().isOk())
				.andExpect(view().name("pets/createOrUpdatePetForm"));
		}

		@Test
		void processCreationFormWithDuplicateName() throws Exception {
			mockMvc
				.perform(post("/owners/{ownerId}/pets/new", TEST_OWNER_ID).with(csrf())
					.param("name", "petty")
					.param("birthDate", "2015-02-12"))
				.andExpect(model().attributeHasNoErrors("owner"))
				.andExpect(model().attributeHasErrors("pet"))
				.andExpect(model().attributeHasFieldErrors("pet", "name"))
				.andExpect(model().attributeHasFieldErrorCode("pet", "name", "duplicate"))
				.andExpect(status().isOk())
				.andExpect(view().name("pets/createOrUpdatePetForm"));
		}

		@Test
		void processCreationFormWithMissingPetType() throws Exception {
			mockMvc
				.perform(post("/owners/{ownerId}/pets/new", TEST_OWNER_ID).with(csrf())
					.param("name", "Betty")
					.param("birthDate", "2015-02-12"))
				.andExpect(model().attributeHasNoErrors("owner"))
				.andExpect(model().attributeHasErrors("pet"))
				.andExpect(model().attributeHasFieldErrors("pet", "type"))
				.andExpect(model().attributeHasFieldErrorCode("pet", "type", "required"))
				.andExpect(status().isOk())
				.andExpect(view().name("pets/createOrUpdatePetForm"));
		}

		@Test
		void processCreationFormWithInvalidBirthDate() throws Exception {
			LocalDate currentDate = LocalDate.now();
			String futureBirthDate = currentDate.plusMonths(1).toString();

			mockMvc
				.perform(post("/owners/{ownerId}/pets/new", TEST_OWNER_ID).with(csrf())
					.param("name", "Betty")
					.param("birthDate", futureBirthDate))
				.andExpect(model().attributeHasNoErrors("owner"))
				.andExpect(model().attributeHasErrors("pet"))
				.andExpect(model().attributeHasFieldErrors("pet", "birthDate"))
				.andExpect(model().attributeHasFieldErrorCode("pet", "birthDate", "typeMismatch.birthDate"))
				.andExpect(status().isOk())
				.andExpect(view().name("pets/createOrUpdatePetForm"));
		}

		@Test
		void processCreationFormWithDataIntegrityViolation() throws Exception {
			given(owners.saveAndFlush(any(Owner.class)))
				.willThrow(new DataIntegrityViolationException("Duplicate key: unique_owner_pet_name"));
			mockMvc
				.perform(post("/owners/{ownerId}/pets/new", TEST_OWNER_ID).with(csrf())
					.param("name", "Betty")
					.param("type", "hamster")
					.param("birthDate", "2015-02-12"))
				.andExpect(model().attributeHasNoErrors("owner"))
				.andExpect(model().attributeHasErrors("pet"))
				.andExpect(model().attributeHasFieldErrors("pet", "name"))
				.andExpect(model().attributeHasFieldErrorCode("pet", "name", "duplicate"))
				.andExpect(status().isOk())
				.andExpect(view().name("pets/createOrUpdatePetForm"));
		}

		@Test
		void initUpdateForm() throws Exception {
			mockMvc.perform(get("/owners/{ownerId}/pets/{petId}/edit", TEST_OWNER_ID, TEST_PET_ID))
				.andExpect(status().isOk())
				.andExpect(model().attributeExists("pet"))
				.andExpect(view().name("pets/createOrUpdatePetForm"));
		}

	}

	@Test
	void processUpdateFormSuccess() throws Exception {
		mockMvc
			.perform(post("/owners/{ownerId}/pets/{petId}/edit", TEST_OWNER_ID, TEST_PET_ID).with(csrf())
				.param("name", "Betty")
				.param("type", "hamster")
				.param("birthDate", "2015-02-12"))
			.andExpect(status().is3xxRedirection())
			.andExpect(view().name("redirect:/owners/{ownerId}"));
	}

	@Test
	void processUpdateFormWithSameName() throws Exception {
		mockMvc
			.perform(post("/owners/{ownerId}/pets/{petId}/edit", TEST_OWNER_ID, TEST_PET_ID).with(csrf())
				.param("name", "petty") // same
				// name
				// as
				// existing
				// pet
				.param("type", "hamster")
				.param("birthDate", "2015-02-12"))
			.andExpect(status().is3xxRedirection())
			.andExpect(view().name("redirect:/owners/{ownerId}"));
	}

	@Nested
	class ProcessUpdateFormHasErrors {

		@Test
		void processUpdateFormWithDuplicateName() throws Exception {
			mockMvc
				.perform(post("/owners/{ownerId}/pets/{petId}/edit", TEST_OWNER_ID, TEST_PET_ID + 1).with(csrf())
					.param("name", "petty")
					.param("type", "hamster")
					.param("birthDate", "2015-02-12"))
				.andExpect(model().attributeHasNoErrors("owner"))
				.andExpect(model().attributeHasErrors("pet"))
				.andExpect(model().attributeHasFieldErrors("pet", "name"))
				.andExpect(model().attributeHasFieldErrorCode("pet", "name", "duplicate"))
				.andExpect(status().isOk())
				.andExpect(view().name("pets/createOrUpdatePetForm"));
		}

		@Test
		void processUpdateFormWithInvalidBirthDate() throws Exception {
			mockMvc
				.perform(post("/owners/{ownerId}/pets/{petId}/edit", TEST_OWNER_ID, TEST_PET_ID).with(csrf())
					.param("name", " ")
					.param("birthDate", "2015/02/12"))
				.andExpect(model().attributeHasNoErrors("owner"))
				.andExpect(model().attributeHasErrors("pet"))
				.andExpect(model().attributeHasFieldErrors("pet", "birthDate"))
				.andExpect(model().attributeHasFieldErrorCode("pet", "birthDate", "typeMismatch"))
				.andExpect(view().name("pets/createOrUpdatePetForm"));
		}

		@Test
		void processUpdateFormWithBlankName() throws Exception {
			mockMvc
				.perform(post("/owners/{ownerId}/pets/{petId}/edit", TEST_OWNER_ID, TEST_PET_ID).with(csrf())
					.param("name", "  ")
					.param("birthDate", "2015-02-12"))
				.andExpect(model().attributeHasNoErrors("owner"))
				.andExpect(model().attributeHasErrors("pet"))
				.andExpect(model().attributeHasFieldErrors("pet", "name"))
				.andExpect(model().attributeHasFieldErrorCode("pet", "name", "required"))
				.andExpect(view().name("pets/createOrUpdatePetForm"));
		}

		@Test
		void processUpdateFormWithDataIntegrityViolation() throws Exception {
			given(owners.saveAndFlush(any(Owner.class)))
				.willThrow(new DataIntegrityViolationException("Duplicate key: unique_owner_pet_name"));
			mockMvc
				.perform(post("/owners/{ownerId}/pets/{petId}/edit", TEST_OWNER_ID, TEST_PET_ID).with(csrf())
					.param("name", "Betty")
					.param("type", "hamster")
					.param("birthDate", "2015-02-12"))
				.andExpect(model().attributeHasNoErrors("owner"))
				.andExpect(model().attributeHasErrors("pet"))
				.andExpect(model().attributeHasFieldErrors("pet", "name"))
				.andExpect(model().attributeHasFieldErrorCode("pet", "name", "duplicate"))
				.andExpect(status().isOk())
				.andExpect(view().name("pets/createOrUpdatePetForm"));
		}

	}

}
