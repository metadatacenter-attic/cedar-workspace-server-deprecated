package org.metadatacenter.cedar.folder.resources;

import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.model.folderserver.FolderServerFolder;
import org.metadatacenter.model.folderserver.FolderServerNode;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.rest.exception.CedarAssertionException;
import org.metadatacenter.rest.exception.CedarAssertionResult;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.server.PermissionServiceSession;
import org.metadatacenter.server.neo4j.NodeLabel;
import org.metadatacenter.server.result.BackendCallResult;
import org.metadatacenter.server.security.model.auth.CedarNodePermissions;
import org.metadatacenter.server.security.model.auth.CedarNodePermissionsRequest;
import org.metadatacenter.server.security.model.auth.CedarPermission;
import org.metadatacenter.server.security.model.auth.NodePermission;
import org.metadatacenter.util.http.CedarUrlUtil;
import org.metadatacenter.util.json.JsonMapper;
import org.metadatacenter.util.parameter.ParameterUtil;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.net.URI;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

import static org.metadatacenter.rest.assertion.GenericAssertions.LoggedIn;

@Path("/folders")
@Produces(MediaType.APPLICATION_JSON)
public class FoldersResource {

  private
  @Context
  UriInfo uriInfo;

  private
  @Context
  HttpServletRequest request;

  private static CedarConfig cedarConfig = CedarConfig.getInstance();

  public FoldersResource() {
  }

  @POST
  @Timed
  public Response createFolder() throws CedarAssertionException {
    CedarRequestContext c = CedarRequestContextFactory.fromRequest(request);

    c.must(c.user()).be(LoggedIn);
    c.must(c.user()).have(CedarPermission.FOLDER_CREATE);

    JsonNode creationRequest = c.request().getRequestBody().asJson();
    if (creationRequest == null) {
      Map<String, Object> errorParams = new HashMap<>();
      errorParams.put("errorId", "missingRequestBody");
      errorParams.put("errorMessage", "You must supply the request body as a json object!");
      return Response.status(Response.Status.BAD_REQUEST).entity(errorParams).build();
    }

    String folderId = ParameterUtil.getString(creationRequest, "folderId", "");
    String path = ParameterUtil.getString(creationRequest, "path", "");
    if (folderId.isEmpty() && path.isEmpty()) {
      Map<String, Object> errorParams = new HashMap<>();
      errorParams.put("errorId", "parentFolderNotSpecified");
      errorParams.put("errorMessage",
          "You need to supply either path or folderId parameter identifying the parent folder");
      return Response.status(Response.Status.BAD_REQUEST).entity(errorParams).build();
    }
    if (!folderId.isEmpty() && !path.isEmpty()) {
      Map<String, Object> errorParams = new HashMap<>();
      errorParams.put("errorId", "parentFolderSpecifiedTwice");
      errorParams.put("errorMessage",
          "You need to supply either path or folderId parameter (not both) identifying the parent folder");
      return Response.status(Response.Status.BAD_REQUEST).entity(errorParams).build();
    }

    FolderServiceSession folderSession = CedarDataServices.getFolderServiceSession(c);
    FolderServerFolder parentFolder = null;

    String normalizedPath = null;
    if (!path.isEmpty()) {
      normalizedPath = folderSession.normalizePath(path);
      if (!normalizedPath.equals(path)) {
        Map<String, Object> errorParams = new HashMap<>();
        errorParams.put("errorId", "pathNotNormalized");
        errorParams.put("errorMessage",
            "You must supply the path of the new folder in normalized form!");
        return Response.status(Response.Status.BAD_REQUEST).entity(errorParams).build();
      }
      parentFolder = folderSession.findFolderByPath(path);
    }

    if (!folderId.isEmpty()) {
      parentFolder = folderSession.findFolderById(folderId);
    }

    if (parentFolder == null) {
      Map<String, Object> errorParams = new HashMap<>();
      errorParams.put("path", path);
      errorParams.put("folderId", folderId);
      errorParams.put("errorId", "parentNotPresent");
      errorParams.put("errorMessage", "The parent folder is not present!");
      return Response.status(Response.Status.BAD_REQUEST).entity(errorParams).build();
    }

    // get name parameter
    String name = ParameterUtil.getStringOrThrowError(creationRequest, "name",
        "You must supply the name of the new folder!");
    // test new folder name syntax
    String normalizedName = folderSession.sanitizeName(name);
    if (!normalizedName.equals(name)) {
      Map<String, Object> errorParams = new HashMap<>();
      errorParams.put("errorId", "invalidFolderName");
      errorParams.put("errorMessage", "The new folder name contains invalid characters!");
      return Response.status(Response.Status.BAD_REQUEST).entity(errorParams).build();
    }

    // get description parameter
    String description = ParameterUtil.getStringOrThrowError(creationRequest, "description",
        "You must supply the description of the new folder!");

    // check existence of parent folder
    FolderServerFolder newFolder = null;
    FolderServerNode newFolderCandidate = folderSession.findNodeByParentIdAndName(parentFolder, name);
    if (newFolderCandidate != null) {
      Map<String, Object> errorParams = new HashMap<>();
      errorParams.put("parentFolderId", parentFolder.getId());
      errorParams.put("name", name);
      errorParams.put("errorId", "nodeAlreadyPresent");
      errorParams.put("errorMessage", "There is already a node with the same name at the requested location!");
      return Response.status(Response.Status.BAD_REQUEST).entity(errorParams).build();
    }

    newFolder = folderSession.createFolderAsChildOfId(parentFolder.getId(), name, name, description, NodeLabel
        .FOLDER);

    if (newFolder != null) {
      UriBuilder builder = uriInfo.getAbsolutePathBuilder();
      URI uri = builder.path(CedarUrlUtil.urlEncode(newFolder.getId())).build();
      return Response.created(uri).entity(newFolder).build();

    } else {
      Map<String, Object> errorParams = new HashMap<>();
      errorParams.put("path", path);
      errorParams.put("parentFolderId", parentFolder.getId());
      errorParams.put("name", name);
      errorParams.put("errorId", "folderNotCreated");
      errorParams.put("errorMessage", "The folder was not created!");
      return Response.status(Response.Status.BAD_REQUEST).entity(errorParams).build();
    }
  }

