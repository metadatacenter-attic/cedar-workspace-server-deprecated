package org.metadatacenter.cedar.workspace.resources;

import com.codahale.metrics.annotation.Timed;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.model.folderserverextract.FolderServerNodeExtract;
import org.metadatacenter.model.request.NodeListQueryType;
import org.metadatacenter.model.request.NodeListRequest;
import org.metadatacenter.model.response.FolderServerNodeListResponse;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.util.http.LinkHeaderUtil;
import org.metadatacenter.util.http.PagedSortedQuery;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import java.net.URI;
import java.util.List;
import java.util.Optional;

import static org.metadatacenter.constant.CedarQueryParameters.*;
import static org.metadatacenter.rest.assertion.GenericAssertions.LoggedIn;

@Path("/nodes")
@Produces(MediaType.APPLICATION_JSON)
public class NodesResource extends AbstractFolderServerResource {

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

    PagedSortedQuery pagedSortedQuery = new PagedSortedQuery(
        cedarConfig.getFolderRESTAPI().getPagination())
        .sort(sortParam)
        .limit(limitParam)
        .offset(offsetParam);
    pagedSortedQuery.validate();

    int limit = pagedSortedQuery.getLimit();
    int offset = pagedSortedQuery.getOffset();
    List<String> sortList = pagedSortedQuery.getSortList();

    FolderServiceSession folderSession = CedarDataServices.getFolderServiceSession(c);

    // Retrieve all resources
    List<FolderServerNodeExtract> resources = folderSession.findAllNodes(limit, offset, sortList);

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
