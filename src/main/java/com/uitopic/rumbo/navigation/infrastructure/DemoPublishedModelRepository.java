package com.uitopic.rumbo.navigation.infrastructure;
import com.uitopic.rumbo.navigation.domain.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import java.util.*;
/** Explicit development fixture; NEVER used as an alternative to real clinic data. */
@Repository
@Profile("demo")
public final class DemoPublishedModelRepository implements NavigableModelRepository {
  public static final UUID SITE=UUID.fromString("00000000-0000-4000-8000-000000000001");
  public static final UUID FLOOR=UUID.fromString("00000000-0000-4000-8000-000000000002");
  public static final UUID ENTRANCE=UUID.fromString("00000000-0000-4000-8000-000000000010");
  public static final UUID CARDIO=UUID.fromString("00000000-0000-4000-8000-000000000011");
  public static final UUID PHARMACY=UUID.fromString("00000000-0000-4000-8000-000000000012");
  public static final UUID LAB=UUID.fromString("00000000-0000-4000-8000-000000000013");
  @Override public List<Destination> publishedDestinations(UUID siteId) {
    if(!SITE.equals(siteId)) return List.of();
    return List.of(
      new Destination(UUID.fromString("00000000-0000-4000-8000-000000000003"),SITE,FLOOR,CARDIO,"Consultorio 101","Cardiología","CONSULTING_ROOM",true),
      new Destination(UUID.fromString("00000000-0000-4000-8000-000000000004"),SITE,FLOOR,PHARMACY,"Farmacia",null,"PHARMACY",true),
      new Destination(UUID.fromString("00000000-0000-4000-8000-000000000005"),SITE,FLOOR,LAB,"Laboratorio",null,"LABORATORY",false)
    );
  }
  @Override public Optional<PublishedRouteNetwork> publishedNetwork(UUID siteId) {
    if(!SITE.equals(siteId)) return Optional.empty();
    return Optional.of(new PublishedRouteNetwork(SITE,List.of(
      new RouteNode(FLOOR,ENTRANCE,"Entrada",0,0),
      new RouteNode(FLOOR,CARDIO,"Consultorio 101",8,0),
      new RouteNode(FLOOR,PHARMACY,"Farmacia",8,7),
      new RouteNode(FLOOR,LAB,"Laboratorio",12,7)
    ),List.of(
      new RouteEdge(new NodeKey(FLOOR,ENTRANCE),new NodeKey(FLOOR,CARDIO),8,"corridor",true,true),
      new RouteEdge(new NodeKey(FLOOR,CARDIO),new NodeKey(FLOOR,PHARMACY),7,"corridor",true,true),
      new RouteEdge(new NodeKey(FLOOR,PHARMACY),new NodeKey(FLOOR,LAB),4,"corridor",false,true)
    ),List.of()));
  }
}
