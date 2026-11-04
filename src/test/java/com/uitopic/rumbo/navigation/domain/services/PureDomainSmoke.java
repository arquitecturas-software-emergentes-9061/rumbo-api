package com.uitopic.rumbo.navigation.domain.services;
import com.uitopic.rumbo.navigation.application.internal.queryservices.DemoDestinationQueryServiceImpl;
import com.uitopic.rumbo.navigation.domain.exceptions.NavigationValidationException;
import com.uitopic.rumbo.navigation.domain.model.queries.GetDestinationDirectoryQuery;
import com.uitopic.rumbo.navigation.domain.model.valueobjects.Destination;
import com.uitopic.rumbo.navigation.domain.model.valueobjects.PublishedRouteNetwork;
import com.uitopic.rumbo.navigation.domain.model.valueobjects.RouteEdge;
import com.uitopic.rumbo.navigation.domain.model.valueobjects.RouteNode;
import com.uitopic.rumbo.navigation.domain.model.valueobjects.WallSegment;
import com.uitopic.rumbo.navigation.domain.repositories.NavigableModelRepository;

import java.util.*;
/** No Spring/JUnit required. Executed with javac in this workspace for basic validation. */
public final class PureDomainSmoke {
  public static void main(String[] args) {
    UUID site=UUID.randomUUID(),floor=UUID.randomUUID(),na=UUID.randomUUID(),nb=UUID.randomUUID(),nc=UUID.randomUUID();
    List<Destination> all=List.of(
      new Destination(UUID.randomUUID(),site,floor,nb,"Consultorio 101","Cardiología","CONSULTING_ROOM",true),
      new Destination(UUID.randomUUID(),site,floor,nc,"Laboratorio",null,"LABORATORY",false));
    var port=new NavigableModelRepository() {
      public List<Destination> publishedDestinations(UUID s) {return site.equals(s)?all:List.of();}
      public Optional<PublishedRouteNetwork> publishedNetwork(UUID s) {return Optional.empty();}
    };
    var query=new DemoDestinationQueryServiceImpl(port);
    check(query.search(new GetDestinationDirectoryQuery(site,"CARDIOLOGIA",false)).size()==1,"accent search");
    check(query.search(new GetDestinationDirectoryQuery(site,"",true)).size()==1,"accessible search");
    check(query.search(new GetDestinationDirectoryQuery(UUID.randomUUID(),"",false)).isEmpty(),"site isolation");
    var a=new RouteNode(floor,na,"A",0,0); var b=new RouteNode(floor,nb,"B",4,0); var c=new RouteNode(floor,nc,"C",8,0);
    var network=new PublishedRouteNetwork(site,List.of(a,b,c),List.of(
      new RouteEdge(a.key(),b.key(),4,"corridor",true,true),new RouteEdge(b.key(),c.key(),4,"corridor",false,true)),List.of());
    var planner=new RouteCalculationDomainService();
    check(planner.calculate(network,a.key(),b.key(),true).totalDistanceMeters()==4,"shortest route");
    try{planner.calculate(network,a.key(),c.key(),true);throw new AssertionError("Expected accessible route reject");}
    catch(NavigationValidationException expected){}
    check(planner.calculate(network,a.key(),c.key(),false).totalDistanceMeters()==8,"nonaccessible accepted");
    var blocked=new PublishedRouteNetwork(site,List.of(a,b),List.of(new RouteEdge(a.key(),b.key(),4,"corridor",true,true)),List.of(new WallSegment(floor,2,-2,2,2)));
    try{planner.calculate(blocked,a.key(),b.key(),false);throw new AssertionError("Expected wall block");}
    catch(NavigationValidationException expected){}
    check(!new WallSegment(floor,4,0,4,2).properlyCrosses(0,0,4,0),"endpoint touch");
    check(planner.calculate(network,a.key(),a.key(),false).totalDistanceMeters()==0,"self route");
    // Ensure cross-floor transitions are explicit vertical connectors.
    UUID secondFloor=UUID.randomUUID(); var upper=new RouteNode(secondFloor,UUID.randomUUID(),"2F",0,0);
    var badFloorEdge=new PublishedRouteNetwork(site,List.of(a,upper),
      List.of(new RouteEdge(a.key(),upper.key(),5,"corridor",true,true)),List.of());
    try{planner.calculate(badFloorEdge,a.key(),upper.key(),false);throw new AssertionError("Expected cross-floor rejection");}
    catch(NavigationValidationException expected){}
    var goodFloorEdge=new PublishedRouteNetwork(site,List.of(a,upper),
      List.of(new RouteEdge(a.key(),upper.key(),5,"elevator",true,true)),List.of());
    check(planner.calculate(goodFloorEdge,a.key(),upper.key(),true).totalDistanceMeters()==5,"vertical connector");
    System.out.println("PASS: eleven pure-JDK domain smoke checks");
  }
  private static void check(boolean yes,String test){if(!yes)throw new AssertionError(test);}
}
