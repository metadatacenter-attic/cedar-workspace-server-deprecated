package org.metadatacenter.cedar.workspace.resources;

import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.error.CedarErrorKey;
import org.metadatacenter.error.CedarErrorReasonKey;
import org.metadatacenter.exception.CedarBackendException;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.model.FolderOrResource;
import org.metadatacenter.model.folderserver.FolderServerFolder;
import org.metadatacenter.model.folderserver.FolderServerNode;
import org.metadatacenter.model.folderserverextract.FolderServerNodeExtract;
import org.metadatacenter.rest.assertion.noun.CedarParameter;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.server.PermissionServiceSession;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;
import org.metadatacenter.server.result.BackendCallResult;
import org.metadatacenter.server.security.model.auth.CedarNodePermissions;
import org.metadatacenter.server.security.model.auth.CedarNodePermissionsRequest;
import org.metadatacenter.server.security.model.auth.CedarPermission;
import org.metadatacenter.server.security.model.auth.NodePermission;
import org.metadatacenter.util.http.CedarResponse;
import org.metadatacenter.util.http.CedarUrlUtil;
import org.metadatacenter.util.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.metadatacenter.constant.CedarPathParameters.PP_ID;
import static org.metadatacenter.rest.assertion.GenericAssertions.LoggedIn;
import static org.metadatacenter.rest.assertion.GenericAssertions.NonEmpty;

@Path("/folders")
@Produces(MediaType.APPLICATION_JSON)
public class FoldersResource extends AbstractFolderServerResource {

  private static final Logger log = LoggerFactory.getLogger(FoldersResource.class);

  public FoldersResource(CedarConfig cedarConfig) {
    super(cedarConfig);
  }

