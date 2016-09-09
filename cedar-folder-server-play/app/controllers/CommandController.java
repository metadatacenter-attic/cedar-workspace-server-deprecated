package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.model.CedarNodeType;
import org.metadatacenter.model.folderserver.CedarFSFolder;
import org.metadatacenter.model.folderserver.CedarFSResource;
import org.metadatacenter.server.neo4j.Neo4JUserSession;
import org.metadatacenter.server.result.BackendCallErrorType;
import org.metadatacenter.server.result.BackendCallResult;
import org.metadatacenter.server.security.Authorization;
import org.metadatacenter.server.security.CedarAuthFromRequestFactory;
import org.metadatacenter.server.security.exception.CedarAccessException;
import org.metadatacenter.server.security.model.IAuthRequest;
import org.metadatacenter.server.security.model.auth.CedarPermission;
import org.metadatacenter.server.security.model.user.CedarUser;
import play.mvc.Result;
import utils.DataServices;

public class CommandController extends AbstractFolderServerController {


  public static Result moveNodeToFolder() {
    IAuthRequest frontendRequest = null;
    CedarUser currentUser = null;
    try {
      frontendRequest = CedarAuthFromRequestFactory.fromRequest(request());
      currentUser = Authorization.getUserAndEnsurePermission(frontendRequest, CedarPermission.LOGGED_IN);
    } catch (CedarAccessException e) {
      play.Logger.error("Access Error while moving the node", e);
      return forbiddenWithError(e);
    }

    JsonNode jsonBody = request().body().asJson();
    String sourceId = jsonBody.get("sourceId").asText();
    String nodeTypeString = jsonBody.get("nodeType").asText();
    String folderId = jsonBody.get("folderId").asText();

    CedarNodeType nodeType = CedarNodeType.forValue(nodeTypeString);


    Neo4JUserSession neoSession = DataServices.getInstance().getNeo4JSession(currentUser);

    boolean moved;
    CedarFSFolder targetFolder = neoSession.findFolderById(folderId);
    if (nodeType == CedarNodeType.FOLDER) {
      CedarFSFolder sourceFolder = neoSession.findFolderById(sourceId);
      moved = neoSession.moveFolder(sourceFolder, targetFolder);
    } else {
      CedarFSResource sourceResource = neoSession.findResourceById(sourceId);
      moved = neoSession.moveResource(sourceResource, targetFolder);
    }
    if (!moved) {
      BackendCallResult backendCallResult = new BackendCallResult();
      backendCallResult.addError(BackendCallErrorType.SERVER_ERROR)
          .subType("nodeNotMoved")
          .message("There was an error while moving the node");
      return backendCallError(backendCallResult);
    }
    return created();
  }
}