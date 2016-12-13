package org.metadatacenter.cedar.folder.resources;

import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.model.CedarNodeType;
import org.metadatacenter.model.folderserver.FolderServerFolder;
import org.metadatacenter.model.folderserver.FolderServerResource;
import org.metadatacenter.rest.assertion.noun.CedarRequestBody;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.rest.exception.CedarAssertionException;
import org.metadatacenter.rest.exception.CedarAssertionResult;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.server.result.BackendCallErrorType;
import org.metadatacenter.server.result.BackendCallResult;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.*;
import java.net.URI;

import static org.metadatacenter.rest.assertion.GenericAssertions.LoggedIn;
import static org.metadatacenter.rest.assertion.GenericAssertions.NonEmpty;

@Path("/command")
@Produces(MediaType.APPLICATION_JSON)
public class CommandResource {

  private
  @Context
  UriInfo uriInfo;

  private
  @Context
  HttpServletRequest request;

  public CommandResource() {
  }

  @POST
  @Timed
  @Path("/move-node-to-folder")
  public Response moveNodeToFolder() throws CedarAssertionException {
    CedarRequestContext c = CedarRequestContextFactory.fromRequest(request);

    c.must(c.user()).be(LoggedIn);

    CedarRequestBody requestBody = c.request().getRequestBody();
    String sourceId = requestBody.get("sourceId").stringValue();
    String nodeTypeString = requestBody.get("nodeType").stringValue();
    String folderId = requestBody.get("folderId").stringValue();

    CedarNodeType nodeType = CedarNodeType.forValue(nodeTypeString);

    FolderServiceSession folderSession = CedarDataServices.getFolderServiceSession(c);

    boolean moved;
    FolderServerFolder targetFolder = folderSession.findFolderById(folderId);
    if (nodeType == CedarNodeType.FOLDER) {
      FolderServerFolder sourceFolder = folderSession.findFolderById(sourceId);
      moved = folderSession.moveFolder(sourceFolder, targetFolder);
    } else {
      FolderServerResource sourceResource = folderSession.findResourceById(sourceId);
      moved = folderSession.moveResource(sourceResource, targetFolder);
    }
    if (!moved) {
      BackendCallResult backendCallResult = new BackendCallResult();
      backendCallResult.addError(BackendCallErrorType.SERVER_ERROR)
          .subType("nodeNotMoved")
          .message("There was an error while moving the node");
      throw new CedarAssertionException(new CedarAssertionResult(backendCallResult));
    }

    // TODO: maybe this should not be CREATED.
    // TODO: if yes, what should be the returned location?
    UriBuilder builder = uriInfo.getAbsolutePathBuilder();
    URI uri = builder.build();

    return Response.created(uri).build();
  }
}