  @POST
  @Timed
  public Response createFolder() throws CedarException {
    CedarRequestContext c = CedarRequestContextFactory.fromRequest(request);

    c.must(c.user()).be(LoggedIn);
    c.must(c.user()).have(CedarPermission.FOLDER_CREATE);

    c.must(c.request().getRequestBody()).be(NonEmpty);
    JsonNode creationRequest = c.request().getRequestBody().asJson();

    CedarParameter folderId = c.request().getRequestBody().get("folderId");
    CedarParameter path = c.request().getRequestBody().get("path");

    if (folderId.isMissing() && path.isEmpty()) {
      return CedarResponse.badRequest()
          .errorKey(CedarErrorKey.PARENT_FOLDER_NOT_SPECIFIED)
          .errorMessage("You need to supply either path or folderId parameter identifying the parent folder")
          .build();
    }

    if (!folderId.isEmpty() && !path.isEmpty()) {
      return CedarResponse.badRequest()
          .errorKey(CedarErrorKey.PARENT_FOLDER_SPECIFIED_TWICE)
          .errorMessage("You need to supply either path or folderId parameter (not both) identifying the parent folder")
          .build();
    }

    FolderServiceSession folderSession = CedarDataServices.getFolderServiceSession(c);
    FolderServerFolder parentFolder = null;

    String pathV = null;
    String folderIdV;

    String normalizedPath = null;
    if (!path.isEmpty()) {
      pathV = path.stringValue();
      normalizedPath = folderSession.normalizePath(pathV);
      if (!normalizedPath.equals(pathV)) {
        return CedarResponse.badRequest()
            .errorKey(CedarErrorKey.PATH_NOT_NORMALIZED)
            .errorMessage("You must supply the path of the new folder in normalized form!")
            .build();
      }
      parentFolder = folderSession.findFolderByPath(pathV);
    }

    if (!folderId.isEmpty()) {
      folderIdV = folderId.stringValue();
      parentFolder = folderSession.findFolderById(folderIdV);
    }

    if (parentFolder == null) {
      return CedarResponse.badRequest()
          .parameter("path", path)
          .parameter("folderId", folderId)
          .errorKey(CedarErrorKey.PARENT_FOLDER_NOT_FOUND)
          .errorMessage("The parent folder is not present!")
          .build();
    }


    // get name parameter
    CedarParameter name = c.request().getRequestBody().get("name");
    c.must(name).be(NonEmpty);

    String nameV = name.stringValue();
    // test new folder name syntax
    String normalizedName = folderSession.sanitizeName(nameV);
    if (!normalizedName.equals(nameV)) {
      return CedarResponse.badRequest()
          .errorKey(CedarErrorKey.CREATE_INVALID_FOLDER_NAME)
          .errorMessage("The new folder name contains invalid characters!")
          .parameter("name", name.stringValue())
          .build();
    }

    CedarParameter description = c.request().getRequestBody().get("description");
    c.must(description).be(NonEmpty);

    // check existence of parent folder
    FolderServerFolder newFolder = null;
    FolderServerNode newFolderCandidate = folderSession.findNodeByParentIdAndName(parentFolder, nameV);
    if (newFolderCandidate != null) {
      return CedarResponse.badRequest()
          .parameter("parentFolderId", parentFolder.getId())
          .parameter("name", name)
          .errorKey(CedarErrorKey.NODE_ALREADY_PRESENT)
          .errorMessage("There is already a node with the same name at the requested location!")
          .parameter("conflictingNodeType", newFolderCandidate.getType().getValue())
          .parameter("conflictingNodeId", newFolderCandidate.getId())
          .build();
    }

    String descriptionV = description.stringValue();

    FolderServerFolder brandNewFolder = new FolderServerFolder();
    brandNewFolder.setName(nameV);
    brandNewFolder.setDescription(descriptionV);
    newFolder = folderSession.createFolderAsChildOfId(brandNewFolder, parentFolder.getId());

    if (newFolder != null) {
      UriBuilder builder = uriInfo.getAbsolutePathBuilder();
      URI uri = builder.path(CedarUrlUtil.urlEncode(newFolder.getId())).build();
      return Response.created(uri).entity(newFolder).build();

    } else {
      return CedarResponse.badRequest()
          .parameter("path", pathV)
          .parameter("parentFolderId", parentFolder.getId())
          .parameter("name", nameV)
          .errorKey(CedarErrorKey.FOLDER_NOT_CREATED)
          .errorMessage("The folder was not created!")
          .build();
    }
  }

  @GET
  @Timed
  @Path("/{id}")
  public Response getFolder(@PathParam(PP_ID) String id) throws CedarException {
    CedarRequestContext c = CedarRequestContextFactory.fromRequest(request);
    c.must(c.user()).be(LoggedIn);
    c.must(c.user()).have(CedarPermission.FOLDER_READ);

    FolderServiceSession folderSession = CedarDataServices.getFolderServiceSession(c);
    PermissionServiceSession permissionSession = CedarDataServices.getPermissionServiceSession(c);

    FolderServerFolder folder = folderSession.findFolderById(id);
    if (folder == null) {
      return CedarResponse.notFound()
          .id(id)
          .errorKey(CedarErrorKey.FOLDER_NOT_FOUND)
          .errorMessage("The folder can not be found by id")
          .build();
    } else {
      folderSession.addPathAndParentId(folder);
      if (permissionSession.userHasReadAccessToFolder(id)) {
        folder.addCurrentUserPermission(NodePermission.READ);
      }
      if (permissionSession.userHasWriteAccessToFolder(id)) {
        folder.addCurrentUserPermission(NodePermission.WRITE);
      }
      if (permissionSession.userCanChangeOwnerOfFolder(id)) {
        folder.addCurrentUserPermission(NodePermission.CHANGEOWNER);
      }
      if (!folder.isRoot() && !folder.isSystem() && !folder.isUserHome()
          && permissionSession.userHasWriteAccessToFolder(id)) {
        folder.addCurrentUserPermission(NodePermission.CHANGEPERMISSIONS);
      }

      List<FolderServerNodeExtract> pathInfo = folderSession.findNodePathExtract(folder);
      folder.setPathInfo(pathInfo);

      return Response.ok().entity(folder).build();
    }
  }

