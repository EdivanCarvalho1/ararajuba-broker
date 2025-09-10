package br.iff.edu.ararajuba.dto;

import java.util.List;

public record AckDTO(List<String> deliveryIds) {

    public List<String> ids() {return deliveryIds;}
}
