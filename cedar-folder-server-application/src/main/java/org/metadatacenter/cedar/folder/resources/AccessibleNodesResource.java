package org.metadatacenter.cedar.folder.resources;

import com.codahale.metrics.annotation.Timed;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.server.PermissionServiceSession;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.IdentityHashMap;
import java.util.Map;

import static org.metadatacenter.rest.assertion.GenericAssertions.LoggedIn;

@Path("/accessible-node-ids")
@Produces(MediaType.APPLICATION_JSON)
public class AccessibleNodesResource extends AbstractFolderServerResource {

  public AccessibleNodesResource(CedarConfig cedarConfig) {
    super(cedarConfig);
  }

  @GET
  @Timed
  public Response accessibleNodeIds() throws CedarException {
    CedarRequestContext c = CedarRequestContextFactory.fromRequest(request);

    c.must(c.user()).be(LoggedIn);

    PermissionServiceSession permissionSession = CedarDataServices.getPermissionServiceSession(c);

    Map<String, String> ids = permissionSession.findAccessibleNodeIds();

    Map<String, Object> r = new IdentityHashMap<>();
    r.put("accessibleNodes", ids);

    return Response.ok().entity(r).build();
  }
}
