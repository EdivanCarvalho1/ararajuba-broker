package br.iff.edu.ararajuba.api;

import br.iff.edu.ararajuba.dto.AckDTO;
import br.iff.edu.ararajuba.dto.MessageView;
import br.iff.edu.ararajuba.service.BrokerService;
import br.iff.edu.ararajuba.util.TopicNames;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/topics/{topic}")
@Produces(MediaType.APPLICATION_JSON)
public class PollResource {

    @Inject
    BrokerService brokerService;

    @GET
    @Path("/poll")
    public Response poll(@PathParam("topic") String topic,
                         @QueryParam("routeGroup") String routeGroup,
                         @QueryParam("consumerGroup") String consumerGroup,
                         @QueryParam("max") @DefaultValue("50") int max,
                         @QueryParam("timeoutMs") @DefaultValue("10000") long timeoutMs) throws Exception {

        String physical = TopicNames.physicalTopic(topic, routeGroup);
        List<MessageView> out = brokerService.poll(physical, consumerGroup, max, timeoutMs);
        return Response.ok(out).build();
    }

    @POST
    @Path("/ack")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response ack(@PathParam("topic") String topic,
                        @QueryParam("routeGroup") String routeGroup,
                        @QueryParam("consumerGroup") String consumerGroup,
                        AckDTO ackDTO){
        String physical = TopicNames.physicalTopic(topic, routeGroup);
        brokerService.ack(physical, consumerGroup, ackDTO);
        return Response.noContent().build();
    }

    @GET
    @Path("/list")
    public Response listTopics() throws Exception {
        return Response.ok(brokerService.listTopics()).build();
    }
}
