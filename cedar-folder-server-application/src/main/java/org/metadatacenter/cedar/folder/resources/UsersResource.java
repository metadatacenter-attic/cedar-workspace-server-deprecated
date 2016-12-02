package org.metadatacenter.cedar.folder.resources;

import com.codahale.metrics.annotation.Timed;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.model.folderserver.FolderServerUser;
import org.metadatacenter.model.response.FolderServerUserListResponse;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.rest.exception.CedarAssertionException;
import org.metadatacenter.server.UserServiceSession;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.util.List;

import static org.metadatacenter.rest.assertion.GenericAssertions.LoggedIn;

@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
public class UsersResource {

  private
  @Context
  UriInfo uriInfo;

  private
  @Context
  HttpServletRequest request;

  public UsersResource() {
  }

  @GET
  @Timed
  public Response findUsers() throws CedarAssertionException {
    CedarRequestContext c = CedarRequestContextFactory.fromRequest(request);

    c.must(c.user()).be(LoggedIn);

    UserServiceSession userSession = CedarDataServices.getUserServiceSession(c);

    List<FolderServerUser> users = userSession.findUsers();

    FolderServerUserListResponse r = new FolderServerUserListResponse();

    r.setUsers(users);

    return Response.ok().entity(r).build();
  }
}
