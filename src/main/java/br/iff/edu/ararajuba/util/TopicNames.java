package br.iff.edu.ararajuba.util;

public final class TopicNames {

    private TopicNames(){}

    public static String physicalTopic(String topic, String routeGroup){
        if (routeGroup == null || routeGroup.isBlank()) return topic;

        return topic + "__" + routeGroup;
    }
}