  @PUT
  @Timed
  @Path("/{id}")
  public Response updateFolder(@PathParam(PP_ID) String id) throws CedarException {
    CedarRequestContext c = CedarRequestContextFactory.fromRequest(request);
    c.must(c.user()).be(LoggedIn);
    c.must(c.user()).have(CedarPermission.FOLDER_UPDATE);

    c.must(c.request().getRequestBody()).be(NonEmpty);

    FolderServiceSession folderSession = CedarDataServices.getFolderServiceSession(c);

    CedarParameter name = c.request().getRequestBody().get("name");

    String nameV = null;
    if (!name.isEmpty()) {
      nameV = name.stringValue();
      nameV = nameV.trim();
      String normalizedName = folderSession.sanitizeName(nameV);
      if (!normalizedName.equals(nameV)) {
        return CedarResponse.badRequest()
            .errorKey(CedarErrorKey.UPDATE_INVALID_FOLDER_NAME)
            .errorMessage("The folder name contains invalid characters!")
            .parameter("name", name.stringValue())
            .build();
      }
    }

    CedarParameter description = c.request().getRequestBody().get("description");

    String descriptionV = null;
    if (!description.isEmpty()) {
      descriptionV = description.stringValue();
      descriptionV = descriptionV.trim();
    }

    if ((name == null || name.isEmpty()) && (description == null || description.isEmpty())) {
      return CedarResponse.badRequest()
          .errorKey(CedarErrorKey.MISSING_NAME_AND_DESCRIPTION)
          .errorMessage("You must supply the new description or the new name of the folder!")
          .build();
    }

    FolderServerFolder folder = folderSession.findFolderById(id);
    if (folder == null) {
      return CedarResponse.notFound()
          .id(id)
          .errorKey(CedarErrorKey.FOLDER_NOT_FOUND)
          .errorMessage("The folder can not be found by id")
          .build();
    } else {
      Map<NodeProperty, String> updateFields = new HashMap<>();
      if (descriptionV != null) {
        updateFields.put(NodeProperty.DESCRIPTION, descriptionV);
      }
      if (nameV != null) {
        updateFields.put(NodeProperty.NAME, nameV);
      }
      FolderServerFolder updatedFolder = folderSession.updateFolderById(id, updateFields);
      if (updatedFolder == null) {
        return CedarResponse.notFound().build();
      } else {
        return Response.ok().entity(updatedFolder).build();
      }
    }
  }

  @DELETE
  @Timed
  @Path("/{id}")
  public Response deleteFolder(@PathParam(PP_ID) String id) throws CedarException {
    CedarRequestContext c = CedarRequestContextFactory.fromRequest(request);
    c.must(c.user()).be(LoggedIn);
    c.must(c.user()).have(CedarPermission.FOLDER_DELETE);

    FolderServiceSession folderSession = CedarDataServices.getFolderServiceSession(c);

    FolderServerFolder folder = folderSession.findFolderById(id);
    if (folder == null) {
      return CedarResponse.notFound()
          .id(id)
          .errorKey(CedarErrorKey.FOLDER_NOT_FOUND)
          .errorMessage("The folder can not be found by id")
          .build();
    } else {
      long contentCount = folderSession.findFolderContentsUnfilteredCount(id);
      if (contentCount > 0) {
        return CedarResponse.badRequest()
            .id(id)
            .errorKey(CedarErrorKey.FOLDER_CAN_NOT_BE_DELETED)
            .errorReasonKey(CedarErrorReasonKey.NON_EMPTY_FOLDER)
            .errorMessage("Non-empty folders can not be deleted")
            .build();
      } else if (folder.isUserHome()) {
        return CedarResponse.badRequest()
            .id(id)
            .errorKey(CedarErrorKey.FOLDER_CAN_NOT_BE_DELETED)
            .errorReasonKey(CedarErrorReasonKey.USER_HOME_FOLDER)
            .errorMessage("User home folders can not be deleted")
            .build();
      } else if (folder.isSystem()) {
        return CedarResponse.badRequest()
            .id(id)
            .errorKey(CedarErrorKey.FOLDER_CAN_NOT_BE_DELETED)
            .errorReasonKey(CedarErrorReasonKey.SYSTEM_FOLDER)
            .errorMessage("System folders can not be deleted")
            .build();
      } else {
        boolean deleted = folderSession.deleteFolderById(id);
        if (deleted) {
          return CedarResponse.noContent().build();
        } else {
          return CedarResponse.internalServerError()
              .id(id)
              .errorKey(CedarErrorKey.FOLDER_NOT_DELETED)
              .errorMessage("The folder can not be delete by id")
              .build();
        }
      }
    }
  }

