package org.metadatacenter.server.util;

import com.fasterxml.jackson.databind.JsonNode;

public class ParameterUtil {
  private ParameterUtil() {
  }

  public static String getStringOrThrowError(JsonNode node, String paramName, String errorMessage) throws
      IllegalArgumentException {
    String param = null;
    JsonNode paramNode = node.get(paramName);
    if (paramNode != null) {
      param = paramNode.asText();
      if (param != null) {
        param = param.trim();
      }
    }
    if (param == null || param.isEmpty()) {
      throw new IllegalArgumentException(errorMessage);
    }
    return param;
  }
}
