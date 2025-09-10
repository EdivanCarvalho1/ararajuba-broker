package br.iff.edu.ararajuba.core;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class Dispatcher {
    private final Set<String> topicsWithNewData = ConcurrentHashMap.newKeySet();

    public void enqueue(String topic, long offset){
        topicsWithNewData.add(topic);
    }

    public boolean hasNewData(String topic){
        return topicsWithNewData.contains(topic);
    }

    public void clear(String topic){
        topicsWithNewData.remove(topic);
    }
}
