package com.sigmav.sigmavbackend.modules.student.service;

import com.sigmav.sigmavbackend.modules.student.dto.DniLookupResponse;
import com.sigmav.sigmavbackend.modules.student.entity.Student;
import com.sigmav.sigmavbackend.modules.student.repository.StudentRepository;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DniLookupService {

    private final StudentRepository studentRepository;

    @Value("${decolecta.api-url}")
    private String apiUrl;

    @Value("${decolecta.token:}")
    private String token;

    public DniLookupResponse lookup(String dni) {
        // 1. Buscar en la tabla local (cualquier academia)
        List<Student> existing = studentRepository.findAnyByDni(dni);
        if (!existing.isEmpty()) {
            Student s = existing.get(0);
            return new DniLookupResponse(s.getFirstName(), s.getLastName(), "LOCAL");
        }

        // 2. Si no existe localmente, consultar la API de Decolecta
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("DECOLECTA_TOKEN no configurado.");
        }

        try {
            RestClient client = RestClient.builder()
                    .baseUrl(apiUrl)
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .build();

            DecolectaDniResponse resp = client.get()
                    .uri("/v1/reniec/dni?numero={dni}", dni)
                    .retrieve()
                    .body(DecolectaDniResponse.class);

            if (resp == null || resp.firstName() == null) {
                throw new IllegalArgumentException("DNI no encontrado: " + dni);
            }

            String firstName = capitalize(resp.firstName().trim());
            String lastName  = capitalize(
                    (resp.firstLastName() + " " + resp.secondLastName()).trim()
            );

            return new DniLookupResponse(firstName, lastName, "API");

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error consultando Decolecta para DNI {}: {}", dni, e.getMessage());
            throw new IllegalArgumentException("No se pudo obtener datos para el DNI " + dni + ". Verificá que sea correcto.");
        }
    }

    private String capitalize(String input) {
        if (input == null || input.isBlank()) return input;
        String[] words = input.toLowerCase().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isBlank()) {
                sb.append(Character.toUpperCase(word.charAt(0)))
                  .append(word.substring(1))
                  .append(" ");
            }
        }
        return sb.toString().trim();
    }

    private record DecolectaDniResponse(
            @JsonProperty("first_name")       String firstName,
            @JsonProperty("first_last_name")  String firstLastName,
            @JsonProperty("second_last_name") String secondLastName,
            @JsonProperty("document_number")  String documentNumber
    ) {}
}
