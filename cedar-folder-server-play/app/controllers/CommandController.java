package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.model.CedarNodeType;
import org.metadatacenter.model.folderserver.FolderServerFolder;
import org.metadatacenter.model.folderserver.FolderServerResource;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.rest.exception.CedarAssertionException;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.server.result.BackendCallErrorType;
import org.metadatacenter.server.result.BackendCallResult;
import play.mvc.Result;

import static org.metadatacenter.rest.assertion.GenericAssertions.LoggedIn;

public class CommandController extends AbstractFolderServerController {


  public static Result moveNodeToFolder() throws CedarAssertionException {
    CedarRequestContext c = CedarRequestContextFactory.fromRequest(request());
    c.must(c.user()).be(LoggedIn);

    JsonNode jsonBody = request().body().asJson();
    String sourceId = jsonBody.get("sourceId").asText();
    String nodeTypeString = jsonBody.get("nodeType").asText();
    String folderId = jsonBody.get("folderId").asText();

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
      return backendCallError(backendCallResult);
    }
    return created();
  }
}