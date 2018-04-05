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
import org.metadatacenter.model.folderserver.FolderServerFolder;
import org.metadatacenter.model.folderserver.FolderServerResource;
import org.metadatacenter.model.folderserver.FolderServerResourceBuilder;
import org.metadatacenter.rest.assertion.noun.CedarParameter;
import org.metadatacenter.rest.assertion.noun.CedarRequestBody;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.server.neo4j.NodeLabel;
import org.metadatacenter.server.result.BackendCallResult;
import org.metadatacenter.util.CedarNodeTypeUtil;
import org.metadatacenter.util.http.CedarResponse;
import org.metadatacenter.util.http.CedarUrlUtil;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import java.net.URI;

import static org.metadatacenter.rest.assertion.GenericAssertions.LoggedIn;
import static org.metadatacenter.rest.assertion.GenericAssertions.NonEmpty;

@Path("/command")
@Produces(MediaType.APPLICATION_JSON)
public class CommandResource extends AbstractFolderServerResource {

  public CommandResource(CedarConfig cedarConfig) {
    super(cedarConfig);
  }

  @POST
  @Timed
  @Path("/move-node-to-folder")
  public Response moveNodeToFolder() throws CedarException {
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
      backendCallResult.addError(CedarErrorType.SERVER_ERROR)
          .errorKey(CedarErrorKey.NODE_NOT_MOVED)
          .message("There was an error while moving the node");
      throw new CedarBackendException(backendCallResult);
    }

    // TODO: maybe this should not be CREATED.
    // TODO: if yes, what should be the returned location?
    UriBuilder builder = uriInfo.getAbsolutePathBuilder();
    URI uri = builder.build();