  @GET
  @Timed
  @Path("/{id}")
  public Response getFolder(@PathParam("id") String id) throws CedarAssertionException {
    CedarRequestContext c = CedarRequestContextFactory.fromRequest(request);
    c.must(c.user()).be(LoggedIn);
    c.must(c.user()).have(CedarPermission.FOLDER_READ);

    FolderServiceSession folderSession = CedarDataServices.getFolderServiceSession(c);
    PermissionServiceSession permissionSession = CedarDataServices.getPermissionServiceSession(c);

    FolderServerFolder folder = folderSession.findFolderById(id);
    if (folder == null) {
      Map<String, Object> errorParams = new HashMap<>();
      errorParams.put("id", id);
      errorParams.put("errorId", "folderNotFound");
      errorParams.put("errorMessage", "The folder can not be found by id:" + id);
      return Response.status(Response.Status.NOT_FOUND).entity(errorParams).build();
    } else {
      folderSession.addPathAndParentId(folder);
      if (permissionSession.userHasReadAccessToFolder(id)) {
        folder.addCurrentUserPermission(NodePermission.READ);
      }
      if (permissionSession.userHasWriteAccessToFolder(id)) {
        folder.addCurrentUserPermission(NodePermission.WRITE);
      }
      if (permissionSession.userIsOwnerOfFolder(id)) {
        folder.addCurrentUserPermission(NodePermission.CHANGEOWNER);
      }

      return Response.ok().entity(folder).build();
    }
  }

