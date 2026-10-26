package com.uitopic.rumbo.navigation.application;
import java.util.UUID;
public record GetDestinationDirectoryQuery(UUID siteId, String searchText, boolean accessibleOnly) {
  public GetDestinationDirectoryQuery {
    if(siteId==null) throw new IllegalArgumentException("siteId is required");
    searchText=searchText==null?"":searchText.strip();
    if(searchText.length()>150) throw new IllegalArgumentException("Search text too long");
  }
}
