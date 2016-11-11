package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.constant.HttpConstants;
import org.metadatacenter.model.folderserver.FolderServerFolder;
import org.metadatacenter.model.folderserver.FolderServerNode;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.rest.exception.CedarAssertionException;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.server.PermissionServiceSession;
import org.metadatacenter.server.neo4j.NodeLabel;
import org.metadatacenter.server.result.BackendCallResult;
import org.metadatacenter.server.security.model.auth.CedarNodePermissions;
import org.metadatacenter.server.security.model.auth.CedarNodePermissionsRequest;
import org.metadatacenter.server.security.model.auth.CedarPermission;
import org.metadatacenter.server.security.model.auth.NodePermission;
import org.metadatacenter.util.json.JsonMapper;
import org.metadatacenter.util.parameter.ParameterUtil;
import play.mvc.Result;

import java.util.HashMap;
import java.util.Map;

import static org.metadatacenter.rest.assertion.GenericAssertions.LoggedIn;

public class FolderController extends AbstractFolderServerController {

  public static Result createFolder() throws CedarAssertionException {
    CedarRequestContext c = CedarRequestContextFactory.fromRequest(request());
    c.must(c.user()).be(LoggedIn);
    c.must(c.user()).have(CedarPermission.FOLDER_CREATE);

    try {
      JsonNode creationRequest = request().body().asJson();
      if (creationRequest == null) {
        return badRequest(generateErrorDescription("missingRequestBody",
            "You must supply the request body as a json object!"));
      }

      String folderId = ParameterUtil.getString(creationRequest, "folderId", "");
      String path = ParameterUtil.getString(creationRequest, "path", "");
      if (folderId.isEmpty() && path.isEmpty()) {
        return badRequest(generateErrorDescription("parentFolderNotSpecified",
            "You need to supply either path or folderId parameter identifying the parent folder"));
      }
      if (!folderId.isEmpty() && !path.isEmpty()) {
        return badRequest(generateErrorDescription("parentFolderSpecifiedTwice",
            "You need to supply either path or folderId parameter (not both) identifying the parent folder"));
      }

      FolderServiceSession folderSession = CedarDataServices.getFolderServiceSession(c);
      FolderServerFolder parentFolder = null;

      String normalizedPath = null;
      if (!path.isEmpty()) {
        normalizedPath = folderSession.normalizePath(path);
        if (!normalizedPath.equals(path)) {
          return badRequest(generateErrorDescription("pathNotNormalized",
              "You must supply the path of the new folder in normalized form!"));
        }
        parentFolder = folderSession.findFolderByPath(path);
      }

      if (!folderId.isEmpty()) {
        parentFolder = folderSession.findFolderById(folderId);
      }

      if (parentFolder == null) {
        ObjectNode errorParams = JsonNodeFactory.instance.objectNode();
        errorParams.put("path", path);
        errorParams.put("folderId", folderId);
        return badRequest(generateErrorDescription("parentNotPresent",
            "The parent folder is not present!", errorParams));
      }

      // get name parameter
      String name = ParameterUtil.getStringOrThrowError(creationRequest, "name",
          "You must supply the name of the new folder!");
      // test new folder name syntax
      String normalizedName = folderSession.sanitizeName(name);
      if (!normalizedName.equals(name)) {
        return badRequest(generateErrorDescription("invalidFolderName",
            "The new folder name contains invalid characters!"));
      }

      // get description parameter
      String description = ParameterUtil.getStringOrThrowError(creationRequest, "description",
          "You must supply the description of the new folder!");

      // check existence of parent folder
      FolderServerFolder newFolder = null;
      FolderServerNode newFolderCandidate = folderSession.findNodeByParentIdAndName(parentFolder, name);
      if (newFolderCandidate != null) {
        ObjectNode errorParams = JsonNodeFactory.instance.objectNode();
        errorParams.put("parentFolderId", parentFolder.getId());
        errorParams.put("name", name);
        return badRequest(generateErrorDescription("nodeAlreadyPresent",
            "There is already a node with the same name at the requested location!", errorParams));
      }

      newFolder = folderSession.createFolderAsChildOfId(parentFolder.getId(), name, name, description, NodeLabel
          .FOLDER);

      if (newFolder != null) {
        JsonNode createdFolder = JsonMapper.MAPPER.valueToTree(newFolder);
        String absoluteUrl = routes.FolderController.findFolder(newFolder.getId()).absoluteURL(request());
        response().setHeader(HttpConstants.HTTP_HEADER_LOCATION, absoluteUrl);
        return created(createdFolder);
      } else {
        ObjectNode errorParams = JsonNodeFactory.instance.objectNode();
        errorParams.put("path", path);
        errorParams.put("parentFolderId", parentFolder.getId());
        errorParams.put("name", name);
        return badRequest(generateErrorDescription("folderNotCreated",
            "The folder was not created!", errorParams));
      }
    } catch (Exception e) {
      play.Logger.error("Error while creating the folder", e);
      return internalServerErrorWithError(e);
    }
  }

