package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.metadatacenter.constant.HttpConstants;
import org.metadatacenter.model.folderserver.CedarFSFolder;
import org.metadatacenter.model.folderserver.CedarFSGroup;
import org.metadatacenter.model.folderserver.CedarFSNode;
import org.metadatacenter.model.response.FSGroupListResponse;
import org.metadatacenter.rest.CedarRequestContextFactory;
import org.metadatacenter.rest.PlayRequestContext;
import org.metadatacenter.rest.ICedarRequestContext;
import org.metadatacenter.server.neo4j.Neo4JUserSession;
import org.metadatacenter.server.neo4j.NodeLabel;
import org.metadatacenter.server.security.Authorization;
import org.metadatacenter.server.security.CedarAuthFromRequestFactory;
import org.metadatacenter.server.security.exception.CedarAccessException;
import org.metadatacenter.server.security.model.IAuthRequest;
import org.metadatacenter.server.security.model.auth.CedarPermission;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.util.json.JsonMapper;
import org.metadatacenter.util.parameter.ParameterUtil;
import play.mvc.Result;
import utils.DataServices;

import java.util.List;

public class GroupController extends AbstractFolderServerController {


  public static Result findGroups() {
    IAuthRequest frontendRequest = null;
    CedarUser currentUser = null;
    try {
      frontendRequest = CedarAuthFromRequestFactory.fromRequest(request());
      currentUser = Authorization.getUserAndEnsurePermission(frontendRequest, CedarPermission.LOGGED_IN);
    } catch (CedarAccessException e) {
      play.Logger.error("Access Error while reading the groups", e);
      return forbiddenWithError(e);
    }

    try {
      Neo4JUserSession neoSession = DataServices.getInstance().getNeo4JSession(currentUser);

      List<CedarFSGroup> groups = neoSession.findGroups();

      FSGroupListResponse r = new FSGroupListResponse();

      r.setGroups(groups);

      JsonNode resp = JsonMapper.MAPPER.valueToTree(r);
      return ok(resp);

    } catch (Exception e) {
      play.Logger.error("Error while listing groups", e);
      return internalServerErrorWithError(e);
    }
  }

  public static Result createGroup() {
    ICedarRequestContext c = CedarRequestContextFactory.fromRequest(request());

    c.must(c.user()).be(GenericAssertions.LoggedIn);
    c.must(c.user()).have(CedarPermission.GROUP_CREATE);

    c.must(c.request()).be(GenericAssertions.JsonBody, GenericAssertions.NonEmpty);
    ICedarRequestBody requestBody = c.request().jsonBody();

    CedarParameter groupName = requestBody.get("name");
    CedarParameter groupDescription = requestBody.get("description");
    c.validator().must(groupName, groupDescription).allPresent();

    Neo4JUserSession neoSession = CedarDataServices.getNeo4jSession(c);

    CedarFSGroup oldGroup = neoSession.findGroupByName(groupName.value());
    c.validator().must(oldGroup).beNull(c.operation().lookup(CedarFSGroup.class, "name", groupName));

    CedarFSGroup newGroup = neoSession.createGroup(groupName.value(), groupDescription.value(), c.user().id());
    c.validator().must(newGroup).beNotNull(c.operation().create(CedarFSGroup.class, "name", groupName));

    if (newGroup != null) {
      JsonNode createdGroup = JsonMapper.MAPPER.valueToTree(newGroup);
      String absoluteUrl = routes.GroupController.findGroup(newGroup.getId()).absoluteURL(request());
      response().setHeader(HttpConstants.HTTP_HEADER_LOCATION, absoluteUrl);
      return created(createdGroup);
    }

    return null;
  }
}