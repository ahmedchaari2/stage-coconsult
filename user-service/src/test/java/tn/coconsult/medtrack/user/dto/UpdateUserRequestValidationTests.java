package tn.coconsult.medtrack.user.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UpdateUserRequestValidationTests {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsAdministrativePasswordShorterThanEightCharacters() {
        UpdateUserRequest request = new UpdateUserRequest();
        request.setPassword("1234567");

        assertEquals(1, validator.validate(request).size());
    }

    @Test
    void acceptsAdministrativePasswordWithEightCharacters() {
        UpdateUserRequest request = new UpdateUserRequest();
        request.setPassword("12345678");

        assertEquals(0, validator.validate(request).size());
    }
}
