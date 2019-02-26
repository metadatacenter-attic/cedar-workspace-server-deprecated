package org.metadatacenter.cedar.workspace.resources;

import com.codahale.metrics.annotation.Timed;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.error.CedarErrorKey;
import org.metadatacenter.error.CedarErrorType;
import org.metadatacenter.exception.CedarBackendException;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.model.BiboStatus;
import org.metadatacenter.model.CedarNodeType;
import org.metadatacenter.model.ResourceVersion;
import org.metadatacenter.model.WorkspaceObjectBuilder;
import org.metadatacenter.model.folderserver.basic.FolderServerFolder;
import org.metadatacenter.model.folderserver.basic.FolderServerResource;
import org.metadatacenter.rest.assertion.noun.CedarRequestBody;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.server.PermissionServiceSession;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;
import org.metadatacenter.server.result.BackendCallResult;
import org.metadatacenter.server.security.model.auth.CedarNodePermissions;
import org.metadatacenter.server.security.model.auth.CedarNodePermissionsRequest;
import org.metadatacenter.server.security.model.auth.NodePermissionUser;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.metadatacenter.rest.assertion.GenericAssertions.LoggedIn;

@Path("/command")
@Produces(MediaType.APPLICATION_JSON)
public class CommandResource extends AbstractFolderServerResource {

  public CommandResource(CedarConfig cedarConfig) {
    super(cedarConfig);
  }

  @POST
  @Timed
  @Path("/create-draft-resource")
  public Response createDraftResource() throws CedarException {
    CedarRequestContext c = buildRequestContext();

    c.must(c.user()).be(LoggedIn);


    CedarRequestBody requestBody = c.request().getRequestBody();
    String oldId = requestBody.get("oldId").stringValue();
    String newId = requestBody.get("newId").stringValue();
    String folderId = requestBody.get("folderId").stringValue();
    String nodeTypeString = requestBody.get("nodeType").stringValue();
    String versionString = requestBody.get("version").stringValue();
    String publicationStatusString = requestBody.get("publicationStatus").stringValue();
    String propagateSharingString = requestBody.get("propagateSharing").stringValue();

    CedarNodeType nodeType = CedarNodeType.forValue(nodeTypeString);

    FolderServiceSession folderSession = CedarDataServices.getFolderServiceSession(c);

    FolderServerFolder targetFolder = folderSession.findFolderById(folderId);
    //TODO: Must have write access
    FolderServerResource sourceResource = folderSession.findResourceById(oldId);

    ResourceVersion version = ResourceVersion.forValue(versionString);
    BiboStatus status = BiboStatus.forValue(publicationStatusString);
    FolderServerResource brandNewResource = WorkspaceObjectBuilder.forNodeType(nodeType, newId,
        sourceResource.getName(), sourceResource.getDescription(), sourceResource.getIdentifier(), version, status);
    if (nodeType.isVersioned()) {
      brandNewResource.setPreviousVersion(oldId);
      brandNewResource.setLatestVersion(true);
      brandNewResource.setLatestDraftVersion(true);
      brandNewResource.setLatestPublishedVersion(false);
    }

    folderSession.unsetLatestVersion(sourceResource.getId());
    FolderServerResource newResource = folderSession.createResourceAsChildOfId(brandNewResource, folderId);
    if (newResource == null) {
      BackendCallResult backendCallResult = new BackendCallResult();
      backendCallResult.addError(CedarErrorType.SERVER_ERROR)
          .errorKey(CedarErrorKey.DRAFT_NOT_CREATED)
          .message("There was an error while creating the draft version of the resource");
      throw new CedarBackendException(backendCallResult);
    } else {
      boolean propagateSharing = Boolean.parseBoolean(propagateSharingString);

      if (propagateSharing) {
        PermissionServiceSession permissionSession = CedarDataServices.getPermissionServiceSession(c);
        CedarNodePermissions permissions = permissionSession.getNodePermissions(oldId);
        CedarNodePermissionsRequest permissionsRequest = permissions.toRequest();
        NodePermissionUser newOwner = new NodePermissionUser();
        newOwner.setId(c.getCedarUser().getId());
        permissionsRequest.setOwner(newOwner);
        BackendCallResult backendCallResult = permissionSession.updateNodePermissions(newId, permissionsRequest);
        if (backendCallResult.isError()) {
          throw new CedarBackendException(backendCallResult);
        }

      }
    }

    // TODO: maybe this should not be CREATED.
    // TODO: if yes, what should be the returned location?
    UriBuilder builder = uriInfo.getAbsolutePathBuilder();
    URI uri = builder.build();

    return Response.created(uri).entity(newResource).build();
  }

  @POST
  @Timed
  @Path("/publish-resource")
  public Response publishResource() throws CedarException {
    CedarRequestContext c = buildRequestContext();

    c.must(c.user()).be(LoggedIn);


    CedarRequestBody requestBody = c.request().getRequestBody();
    String id = requestBody.get("id").stringValue();
    String nodeTypeString = requestBody.get("nodeType").stringValue();
    String versionString = requestBody.get("version").stringValue();

    CedarNodeType nodeType = CedarNodeType.forValue(nodeTypeString);

    FolderServiceSession folderSession = CedarDataServices.getFolderServiceSession(c);

    FolderServerResource sourceResource = folderSession.findResourceById(id);

    ResourceVersion version = ResourceVersion.forValue(versionString);

    sourceResource.setLatestPublishedVersion(true);

    Map<NodeProperty, String> updates = new HashMap<>();
    updates.put(NodeProperty.VERSION, version.getValue());
    updates.put(NodeProperty.PUBLICATION_STATUS, BiboStatus.PUBLISHED.getValue());
    folderSession.updateResourceById(id, nodeType, updates);

    if (nodeType.isVersioned()) {
      folderSession.setLatestVersion(id);
      folderSession.unsetLatestDraftVersion(id);
      folderSession.setLatestPublishedVersion(id);
      if (sourceResource.getPreviousVersion() != null) {
        folderSession.unsetLatestPublishedVersion(sourceResource.getPreviousVersion().getValue());
      }
    }

    FolderServerResource updatedResource = folderSession.findResourceById(id);

    // TODO: this should not be CREATED.
    // TODO: if yes, what should be the returned location?
    UriBuilder builder = uriInfo.getAbsolutePathBuilder();
    URI uri = builder.build();

    return Response.created(uri).entity(updatedResource).build();
  }

}