  public static Result findFolder(String folderId) throws CedarAssertionException {
    CedarRequestContext c = CedarRequestContextFactory.fromRequest(request());
    c.must(c.user()).be(LoggedIn);
    c.must(c.user()).have(CedarPermission.FOLDER_READ);

    try {
      FolderServiceSession folderSession = CedarDataServices.getFolderServiceSession(c);
      PermissionServiceSession permissionSession = CedarDataServices.getPermissionServiceSession(c);

      FolderServerFolder folder = folderSession.findFolderById(folderId);
      if (folder == null) {
        ObjectNode errorParams = JsonNodeFactory.instance.objectNode();
        errorParams.put("id", folderId);
        return notFound(generateErrorDescription("folderNotFound",
            "The folder can not be found by id:" + folderId, errorParams));
      } else {
        folderSession.addPathAndParentId(folder);
        if (permissionSession.userHasReadAccessToFolder(folderId)) {
          folder.addCurrentUserPermission(NodePermission.READ);
        }
        if (permissionSession.userHasWriteAccessToFolder(folderId)) {
          folder.addCurrentUserPermission(NodePermission.WRITE);
        }
        if (permissionSession.userIsOwnerOfFolder(folderId)) {
          folder.addCurrentUserPermission(NodePermission.CHANGEOWNER);
        }
        JsonNode folderNode = JsonMapper.MAPPER.valueToTree(folder);
        return ok(folderNode);
      }
    } catch (Exception e) {
      play.Logger.error("Error while getting the folder", e);
      return internalServerErrorWithError(e);
    }
  }

  public static Result updateFolder(String folderId) throws CedarAssertionException {
    CedarRequestContext c = CedarRequestContextFactory.fromRequest(request());
    c.must(c.user()).be(LoggedIn);
    c.must(c.user()).have(CedarPermission.FOLDER_UPDATE);

    try {
      JsonNode folderUpdateRequest = request().body().asJson();
      if (folderUpdateRequest == null) {
        return badRequest(generateErrorDescription("missingRequestBody",
            "You must supply the request body as a json object!"));
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
          return badRequest(generateErrorDescription("invalidFolderName",
              "The folder name contains invalid characters!"));
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
        return badRequest(generateErrorDescription("missingNameAndDescription",
            "You must supply the new description or the new name of the folder!"));
      }

      FolderServerFolder folder = folderSession.findFolderById(folderId);
      if (folder == null) {
        ObjectNode errorParams = JsonNodeFactory.instance.objectNode();
        errorParams.put("id", folderId);
        return notFound(generateErrorDescription("folderNotFound",
            "The folder can not be found by id:" + folderId, errorParams));
      } else {
        Map<String, String> updateFields = new HashMap<>();
        if (description != null) {
          updateFields.put("description", description);
        }
        if (name != null) {
          updateFields.put("name", name);
          updateFields.put("displayName", name);
        }
        FolderServerFolder updatedFolder = folderSession.updateFolderById(folderId, updateFields);
        if (updatedFolder == null) {
          return notFound();
        } else {
          JsonNode updatedFolderNode = JsonMapper.MAPPER.valueToTree(updatedFolder);
          return ok(updatedFolderNode);
        }
      }
    } catch (Exception e) {
      play.Logger.error("Error while updating the folder", e);
      return internalServerErrorWithError(e);
    }
  }

