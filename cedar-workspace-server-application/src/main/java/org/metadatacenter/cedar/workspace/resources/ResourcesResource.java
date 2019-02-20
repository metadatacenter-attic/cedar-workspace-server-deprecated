package org.metadatacenter.cedar.workspace.resources;

import com.codahale.metrics.annotation.Timed;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.error.CedarErrorKey;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.model.folderserver.basic.FolderServerResource;
import org.metadatacenter.model.folderserver.extract.FolderServerNodeExtract;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.util.http.CedarResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

import static org.metadatacenter.constant.CedarPathParameters.PP_ID;
import static org.metadatacenter.rest.assertion.GenericAssertions.LoggedIn;

@Path("/resources")
@Produces(MediaType.APPLICATION_JSON)
public class ResourcesResource extends AbstractFolderServerResource {

  private static final Logger log = LoggerFactory.getLogger(ResourcesResource.class);

  public ResourcesResource(CedarConfig cedarConfig) {
    super(cedarConfig);
  }

  @GET
  @Timed
  @Path("/{id}")
  public Response findResource(@PathParam(PP_ID) String id) throws CedarException {
    CedarRequestContext c = buildRequestContext();
    c.must(c.user()).be(LoggedIn);

    FolderServiceSession folderSession = CedarDataServices.getFolderServiceSession(c);

    FolderServerResource resource = folderSession.findResourceById(id);
    if (resource == null) {
      return CedarResponse.notFound()
          .id(id)
          .errorKey(CedarErrorKey.RESOURCE_NOT_FOUND)
          .errorMessage("The resource can not be found by id")
          .build();
    }

    folderSession.addPathAndParentId(resource);

    List<FolderServerNodeExtract> pathInfo = folderSession.findNodePathExtract(resource);
    resource.setPathInfo(pathInfo);

    return Response.ok().entity(resource).build();

  }

}
