package com.uitopic.rumbo.navigation.domain;
import com.uitopic.rumbo.navigation.application.*;
import com.uitopic.rumbo.navigation.infrastructure.DemoPublishedModelRepository;
import java.util.*;
/** Pure-JDK smoke checks; not a replacement for Maven + Spring integration tests. */
public final class DomainSmokeChecks {
  public static void main(String[] args) {
    var repo=new DemoPublishedModelRepository();
    var search=new GetDestinationDirectoryQueryHandler(repo);
    var site=DemoPublishedModelRepository.SITE;var floor=DemoPublishedModelRepository.FLOOR;
    check(search.handle(new GetDestinationDirectoryQuery(site,"cardiologia",false)).size()==1,"accent search");
    check(search.handle(new GetDestinationDirectoryQuery(site,"",true)).size()==2,"accessible only search");
    check(search.handle(new GetDestinationDirectoryQuery(UUID.randomUUID(),"",false)).isEmpty(),"site isolation");
    var model=repo.publishedNetwork(site).orElseThrow();var planner=new RouteCalculationDomainService();
    var start=new NodeKey(floor,DemoPublishedModelRepository.ENTRANCE);
    var cardio=new NodeKey(floor,DemoPublishedModelRepository.CARDIO);
    check(planner.calculate(model,start,cardio,true).totalDistanceMeters()==8.0,"shortest route");
    try {planner.calculate(model,start,new NodeKey(floor,DemoPublishedModelRepository.LAB),true);throw new Error("Expected route block");}
    catch(NavigationValidationException expected) {}
    var wall=new WallSegment(floor,4,-2,4,2);
    check(wall.properlyCrosses(0,0,8,0),"wall crossing");
    System.out.println("PASS: six domain smoke checks");
  }
  private static void check(boolean yes,String name) {if(!yes) throw new AssertionError(name);}
}