  @GET
  @Timed
  @Path("/{id}/permissions")
  public Response getPermissions(@PathParam(PP_ID) String folderId) throws CedarException {
    CedarRequestContext c = CedarRequestContextFactory.fromRequest(request);
    c.must(c.user()).be(LoggedIn);

    FolderServiceSession folderSession = CedarDataServices.getFolderServiceSession(c);
    PermissionServiceSession permissionSession = CedarDataServices.getPermissionServiceSession(c);

    FolderServerFolder folder = folderSession.findFolderById(folderId);
    if (folder == null) {
      return CedarResponse.notFound()
          .id(folderId)
          .errorKey(CedarErrorKey.FOLDER_NOT_FOUND)
          .errorMessage("The folder can not be found by id")
          .build();
    } else {
      CedarNodePermissions permissions = permissionSession.getNodePermissions(folderId, FolderOrResource.FOLDER);
      return Response.ok().entity(permissions).build();
    }
  }

  @PUT
  @Timed
  @Path("/{id}/permissions")
  public Response updatePermissions(@PathParam(PP_ID) String folderId) throws CedarException {
    CedarRequestContext c = CedarRequestContextFactory.fromRequest(request);
    c.must(c.user()).be(LoggedIn);

    c.must(c.request().getRequestBody()).be(NonEmpty);


    FolderServiceSession folderSession = CedarDataServices.getFolderServiceSession(c);
    PermissionServiceSession permissionSession = CedarDataServices.getPermissionServiceSession(c);

    CedarNodePermissionsRequest permissionsRequest = null;
    try {
      JsonNode permissionUpdateRequest = c.request().getRequestBody().asJson();
      permissionsRequest = JsonMapper.MAPPER.treeToValue(permissionUpdateRequest, CedarNodePermissionsRequest.class);
    } catch (JsonProcessingException e) {
      log.error("Error while reading permission update request", e);
    }

    FolderServerFolder folder = folderSession.findFolderById(folderId);
    if (folder == null) {
      return CedarResponse.notFound()
          .id(folderId)
          .errorKey(CedarErrorKey.FOLDER_NOT_FOUND)
          .errorMessage("The folder can not be found by id")
          .build();
    } else if (folder.isUserHome()) {
      return CedarResponse.badRequest()
          .id(folderId)
          .errorKey(CedarErrorKey.FOLDER_PERMISSIONS_CAN_NOT_BE_CHANGED)
          .errorReasonKey(CedarErrorReasonKey.USER_HOME_FOLDER)
          .errorMessage("User home folder permissions can not be changed")
          .build();
    } else {
      BackendCallResult backendCallResult = permissionSession.updateNodePermissions(folderId, permissionsRequest,
          FolderOrResource.FOLDER);
      if (backendCallResult.isError()) {
        throw new CedarBackendException(backendCallResult);
      }
      CedarNodePermissions permissions = permissionSession.getNodePermissions(folderId, FolderOrResource.FOLDER);
      return Response.ok().entity(permissions).build();
    }
  }
}
