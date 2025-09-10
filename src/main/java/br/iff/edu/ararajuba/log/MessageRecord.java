package br.iff.edu.ararajuba.log;

public record MessageRecord(long offset, long ts, byte [] key, byte[] value) {
}
