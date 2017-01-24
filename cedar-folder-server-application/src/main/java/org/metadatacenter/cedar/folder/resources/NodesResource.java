package org.metadatacenter.cedar.folder.resources;

import com.codahale.metrics.annotation.Timed;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.model.folderserver.FolderServerNode;
import org.metadatacenter.model.request.NodeListQueryType;
import org.metadatacenter.model.request.NodeListRequest;
import org.metadatacenter.model.response.FolderServerNodeListResponse;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.util.http.LinkHeaderUtil;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.metadatacenter.constant.CedarQueryParameters.QP_LIMIT;
import static org.metadatacenter.constant.CedarQueryParameters.QP_OFFSET;
import static org.metadatacenter.constant.CedarQueryParameters.QP_SORT;
import static org.metadatacenter.rest.assertion.GenericAssertions.LoggedIn;

@Path("/nodes")
@Produces(MediaType.APPLICATION_JSON)
public class NodesResource extends AbstractFolderServerResource {

  final static List<String> knownSortKeys;
  public static final String DEFAULT_SORT;

  static {
    DEFAULT_SORT = "name";
    knownSortKeys = new ArrayList<>();
    knownSortKeys.add("name");
    knownSortKeys.add("createdOnTS");
    knownSortKeys.add("lastUpdatedOnTS");
  }

  public NodesResource(CedarConfig cedarConfig) {
    super(cedarConfig);
  }

  @GET
  @Timed
  public Response findAllNodes(@QueryParam(QP_SORT) Optional<String> sortParam,
                               @QueryParam(QP_LIMIT) Optional<Integer> limitParam,
                               @QueryParam(QP_OFFSET) Optional<Integer> offsetParam) throws CedarException {
    CedarRequestContext c = CedarRequestContextFactory.fromRequest(request);

    c.must(c.user()).be(LoggedIn);

    UriBuilder builder = uriInfo.getAbsolutePathBuilder();
    URI absoluteURI = builder.queryParam(QP_SORT, sortParam).build();

    // TODO : set default values for input parameters from config
    int limit = 50;
    int maxAllowedLimit = 50000;
    int offset = 0;

    // Input parameter validation: 'limit'
    if (limitParam.isPresent()) {
      if (limitParam.get() <= 0) {
        throw new IllegalArgumentException("You should specify a positive limit!");
      } else if (limitParam.get() > maxAllowedLimit) {
        throw new IllegalArgumentException("You should specify a limit smaller than " + maxAllowedLimit + "!");
      }
      limit = limitParam.get();
    }
    // Input parameter validation: 'offset'
    if (offsetParam.isPresent()) {
      if (offsetParam.get() < 0) {
        throw new IllegalArgumentException("You should specify a positive or zero offset!");
      }
      offset = offsetParam.get();
    }
    // Input parameter validation: 'sort'
    List<String> sortList = new ArrayList<>();
    if (sortParam.isPresent()) {
      sortList = Arrays.asList(sortParam.get().split("\\s*,\\s*"));
      for (String s : sortList) {
        if (!knownSortKeys.contains(s) && !knownSortKeys.contains("-" + s)) {
          throw new IllegalArgumentException("You passed an illegal sort type: '" + s + "'. The allowed values are:" +
              knownSortKeys);
        }
      }
    } else {
      sortList.add(DEFAULT_SORT);
    }

    FolderServiceSession folderSession = CedarDataServices.getFolderServiceSession(c);

    // Retrieve all resources
    List<FolderServerNode> resources = folderSession.findAllNodes(limit, offset, sortList);

    // Build response
    FolderServerNodeListResponse r = new FolderServerNodeListResponse();
    r.setNodeListQueryType(NodeListQueryType.ALL_NODES);
    NodeListRequest req = new NodeListRequest();
    req.setLimit(limit);
    req.setOffset(offset);
    req.setSort(sortList);
    r.setRequest(req);
    long total = folderSession.findAllNodesCount();
    r.setTotalCount(total);
    r.setCurrentOffset(offset);
    r.setResources(resources);
    r.setPaging(LinkHeaderUtil.getPagingLinkHeaders(absoluteURI, total, limit, offset));

    return Response.ok().entity(r).build();
  }

}