  public static Result deleteFolder(String folderId) throws CedarAssertionException {
    CedarRequestContext c = CedarRequestContextFactory.fromRequest(request());
    c.must(c.user()).be(LoggedIn);
    c.must(c.user()).have(CedarPermission.FOLDER_DELETE);

    try {
      FolderServiceSession folderSession = CedarDataServices.getFolderServiceSession(c);

      FolderServerFolder folder = folderSession.findFolderById(folderId);
      if (folder == null) {
        ObjectNode errorParams = JsonNodeFactory.instance.objectNode();
        errorParams.put("id", folderId);
        return notFound(generateErrorDescription("folderNotFound",
            "The folder can not be found by id:" + folderId, errorParams));
      } else {
        if (folder.isSystem()) {
          ObjectNode errorParams = JsonNodeFactory.instance.objectNode();
          errorParams.put("id", folderId);
          errorParams.put("folderType", "system");
          return badRequest(generateErrorDescription("folderCanNotBeDeleted",
              "System folders can not be deleted", errorParams));
        } else {
          boolean deleted = folderSession.deleteFolderById(folderId);
          if (deleted) {
            return noContent();
          } else {
            // TODO: check folder contents, if not, delete only if "?force=true" param is present
            ObjectNode errorParams = JsonNodeFactory.instance.objectNode();
            errorParams.put("id", folderId);
            return internalServerErrorWithError(generateErrorDescription("folderNotDeleted",
                "The folder can not be delete by id:" + folderId, errorParams));
          }
        }
      }
    } catch (Exception e) {
      play.Logger.error("Error while deleting the folder", e);
      return internalServerErrorWithError(e);
    }
  }

  public static Result getPermissions(String folderId) throws CedarAssertionException {
    CedarRequestContext c = CedarRequestContextFactory.fromRequest(request());
    c.must(c.user()).be(LoggedIn);

    try {
      FolderServiceSession folderSession = CedarDataServices.getFolderServiceSession(c);
      PermissionServiceSession permissionSession = CedarDataServices.getPermissionServiceSession(c);

      FolderServerFolder folder = folderSession.findFolderById(folderId);
      if (folder == null) {
        ObjectNode errorParams = JsonNodeFactory.instance.objectNode();
        errorParams.put("id", folderId);
        return notFound(generateErrorDescription("folderNotFound",
            "The folder can not be found by id:" + folderId, errorParams));
      } else {
        CedarNodePermissions permissions = permissionSession.getNodePermissions(folderId, true);
        JsonNode permissionsNode = JsonMapper.MAPPER.valueToTree(permissions);
        return ok(permissionsNode);
      }
    } catch (Exception e) {
      play.Logger.error("Error while getting the folder", e);
      return internalServerErrorWithError(e);
    }
  }

  public static Result updatePermissions(String folderId) throws CedarAssertionException {
    CedarRequestContext c = CedarRequestContextFactory.fromRequest(request());
    c.must(c.user()).be(LoggedIn);

    try {
      JsonNode permissionUpdateRequest = request().body().asJson();
      if (permissionUpdateRequest == null) {
        return badRequest(generateErrorDescription("missingRequestBody",
            "You must supply the request body as a json object!"));
      }

      FolderServiceSession folderSession = CedarDataServices.getFolderServiceSession(c);
      PermissionServiceSession permissionSession = CedarDataServices.getPermissionServiceSession(c);

      CedarNodePermissionsRequest permissionsRequest = JsonMapper.MAPPER.treeToValue(permissionUpdateRequest,
          CedarNodePermissionsRequest.class);

      FolderServerFolder folder = folderSession.findFolderById(folderId);
      if (folder == null) {
        ObjectNode errorParams = JsonNodeFactory.instance.objectNode();
        errorParams.put("id", folderId);
        return notFound(generateErrorDescription("folderNotFound",
            "The folder can not be found by id:" + folderId, errorParams));
      } else {
        BackendCallResult backendCallResult = permissionSession.updateNodePermissions(folderId, permissionsRequest,
            true);
        if (backendCallResult.isError()) {
          return backendCallError(backendCallResult);
        }
        CedarNodePermissions permissions = permissionSession.getNodePermissions(folderId, true);
        JsonNode permissionsNode = JsonMapper.MAPPER.valueToTree(permissions);
        return ok(permissionsNode);
      }
    } catch (Exception e) {
      play.Logger.error("Error while updating the folder permissions", e);
      return internalServerErrorWithError(e);
    }
  }

}
