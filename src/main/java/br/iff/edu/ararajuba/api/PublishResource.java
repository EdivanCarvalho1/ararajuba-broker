package br.iff.edu.ararajuba.api;

import br.iff.edu.ararajuba.dto.MessageDTO;
import br.iff.edu.ararajuba.service.BrokerService;
import br.iff.edu.ararajuba.util.TopicNames;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;


import java.util.Map;

@Path("/topics/{topic}/publish")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class PublishResource {

    @Inject
    BrokerService brokerService;

    @POST
    public Response publish(@PathParam("topic") String topic,
                            @QueryParam("group") String routeGroup,
                            MessageDTO messageDTO) throws Exception {

        String physical = TopicNames.physicalTopic(topic, routeGroup);
        long off = brokerService.publish(physical, messageDTO);
        return Response.accepted().entity(Map.of("offset", off, "topic", physical)).build();
    }
}
