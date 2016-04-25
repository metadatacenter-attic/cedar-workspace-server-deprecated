package org.metadatacenter.server.neo4j;

import java.util.Map;

public class CypherQueryWithParameters implements CypherQuery {
  private String query;
  private Map<String, Object> parameters;

  public CypherQueryWithParameters(String query, Map<String, Object> parameters) {
    this.query = query;
    this.parameters = parameters;
  }

  @Override
  public String getQuery() {
    return query;
  }

  public Map<String, Object> getParameters() {
    return parameters;
  }
}
