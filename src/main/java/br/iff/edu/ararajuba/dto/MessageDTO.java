package br.iff.edu.ararajuba.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MessageDTO (String key, String value) {

}