  @PUT
  @Timed
  @Path("/{id}")
  public Response updateFolder(@PathParam("id") String id) throws CedarAssertionException {
    CedarRequestContext c = CedarRequestContextFactory.fromRequest(request);
    c.must(c.user()).be(LoggedIn);
    c.must(c.user()).have(CedarPermission.FOLDER_UPDATE);

    JsonNode folderUpdateRequest = c.request().getRequestBody().asJson();
    if (folderUpdateRequest == null) {
      Map<String, Object> errorParams = new HashMap<>();
      errorParams.put("errorId", "missingRequestBody");
      errorParams.put("errorMessage", "You must supply the request body as a json object!");
      return Response.status(Response.Status.BAD_REQUEST).entity(errorParams).build();
    }

    FolderServiceSession folderSession = CedarDataServices.getFolderServiceSession(c);

    String name = null;
    JsonNode nameNode = folderUpdateRequest.get("name");
    if (nameNode != null) {
      name = nameNode.asText();
      if (name != null) {
        name = name.trim();
      }
    }

    // test update folder name syntax
    if (name != null) {
      String normalizedName = folderSession.sanitizeName(name);
      if (!normalizedName.equals(name)) {
        Map<String, Object> errorParams = new HashMap<>();
        errorParams.put("errorId", "invalidFolderName");
        errorParams.put("errorMessage", "The folder name contains invalid characters!");
        return Response.status(Response.Status.BAD_REQUEST).entity(errorParams).build();
      }
    }

    String description = null;
    JsonNode descriptionNode = folderUpdateRequest.get("description");
    if (descriptionNode != null) {
      description = descriptionNode.asText();
      if (description != null) {
        description = description.trim();
      }
    }

    if ((name == null || name.isEmpty()) && (description == null || description.isEmpty())) {
      Map<String, Object> errorParams = new HashMap<>();
      errorParams.put("errorId", "missingNameAndDescription");
      errorParams.put("errorMessage", "You must supply the new description or the new name of the folder!");
      return Response.status(Response.Status.BAD_REQUEST).entity(errorParams).build();
    }

    FolderServerFolder folder = folderSession.findFolderById(id);
    if (folder == null) {
      Map<String, Object> errorParams = new HashMap<>();
      errorParams.put("id", id);
      errorParams.put("errorId", "folderNotFound");
      errorParams.put("errorMessage", "The folder can not be found by id:" + id);
      return Response.status(Response.Status.NOT_FOUND).entity(errorParams).build();
    } else {
      Map<String, String> updateFields = new HashMap<>();
      if (description != null) {
        updateFields.put("description", description);
      }
      if (name != null) {
        updateFields.put("name", name);
        updateFields.put("displayName", name);
      }
      FolderServerFolder updatedFolder = folderSession.updateFolderById(id, updateFields);
      if (updatedFolder == null) {
        return Response.status(Response.Status.NOT_FOUND).build();
      } else {
        return Response.ok().entity(updatedFolder).build();
      }
    }
  }

  @DELETE
  @Timed
  @Path("/{id}")
  public Response deleteFolder(@PathParam("id") String id) throws CedarAssertionException {
    CedarRequestContext c = CedarRequestContextFactory.fromRequest(request);
    c.must(c.user()).be(LoggedIn);
    c.must(c.user()).have(CedarPermission.FOLDER_DELETE);

    FolderServiceSession folderSession = CedarDataServices.getFolderServiceSession(c);

    FolderServerFolder folder = folderSession.findFolderById(id);
    if (folder == null) {
      Map<String, Object> errorParams = new HashMap<>();
      errorParams.put("id", id);
      errorParams.put("errorId", "folderNotFound");
      errorParams.put("errorMessage", "The folder can not be found by id:" + id);
      return Response.status(Response.Status.NOT_FOUND).entity(errorParams).build();
    } else {
      if (folder.isSystem()) {
        Map<String, Object> errorParams = new HashMap<>();
        errorParams.put("id", id);
        errorParams.put("folderType", "system");
        errorParams.put("errorId", "folderCanNotBeDeleted");
        errorParams.put("errorMessage", "System folders can not be deleted");
        return Response.status(Response.Status.BAD_REQUEST).entity(errorParams).build();
      } else {
        boolean deleted = folderSession.deleteFolderById(id);
        if (deleted) {
          return Response.status(Response.Status.NO_CONTENT).build();
        } else {
          // TODO: check folder contents, if not, delete only if "?force=true" param is present
          Map<String, Object> errorParams = new HashMap<>();
          errorParams.put("id", id);
          errorParams.put("errorId", "folderNotDeleted");
          errorParams.put("errorMessage", "The folder can not be delete by id:" + id);
          return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(errorParams).build();
        }
      }
    }
  }

