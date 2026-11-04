package com.uitopic.rumbo.navigation.domain.services;
import com.uitopic.rumbo.navigation.domain.exceptions.NavigationValidationException;
import com.uitopic.rumbo.navigation.domain.model.valueobjects.NodeKey;
import com.uitopic.rumbo.navigation.domain.model.valueobjects.PublishedRouteNetwork;
import com.uitopic.rumbo.navigation.domain.model.valueobjects.RouteEdge;
import com.uitopic.rumbo.navigation.domain.model.valueobjects.RouteNode;
import com.uitopic.rumbo.navigation.domain.model.valueobjects.WallSegment;

import com.uitopic.rumbo.navigation.infrastructure.demo.DemoPublishedModelRepository;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RouteCalculationDomainServiceTest {
  private final RouteCalculationDomainService planner=new RouteCalculationDomainService();
  private final DemoPublishedModelRepository source=new DemoPublishedModelRepository();
  @Test void shortestPathToCardiology() {
    var model=source.publishedNetwork(DemoPublishedModelRepository.SITE).orElseThrow();
    var path=planner.calculate(model,new NodeKey(DemoPublishedModelRepository.FLOOR,DemoPublishedModelRepository.ENTRANCE),
      new NodeKey(DemoPublishedModelRepository.FLOOR,DemoPublishedModelRepository.CARDIO),true);
    assertEquals(8,path.totalDistanceMeters(),1e-8);assertEquals(2,path.nodes().size());
  }
  @Test void inaccessibleLinkIsBlocked() {
    var model=source.publishedNetwork(DemoPublishedModelRepository.SITE).orElseThrow();
    assertThrows(NavigationValidationException.class,()->planner.calculate(model,
      new NodeKey(DemoPublishedModelRepository.FLOOR,DemoPublishedModelRepository.ENTRANCE),
      new NodeKey(DemoPublishedModelRepository.FLOOR,DemoPublishedModelRepository.LAB),true));
  }
  @Test void normalModeIncludesLab() {
    var model=source.publishedNetwork(DemoPublishedModelRepository.SITE).orElseThrow();
    var result=planner.calculate(model,new NodeKey(DemoPublishedModelRepository.FLOOR,DemoPublishedModelRepository.ENTRANCE),
      new NodeKey(DemoPublishedModelRepository.FLOOR,DemoPublishedModelRepository.LAB),false);
    assertEquals(19,result.totalDistanceMeters(),1e-8);
  }
  @Test void removesEdgeCrossingPhysicalWall() {
    UUID f=DemoPublishedModelRepository.FLOOR, site=DemoPublishedModelRepository.SITE;
    var a=new RouteNode(f,UUID.randomUUID(),"A",0,0);var b=new RouteNode(f,UUID.randomUUID(),"B",10,0);
    var model=new PublishedRouteNetwork(site,List.of(a,b),List.of(new RouteEdge(a.key(),b.key(),10,"corridor",true,true)),
      List.of(new WallSegment(f,5,-2,5,2)));
    assertThrows(NavigationValidationException.class,()->planner.calculate(model,a.key(),b.key(),false));
  }
  @Test void choosesShorterPathRatherThanFirstPath() {
    UUID f=DemoPublishedModelRepository.FLOOR, site=DemoPublishedModelRepository.SITE;
    var a=new RouteNode(f,UUID.randomUUID(),"A",0,0); var b=new RouteNode(f,UUID.randomUUID(),"B",1,0); var c=new RouteNode(f,UUID.randomUUID(),"C",2,0);
    var model=new PublishedRouteNetwork(site,List.of(a,b,c),List.of(
      new RouteEdge(a.key(),c.key(),8,"corridor",true,true),
      new RouteEdge(a.key(),b.key(),1,"corridor",true,true),
      new RouteEdge(b.key(),c.key(),1,"corridor",true,true)),List.of());
    var result=planner.calculate(model,a.key(),c.key(),false);
    assertEquals(2,result.totalDistanceMeters(),1e-8);assertEquals(3,result.nodes().size());
  }
  @Test void routeToSelfIsZero() {
    var model=source.publishedNetwork(DemoPublishedModelRepository.SITE).orElseThrow();
    var key=new NodeKey(DemoPublishedModelRepository.FLOOR,DemoPublishedModelRepository.ENTRANCE);
    var route=planner.calculate(model,key,key,false);
    assertEquals(0,route.totalDistanceMeters(),1e-8);assertEquals(1,route.nodes().size());
  }
  @Test void crossFloorRequiresVerticalConnector() {
    UUID site=DemoPublishedModelRepository.SITE, floor=DemoPublishedModelRepository.FLOOR, otherFloor=UUID.randomUUID();
    var a=new RouteNode(floor,UUID.randomUUID(),"A",0,0); var b=new RouteNode(otherFloor,UUID.randomUUID(),"B",0,0);
    var bad=new PublishedRouteNetwork(site,List.of(a,b),List.of(new RouteEdge(a.key(),b.key(),5,"corridor",true,true)),List.of());
    assertThrows(NavigationValidationException.class,()->planner.calculate(bad,a.key(),b.key(),false));
    var good=new PublishedRouteNetwork(site,List.of(a,b),List.of(new RouteEdge(a.key(),b.key(),5,"elevator",true,true)),List.of());
    assertEquals(5,planner.calculate(good,a.key(),b.key(),true).totalDistanceMeters());
  }
  @Test void wallEndpointContactIsNotStrictCrossing() {
    var wall=new WallSegment(DemoPublishedModelRepository.FLOOR,1,0,1,5);
    assertFalse(wall.properlyCrosses(0,0,1,0));
    assertTrue(wall.properlyCrosses(0,2,2,2));
  }
}
