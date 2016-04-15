package org.metadatacenter.server.neo4j;

public class CypherQueryLiteral implements CypherQuery {
  private String query;

  public CypherQueryLiteral(String query) {
    this.query = query;
  }

  @Override
  public String getQuery() {
    return query;
  }
}