  @GET
  @Timed
  @Path("/{id}/permissions")
  public Response getPermissions(@PathParam("id") String folderId) throws CedarAssertionException {
    CedarRequestContext c = CedarRequestContextFactory.fromRequest(request);
    c.must(c.user()).be(LoggedIn);

    FolderServiceSession folderSession = CedarDataServices.getFolderServiceSession(c);
    PermissionServiceSession permissionSession = CedarDataServices.getPermissionServiceSession(c);

    FolderServerFolder folder = folderSession.findFolderById(folderId);
    if (folder == null) {
      Map<String, Object> errorParams = new HashMap<>();
      errorParams.put("id", folderId);
      errorParams.put("errorId", "folderNotFound");
      errorParams.put("errorMessage", "The folder can not be found by id:" + folderId);
      return Response.status(Response.Status.NOT_FOUND).entity(errorParams).build();
    } else {
      CedarNodePermissions permissions = permissionSession.getNodePermissions(folderId, true);
      return Response.ok().entity(permissions).build();
    }
  }

  @PUT
  @Timed
  @Path("/{id}/permissions")
  public Response updatePermissions(@PathParam("id") String folderId) throws CedarAssertionException {
    CedarRequestContext c = CedarRequestContextFactory.fromRequest(request);
    c.must(c.user()).be(LoggedIn);

    JsonNode permissionUpdateRequest = c.request().getRequestBody().asJson();
    if (permissionUpdateRequest == null) {
      Map<String, Object> errorParams = new HashMap<>();
      errorParams.put("errorId", "missingRequestBody");
      errorParams.put("errorMessage", "You must supply the request body as a json object!" + folderId);
      return Response.status(Response.Status.BAD_REQUEST).entity(errorParams).build();
    }

    FolderServiceSession folderSession = CedarDataServices.getFolderServiceSession(c);
    PermissionServiceSession permissionSession = CedarDataServices.getPermissionServiceSession(c);

    CedarNodePermissionsRequest permissionsRequest = null;
    try {
      permissionsRequest = JsonMapper.MAPPER.treeToValue(permissionUpdateRequest, CedarNodePermissionsRequest.class);
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }

    FolderServerFolder folder = folderSession.findFolderById(folderId);
    if (folder == null) {
      Map<String, Object> errorParams = new HashMap<>();
      errorParams.put("id", folderId);
      errorParams.put("errorId", "folderNotFound");
      errorParams.put("errorMessage", "The folder can not be found by id:" + folderId);
      return Response.status(Response.Status.NOT_FOUND).entity(errorParams).build();
    } else {
      BackendCallResult backendCallResult = permissionSession.updateNodePermissions(folderId, permissionsRequest,
          true);
      if (backendCallResult.isError()) {
        throw new CedarAssertionException(new CedarAssertionResult(backendCallResult));
      }
      CedarNodePermissions permissions = permissionSession.getNodePermissions(folderId, true);
      return Response.ok().entity(permissions).build();
    }
  }

  /*
  protected static Integer ensureLimit(Integer limit) {
    return limit == null ? cedarConfig.getFolderRESTAPI().getPagination().getDefaultPageSize() : limit;
  }

  protected static void checkPagingParameters(Integer limit, Integer offset) {
    // check offset
    if (offset < 0) {
      throw new IllegalArgumentException("Parameter 'offset' must be positive!");
    }
    // check limit
    if (limit <= 0) {
      throw new IllegalArgumentException("Parameter 'limit' must be greater than zero!");
    }
    int maxPageSize = cedarConfig.getFolderRESTAPI().getPagination().getDefaultPageSize();
    if (limit > maxPageSize) {
      throw new IllegalArgumentException("Parameter 'limit' must be at most " + maxPageSize + "!");
    }
  }

  protected static void checkPagingParametersAgainstTotal(Integer offset, long total) {
    if (offset != 0 && offset > total - 1) {
      throw new IllegalArgumentException("Parameter 'offset' must be smaller than the total count of objects, which " +
          "is " + total + "!");
    }
  }*/
}