    return Response.created(uri).build();
  }

  @POST
  @Timed
  @Path("/create-draft-resource")
  public Response createDraftResource() throws CedarException {
    CedarRequestContext c = CedarRequestContextFactory.fromRequest(request);

    c.must(c.user()).be(LoggedIn);


    CedarRequestBody requestBody = c.request().getRequestBody();
    String oldId = requestBody.get("oldId").stringValue();
    String newId = requestBody.get("newId").stringValue();
    String folderId = requestBody.get("folderId").stringValue();
    String nodeTypeString = requestBody.get("nodeType").stringValue();
    String versionString = requestBody.get("version").stringValue();
    String statusString = requestBody.get("status").stringValue();
    String propagateSharingString = requestBody.get("propagateSharing").stringValue();

    CedarNodeType nodeType = CedarNodeType.forValue(nodeTypeString);

    FolderServiceSession folderSession = CedarDataServices.getFolderServiceSession(c);

    FolderServerFolder targetFolder = folderSession.findFolderById(folderId);
    //TODO: Must have write access
    FolderServerResource sourceResource = folderSession.findResourceById(oldId);

    NodeLabel label = NodeLabel.forCedarNodeType(nodeType);
    ResourceVersion version = ResourceVersion.forValue(versionString);
    BiboStatus status = BiboStatus.forValue(statusString);
    FolderServerResource brandNewResource = FolderServerResourceBuilder.forNodeType(nodeType);
    brandNewResource.setId1(newId);
    brandNewResource.setType(nodeType);
    brandNewResource.setName1(sourceResource.getName());
    brandNewResource.setDescription1(sourceResource.getDescription());
    brandNewResource.setVersion1(versionString);
    brandNewResource.setStatus1(statusString);
    FolderServerResource newResource = folderSession.createResourceAsChildOfId(brandNewResource, folderId);
    if (newResource == null) {
      BackendCallResult backendCallResult = new BackendCallResult();
      backendCallResult.addError(CedarErrorType.SERVER_ERROR)
          .errorKey(CedarErrorKey.DRAFT_NOT_CREATED)
          .message("There was an error while creating the draft version of the resource");
      throw new CedarBackendException(backendCallResult);
    } else {
      folderSession.setPreviousVersion(newId, oldId);
      // TODO: do the propagate sharing part
      //!!!!!
      // TODO: Create a builder helper for Folders, nodes, resources. Make that work with strong types, as the list
      // of params before
      // TODO: What about the node labels? There was a method which collected those
    }

    // TODO: maybe this should not be CREATED.
    // TODO: if yes, what should be the returned location?
    UriBuilder builder = uriInfo.getAbsolutePathBuilder();
    URI uri = builder.build();

    return Response.created(uri).entity(newResource).build();
  }

  @POST
  @Timed
  @Path("/copy-resource-to-folder")
  public Response copyResourceToFolder() throws CedarException {
    CedarRequestContext c = CedarRequestContextFactory.fromRequest(request);

    c.must(c.user()).be(LoggedIn);

    c.must(c.request().getRequestBody()).be(NonEmpty);

    FolderServiceSession folderSession = CedarDataServices.getFolderServiceSession(c);

    CedarParameter parentIdP = c.request().getRequestBody().get("parentId");
    c.must(parentIdP).be(NonEmpty);
    String parentId = parentIdP.stringValue();

    CedarParameter idP = c.request().getRequestBody().get("id");
    c.must(idP).be(NonEmpty);
    String id = idP.stringValue();

    CedarParameter oldIdP = c.request().getRequestBody().get("oldId");
    c.must(oldIdP).be(NonEmpty);
    String oldId = oldIdP.stringValue();

    CedarParameter name = c.request().getRequestBody().get("name");
    c.must(name).be(NonEmpty);

    CedarParameter nodeTypeP = c.request().getRequestBody().get("nodeType");
    c.must(nodeTypeP).be(NonEmpty);


    String nodeTypeString = nodeTypeP.stringValue();

    CedarNodeType nodeType = CedarNodeType.forValue(nodeTypeString);
    if (!CedarNodeTypeUtil.isValidForRestCall(nodeType)) {
      return CedarResponse.badRequest()
          .errorMessage("You passed an illegal nodeType:'" + nodeTypeString +
              "'. The allowed values are:" + CedarNodeTypeUtil.getValidNodeTypesForRestCalls())
          .errorKey(CedarErrorKey.INVALID_NODE_TYPE)
          .parameter("invalidNodeTypes", nodeTypeString)
          .parameter("allowedNodeTypes", CedarNodeTypeUtil.getValidNodeTypeValuesForRestCalls())
          .build();
    }

    String descriptionV = null;
    CedarParameter description = c.request().getRequestBody().get("description");
    descriptionV = description.stringValue();

    ResourceVersion version = ResourceVersion.ZERO_ZERO_ONE;
    BiboStatus status = BiboStatus.DRAFT;

    // check existence of parent folder
    FolderServerResource newResource = null;
    FolderServerFolder parentFolder = folderSession.findFolderById(parentId);

    String candidatePath = null;
    if (parentFolder == null) {
      return CedarResponse.badRequest()
          .parameter("folderId", parentId)
          .errorKey(CedarErrorKey.PARENT_FOLDER_NOT_FOUND)
          .errorMessage("The parent folder is not present!")
          .build();
    } else {
      // Later we will guarantee some kind of uniqueness for the resource names
      // Currently we allow duplicate names, the id is the PK
      FolderServerResource brandNewResource = FolderServerResourceBuilder.forNodeType(nodeType);
      brandNewResource.setId1(id);
      brandNewResource.setType(nodeType);
      brandNewResource.setName1(name.stringValue());
      brandNewResource.setDescription1(descriptionV);
      brandNewResource.setVersion1(version.getValue());
      brandNewResource.setStatus1(status.getValue());
      newResource = folderSession.createResourceAsChildOfId(brandNewResource, parentId);
    }

    if (newResource != null) {
      folderSession.setDerivedFrom(id, oldId);

      UriBuilder builder = uriInfo.getAbsolutePathBuilder();
      URI uri = builder.path(CedarUrlUtil.urlEncode(id)).build();
      return Response.created(uri).entity(newResource).build();
    } else {
      return CedarResponse.badRequest()
          .parameter("id", id)
          .parameter("parentId", parentId)
          .parameter("resourceType", nodeTypeString)
          .errorKey(CedarErrorKey.RESOURCE_NOT_CREATED)
          .errorMessage("The resource was not created!")
          .build();
    }
  }

}
