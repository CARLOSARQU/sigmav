package com.sigmav.sigmavbackend.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sigmav.sigmavbackend.shared.exception.NotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP status mapping for GlobalExceptionHandler, exercised through real Spring MVC
 * dispatch so the test also proves which handler wins when several could match.
 *
 * A ResponseStatusException is a deliberate, client-facing outcome: the service chose
 * a status and a reason for the caller. Letting it fall into the catch-all tells the
 * client the server broke when it actually refused on purpose, and hides the reason.
 */
@DisplayName("GlobalExceptionHandler — status mapping")
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        logAppender = new ListAppender<>();
        logAppender.start();
        handlerLogger().addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        handlerLogger().detachAppender(logAppender);
    }

    private static Logger handlerLogger() {
        return (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
    }

    @Test
    @DisplayName("ResponseStatusException 403 → 403 with its own reason, not the catch-all 500")
    void responseStatusForbidden_mapsTo403WithReason() throws Exception {
        mockMvc.perform(get("/test/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message")
                        .value("Only a SUPER_ADMIN can assign the SUPER_ADMIN role"));
    }

    @Test
    @DisplayName("ResponseStatusException 404 → 404 with its own reason, not the catch-all 500")
    void responseStatusNotFound_mapsTo404WithReason() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Custom role not found: 7"));
    }

    @Test
    @DisplayName("ResponseStatusException 409 without reason → non-blank message and error")
    void responseStatusWithoutReason_producesNonBlankBody() throws Exception {
        mockMvc.perform(get("/test/conflict-no-reason"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    @DisplayName("ResponseStatusException with a non-standard code → 500 fallback with a usable body")
    void responseStatusNonStandardCode_fallsBackTo500WithBody() throws Exception {
        mockMvc.perform(get("/test/non-standard"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    @DisplayName("ResponseStatusException 5xx → one ERROR event carrying the exception, so the stack trace survives")
    void responseStatus5xx_logsErrorWithTheException() throws Exception {
        mockMvc.perform(get("/test/server-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("Error al subir la imagen"));

        List<ILoggingEvent> errors = logAppender.list.stream()
                .filter(event -> event.getLevel() == Level.ERROR)
                .toList();

        assertThat(errors)
                .as("a 5xx ResponseStatusException signals a server fault and must leave an ERROR trace")
                .hasSize(1);
        assertThat(errors.get(0).getThrowableProxy())
                .as("the ERROR event must carry the exception so the stack trace is retained")
                .isNotNull();
        assertThat(errors.get(0).getThrowableProxy().getClassName())
                .isEqualTo(ResponseStatusException.class.getName());
    }

    @Test
    @DisplayName("ResponseStatusException 4xx → no log event, it is a deliberate client-facing outcome")
    void responseStatus4xx_logsNothing() throws Exception {
        mockMvc.perform(get("/test/forbidden")).andExpect(status().isForbidden());
        mockMvc.perform(get("/test/not-found")).andExpect(status().isNotFound());
        mockMvc.perform(get("/test/conflict-no-reason")).andExpect(status().isConflict());

        assertThat(logAppender.list)
                .as("a 4xx ResponseStatusException is not a server fault and must stay unlogged")
                .isEmpty();
    }

    @Test
    @DisplayName("catch-all: a plain RuntimeException still returns the generic Spanish 500")
    void plainRuntimeException_stillReturnsGeneric500() throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message")
                        .value("Ha ocurrido un error inesperado en el servidor"));
    }

    @Test
    @DisplayName("AccessDeniedException still returns the existing generic Spanish 403 message")
    void accessDenied_stillReturnsGenericSpanishMessage() throws Exception {
        mockMvc.perform(get("/test/access-denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message")
                        .value("No tiene permisos para realizar esta acción"));
    }

    @Test
    @DisplayName("NotFoundException still returns its own 404 message")
    void notFound_stillReturnsItsOwnMessage() throws Exception {
        mockMvc.perform(get("/test/entity-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message")
                        .value("Estudiante no encontrado con ID: 3"));
    }

    /** Minimal controller whose only job is to raise one exception per endpoint. */
    @RestController
    static class ThrowingController {

        @GetMapping("/test/forbidden")
        String forbidden() {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only a SUPER_ADMIN can assign the SUPER_ADMIN role");
        }

        @GetMapping("/test/not-found")
        String notFound() {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Custom role not found: 7");
        }

        @GetMapping("/test/conflict-no-reason")
        String conflictNoReason() {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }

        @GetMapping("/test/non-standard")
        String nonStandard() {
            throw new ResponseStatusException(HttpStatusCode.valueOf(599), "Non-standard status");
        }

        @GetMapping("/test/server-error")
        String serverError() {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error al subir la imagen");
        }

        @GetMapping("/test/boom")
        String boom() {
            throw new RuntimeException("boom");
        }

        @GetMapping("/test/access-denied")
        String accessDenied() {
            throw new AccessDeniedException("raw internal detail that must not leak");
        }

        @GetMapping("/test/entity-not-found")
        String entityNotFound() {
            throw new NotFoundException("Estudiante no encontrado con ID: 3");
        }
    }
}
